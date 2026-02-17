import pytest
import json
from app.services.diagnostic_evaluator import DiagnosticEvaluator
from app import models

def test_evaluator_tracks_harsh_braking():
    evaluator = DiagnosticEvaluator()
    
    # Simulate harsh braking data (Z acceleration < -3.92 m/s² for 0.4g)
    acceleration_data = [
        {'timestamp': 1000, 'x': 0, 'y': 0, 'z': -5.0, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 1001, 'x': 0, 'y': 0, 'z': -0.1, 'latitude': 49.2, 'longitude': -123.1}
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
    
    # Simulate speeding (avg speed 30 -> limit 50, but one point is 70)
    speed_data = [
        {'timestamp': 1000, 'speed': 10, 'latitude': 49.2, 'longitude': -123.1},
        {'timestamp': 1001, 'speed': 10, 'latitude': 49.21, 'longitude': -123.11},
        {'timestamp': 1002, 'speed': 70, 'latitude': 49.22, 'longitude': -123.12}
    ]
    
    # _evaluate_speed detects residential limit (50) for these speeds
    score, feedback = evaluator._evaluate_speed(speed_data, 1)
    
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
    
    ride_data = {
        'acceleration_data': json.dumps([
            {'timestamp': 1000, 'x': 0, 'y': 0, 'z': -10.0, 'latitude': 49.2, 'longitude': -123.1}
        ]),
        'speed_data': json.dumps([
            {'timestamp': 1005, 'speed': 90, 'latitude': 49.3, 'longitude': -123.2},
            {'timestamp': 1006, 'speed': 95, 'latitude': 49.31, 'longitude': -123.21}
        ]),
        'duration_minutes': 25,
        'distance_km': 10
    }
    
    # Avg speed is ~92.5 -> limit 80. So speeding detected.
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
    assert events[1]['timestamp'] == 1005
