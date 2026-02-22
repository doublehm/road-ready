import pytest
import json
from app.services.diagnostic_evaluator import DiagnosticEvaluator
from app import models

def test_evaluator_tracks_harsh_braking():
    evaluator = DiagnosticEvaluator()
    
    # Simulate harsh braking data: -6.5 m/s² ≈ 0.66g, above the 0.6g threshold.
    # Two samples separated by >3 s so each is a distinct event.
    acceleration_data = [
        {'timestamp': 1000, 'x': 0, 'y': 0, 'z': -6.5, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 5000, 'x': 0, 'y': 0, 'z': -0.1, 'latitude': 49.2, 'longitude': -123.1}
    ]
    speed_data = [] # Not needed for pure acceleration check
    
    score, feedback = evaluator._evaluate_braking(acceleration_data, speed_data)
    
    assert score < 100
    assert feedback['harsh_braking_events'] == 1
    assert len(feedback['events']) == 1
    assert feedback['events'][0]['type'] == 'harsh_braking'
    assert feedback['events'][0]['lat'] == 49.2
    assert feedback['events'][0]['severity'] in ['medium', 'high']

def test_evaluator_tracks_speeding():
    evaluator = DiagnosticEvaluator()
    
    # Simulate sustained speeding: 7 consecutive points at 70 km/h in a 50 km/h zone.
    # The evaluator requires CONSECUTIVE_SPEEDING_REQUIRED=3 AND i%5==0 to emit,
    # so we need at least 6 points (the event fires at index 5 with consecutive_over=6).
    speed_data = [
        {'timestamp': 1000 + i, 'speed': 70, 'latitude': 49.2 + i * 0.01, 'longitude': -123.1}
        for i in range(7)
    ]
    
    # Explicit speed_limit_data forcing a 50 km/h residential zone.
    speed_limit_data = [
        {'timestamp': 1000 + i, 'speed_limit': 50, 'zone_type': 'regular', 'road_type': 'residential'}
        for i in range(7)
    ]
    
    score, feedback = evaluator._evaluate_speed(speed_data, 1, speed_limit_data=speed_limit_data)
    
    assert score < 100
    assert len(feedback['events']) > 0
    assert any(e['type'] == 'speeding' for e in feedback['events'])
    assert feedback['events'][0]['limit'] == 50

def test_live_evaluate_endpoint(client, db, auth_headers):
    # Create student
    student = models.User(email="test_live@example.com", full_name="Live Test", role="student", hashed_password="hashed")
    db.add(student)
    db.commit()
    
    headers = auth_headers("test_live@example.com")
    
    # Payload with one harsh braking event
    payload = {
        "acceleration_window": [
            {"timestamp": 1000, "x": 0, "y": 0, "z": -10.0, "latitude": 49.2, "longitude": -123.1}
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
    assert len(data["events"]) == 1
    assert data["events"][0]["type"] == "harsh_braking"

def test_evaluate_aggregates_all_events():
    evaluator = DiagnosticEvaluator()
    
    # Acceleration: -10 m/s² ≈ 1.02g — well above 0.6g threshold
    # Speed: 7 sustained points at 90 km/h in a 80 km/h zone.
    # Need ≥6 points so the event fires at i=5 (i%5==0, consecutive_over=6≥3).
    speed_points = [
        {'timestamp': 1005 + i, 'speed': 90, 'latitude': 49.3 + i * 0.01, 'longitude': -123.2}
        for i in range(7)
    ]
    limit_points = [
        {'timestamp': 1005 + i, 'speed_limit': 80, 'zone_type': 'regular', 'road_type': 'highway'}
        for i in range(7)
    ]
    ride_data = {
        'acceleration_data': json.dumps([
            {'timestamp': 1000, 'x': 0, 'y': 0, 'z': -10.0, 'latitude': 49.2, 'longitude': -123.1}
        ]),
        'speed_data': json.dumps(speed_points),
        'speed_limit_data': json.dumps(limit_points),
        'duration_minutes': 25,
        'distance_km': 10
    }
    
    result = evaluator.evaluate(ride_data)
    eval_dict = json.loads(result['evaluation_result'])
    
    assert 'events' in eval_dict
    events = eval_dict['events']
    # Should have harsh braking and speeding
    event_types = [e['type'] for e in events]
    assert 'harsh_braking' in event_types
    assert 'speeding' in event_types
    # Events should be sorted by timestamp
    assert events[0]['timestamp'] == 1000
