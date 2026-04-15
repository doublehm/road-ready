import pytest
import json
import numpy as np
from app.services.diagnostic_evaluator import DiagnosticEvaluator
from app import models


def _make_physics(points):
    """Helper: convert raw accel-like dicts into physics_data format expected by the evaluator.
    Uses the evaluator's own _process_physics with an identity orientation matrix."""
    evaluator = DiagnosticEvaluator()
    return evaluator._process_physics(points, np.eye(3))


def _make_physics_direct(entries):
    """Helper: build physics_data dicts directly (bypasses gravity filter)."""
    return [
        {
            'timestamp': e['timestamp'],
            'latitude': e.get('latitude'),
            'longitude': e.get('longitude'),
            'lat_accel': e.get('lat_accel', 0.0),
            'long_accel': e.get('long_accel', 0.0),
            'vert_accel': e.get('vert_accel', 0.0),
            'jerk': e.get('jerk', 0.0),
            'raw': np.array([0, 0, 0]),
        }
        for e in entries
    ]


def test_evaluator_tracks_harsh_braking():
    evaluator = DiagnosticEvaluator()

    # Simulate harsh braking: long_accel = -6.5 m/s² ≈ 0.66g.
    # Two samples separated by >3 s so each is a distinct event.
    physics_data = _make_physics_direct([
        {'timestamp': 1000, 'long_accel': -6.5, 'latitude': 49.2, 'longitude': -123.1, 'jerk': 0},
        {'timestamp': 5000, 'long_accel': -0.1, 'latitude': 49.2, 'longitude': -123.1, 'jerk': 0},
    ])
    speed_data = []

    score, feedback = evaluator._evaluate_braking(physics_data, speed_data)

    assert score < 100
    assert feedback['harsh_braking_events'] == 1
    assert len(feedback['events']) == 1
    assert feedback['events'][0]['type'] == 'harsh_braking'
    assert feedback['events'][0]['lat'] == 49.2
    assert feedback['events'][0]['severity'] in ['medium', 'high']


def test_evaluator_tracks_speeding():
    evaluator = DiagnosticEvaluator()

    # 20 sustained points at 70 km/h in a 50 km/h zone.
    # Tolerance is 5 km/h, so 70 > 55 → speeding. Events fire every 10th speeding point.
    speed_data = [
        {'timestamp': 1000 + i * 1000, 'speed': 70, 'latitude': 49.2 + i * 0.01, 'longitude': -123.1}
        for i in range(20)
    ]
    speed_limit_data = [
        {'timestamp': 1000 + i * 1000, 'speed_limit': 50, 'zone_type': 'regular', 'road_type': 'residential'}
        for i in range(20)
    ]

    score, feedback = evaluator._evaluate_speed(speed_data, speed_limit_data)

    assert score < 100
    assert len(feedback['events']) > 0
    assert any(e['type'] == 'speeding' for e in feedback['events'])
    assert feedback['events'][0]['limit'] == 50


def test_live_evaluate_endpoint(client, db, auth_headers):
    student = models.User(email="test_live@example.com", full_name="Live Test", role="student", hashed_password="hashed")
    db.add(student)
    db.commit()

    headers = auth_headers("test_live@example.com")

    payload = {
        "acceleration_window": [
            {"timestamp": 900, "x": 0, "y": 0.0, "z": -9.81, "latitude": 49.2, "longitude": -123.1},
            {"timestamp": 910, "x": 0, "y": 0.0, "z": -9.81, "latitude": 49.2, "longitude": -123.1},
            {"timestamp": 920, "x": 0, "y": 0.0, "z": -9.81, "latitude": 49.2, "longitude": -123.1},
        ] + [
            {"timestamp": 1000 + i * 10, "x": 0, "y": -12.0, "z": -9.81, "latitude": 49.2, "longitude": -123.1}
            for i in range(30)
        ],
        "speed_window": [
            {"timestamp": 1000, "speed": 30, "latitude": 49.2, "longitude": -123.1}
        ],
        "rotation_window": []
    }

    response = client.post("/api/v1/diagnostic-rides/live-evaluate", json=payload, headers=headers)

    assert response.status_code == 200
    data = response.json()
    assert "events" in data
    assert len(data["events"]) >= 1
    assert any(e["type"] == "harsh_braking" for e in data["events"])


@pytest.mark.asyncio
async def test_evaluate_aggregates_all_events():
    evaluator = DiagnosticEvaluator()

    # Multiple accel points with harsh braking sustained so the gravity filter
    # converges and the braking signal comes through clearly.
    accel_points = [
        {'timestamp': 900, 'x': 0, 'y': 0.0, 'z': -9.81, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 910, 'x': 0, 'y': 0.0, 'z': -9.81, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 920, 'x': 0, 'y': 0.0, 'z': -9.81, 'latitude': 49.2, 'longitude': -123.1},
    ] + [
        {'timestamp': 1000 + i * 10, 'x': 0, 'y': -12.0, 'z': -9.81, 'latitude': 49.2, 'longitude': -123.1}
        for i in range(30)
    ]

    # Speed: 20 sustained points at 90 km/h in a 80 km/h zone (tolerance 5).
    speed_points = [
        {'timestamp': 1005 + i * 1000, 'speed': 90, 'latitude': 49.3 + i * 0.01, 'longitude': -123.2}
        for i in range(20)
    ]
    limit_points = [
        {'timestamp': 1005 + i * 1000, 'speed_limit': 80, 'zone_type': 'regular', 'road_type': 'highway'}
        for i in range(20)
    ]
    ride_data = {
        'acceleration_data': json.dumps(accel_points),
        'speed_data': json.dumps(speed_points),
        'speed_limit_data': json.dumps(limit_points),
        'duration_minutes': 25,
        'distance_km': 10
    }

    result = await evaluator.evaluate("test_ride_id", ride_data)
    eval_dict = json.loads(result['evaluation_result'])

    assert 'events' in eval_dict
    events = eval_dict['events']
    event_types = [e['type'] for e in events]
    assert 'harsh_braking' in event_types
    assert 'speeding' in event_types


def test_straight_line_driving_no_cornering_flag():
    """Driving straight with small vibration noise should not trigger sharp turn events."""
    evaluator = DiagnosticEvaluator()

    import random
    random.seed(42)
    # Small lateral noise (< 0.45g = 4.41 m/s²) and no jerk
    physics_data = _make_physics_direct([
        {'timestamp': 1000 + i * 100, 'lat_accel': random.uniform(-1.0, 1.0), 'long_accel': 0.5, 'jerk': 0,
         'latitude': 49.2, 'longitude': -123.1}
        for i in range(50)
    ])
    speed_data = [
        {'timestamp': 1000 + i * 1000, 'speed': 60, 'latitude': 49.2, 'longitude': -123.1}
        for i in range(5)
    ]
    heading_data = [
        {'timestamp': 1000 + i * 1000, 'heading': 90.0}
        for i in range(5)
    ]

    score, feedback = evaluator._evaluate_cornering(physics_data, heading_data, speed_data=speed_data)

    assert feedback['sharp_turns'] == 0, f"Expected 0 sharp turns on straight road, got {feedback['sharp_turns']}"
    assert len(feedback['events']) == 0


def test_pothole_does_not_flag_harsh_braking():
    """Hitting a pothole (Z-axis spike) should not trigger a harsh braking event.
    In the physics pipeline, vertical spikes go to vert_accel, not long_accel."""
    evaluator = DiagnosticEvaluator()

    # Normal driving: long_accel stays calm, vert_accel spikes at sample 3
    physics_data = _make_physics_direct([
        {'timestamp': 1000, 'long_accel': -0.5, 'vert_accel': 0.0, 'jerk': 0, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 1100, 'long_accel': -0.3, 'vert_accel': 0.0, 'jerk': 0, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 1200, 'long_accel': -0.2, 'vert_accel': 0.0, 'jerk': 0, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 1300, 'long_accel': -0.5, 'vert_accel': -5.2, 'jerk': 0, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 1400, 'long_accel': -0.3, 'vert_accel': 0.0, 'jerk': 0, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 1500, 'long_accel': -0.4, 'vert_accel': 0.0, 'jerk': 0, 'latitude': 49.2, 'longitude': -123.1},
    ])
    speed_data = [
        {'timestamp': 1000, 'speed': 50, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 2000, 'speed': 49, 'latitude': 49.2, 'longitude': -123.1},
    ]

    score, feedback = evaluator._evaluate_braking(physics_data, speed_data)

    assert feedback['harsh_braking_events'] == 0, \
        f"Expected 0 harsh braking events from pothole, got {feedback['harsh_braking_events']}"
    assert not any(e['type'] == 'harsh_braking' for e in feedback['events'])


def test_gps_jitter_does_not_flag_speeding():
    """Speed fluctuating ±3 km/h around the limit should not trigger speeding events."""
    evaluator = DiagnosticEvaluator()

    import random
    random.seed(123)
    speed_data = [
        {'timestamp': 1000 + i * 1000, 'speed': 50 + random.uniform(-3, 3),
         'latitude': 49.2 + i * 0.001, 'longitude': -123.1}
        for i in range(20)
    ]
    speed_limit_data = [
        {'timestamp': 1000 + i * 1000, 'speed_limit': 50, 'zone_type': 'regular', 'road_type': 'residential'}
        for i in range(20)
    ]

    score, feedback = evaluator._evaluate_speed(speed_data, speed_limit_data)

    assert len(feedback['events']) == 0, \
        f"Expected 0 speeding events from GPS jitter, got {len(feedback['events'])}"
    assert score > 80, f"Score too low for GPS jitter scenario: {score}"


def test_harsh_acceleration_detection():
    """Aggressive forward acceleration should trigger harsh_acceleration events."""
    evaluator = DiagnosticEvaluator()

    # Two bursts of harsh acceleration (long_accel > 0.3g = 2.94 m/s²) spaced > 3s
    physics_data = _make_physics_direct([
        {'timestamp': 1000, 'long_accel': 5.0, 'jerk': 0, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 5000, 'long_accel': 4.5, 'jerk': 0, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 9000, 'long_accel': 0.5, 'jerk': 0, 'latitude': 49.2, 'longitude': -123.1},
    ])

    penalty, feedback = evaluator._evaluate_erratic_driving(physics_data)

    accel_events = [e for e in feedback['events'] if e['type'] == 'harsh_acceleration']
    assert len(accel_events) == 2
    assert penalty >= 16  # 8 per event


def test_erratic_jerk_detection():
    """High jerk values should trigger erratic_control events."""
    evaluator = DiagnosticEvaluator()

    # Two points with jerk > JERK_THRESHOLD (2.0 m/s³) spaced > 2s
    physics_data = _make_physics_direct([
        {'timestamp': 1000, 'long_accel': 0.5, 'jerk': 5.0, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 4000, 'long_accel': 0.5, 'jerk': 4.0, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 7000, 'long_accel': 0.5, 'jerk': 0.5, 'latitude': 49.2, 'longitude': -123.1},
    ])

    penalty, feedback = evaluator._evaluate_erratic_driving(physics_data)

    erratic_events = [e for e in feedback['events'] if e['type'] == 'erratic_control']
    assert len(erratic_events) == 2
    assert penalty >= 8  # 4 per jerk event


def test_steady_driving_no_erratic_flag():
    """Constant smooth driving should not trigger erratic events."""
    evaluator = DiagnosticEvaluator()

    physics_data = _make_physics_direct([
        {'timestamp': 1000 + i * 1000, 'long_accel': 0.5, 'jerk': 0.3, 'latitude': 49.2, 'longitude': -123.1}
        for i in range(15)
    ])

    penalty, feedback = evaluator._evaluate_erratic_driving(physics_data)

    assert penalty == 0
    assert len(feedback['events']) == 0


def test_low_jerk_no_erratic_flag():
    """Low jerk values in traffic should not trigger erratic flags."""
    evaluator = DiagnosticEvaluator()

    physics_data = _make_physics_direct([
        {'timestamp': 1000 + i * 1000, 'long_accel': 0.2 * ((-1) ** i), 'jerk': 0.8, 'latitude': 49.2, 'longitude': -123.1}
        for i in range(20)
    ])

    penalty, feedback = evaluator._evaluate_erratic_driving(physics_data)

    assert len(feedback['events']) == 0


def test_combined_dynamics_friction_circle():
    """High combined lateral + longitudinal force should trigger friction circle violation."""
    evaluator = DiagnosticEvaluator()

    # Combined G = sqrt(0.4² + 0.5²) ≈ 0.64G > 0.60G threshold
    physics_data = _make_physics_direct([
        {'timestamp': 1000, 'lat_accel': 3.92, 'long_accel': 4.91, 'jerk': 0, 'latitude': 49.2, 'longitude': -123.1},
    ])

    penalty, feedback = evaluator._evaluate_combined_dynamics(physics_data)

    assert penalty > 0
    assert len(feedback['events']) == 1
    assert feedback['events'][0]['type'] == 'friction_circle_violation'


def test_no_friction_circle_gentle_driving():
    """Gentle driving should not trigger friction circle violations."""
    evaluator = DiagnosticEvaluator()

    # Combined G = sqrt(0.1² + 0.1²) ≈ 0.14G < 0.60G threshold
    physics_data = _make_physics_direct([
        {'timestamp': 1000, 'lat_accel': 0.98, 'long_accel': 0.98, 'jerk': 0, 'latitude': 49.2, 'longitude': -123.1},
    ])

    penalty, feedback = evaluator._evaluate_combined_dynamics(physics_data)

    assert penalty == 0
    assert len(feedback['events']) == 0


def test_lower_braking_threshold_catches_moderate_braking():
    """With threshold at 0.6g (5.886 m/s²), braking at 0.66g should be detected."""
    evaluator = DiagnosticEvaluator()

    # 0.66g = 6.47 m/s² — above 0.6g threshold
    physics_data = _make_physics_direct([
        {'timestamp': 1000, 'long_accel': -6.47, 'jerk': 0, 'latitude': 49.2, 'longitude': -123.1},
    ])
    speed_data = []

    score, feedback = evaluator._evaluate_braking(physics_data, speed_data)

    assert feedback['harsh_braking_events'] == 1, \
        f"0.66g braking should be detected with 0.6g threshold, got {feedback['harsh_braking_events']}"
