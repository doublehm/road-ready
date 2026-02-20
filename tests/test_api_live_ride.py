import pytest
from fastapi.testclient import TestClient
from app.main import app
from app import models
import json

def test_websocket_connection():
    client = TestClient(app)
    with client.websocket_connect("/api/v1/diagnostic-rides/ws/123") as websocket:
        websocket.send_json({"type": "ping"})
        data = websocket.receive_json()
        assert data == {"type": "pong"}

def test_websocket_broadcast():
    client = TestClient(app)
    ride_id = "ride_456"
    
    # Connect as mobile (sender)
    with client.websocket_connect(f"/api/v1/diagnostic-rides/ws/{ride_id}?client_type=mobile") as mobile_ws:
        # Connect as web (receiver)
        with client.websocket_connect(f"/api/v1/diagnostic-rides/ws/{ride_id}?client_type=web") as web_ws:
            
            # Mobile sends telemetry
            telemetry_data = {
                "type": "telemetry",
                "data": {
                    "acceleration": {"x": 0.1, "y": 0.2, "z": 9.8},
                    "speed": 50.0,
                    "location": {"lat": 49.2827, "lng": -123.1207}
                }
            }
            mobile_ws.send_json(telemetry_data)
            
            # Web should receive it
            received_data = web_ws.receive_json()
            assert received_data == telemetry_data

def test_websocket_event_detection():
    client = TestClient(app)
    ride_id = "ride_789"
    
    with client.websocket_connect(f"/api/v1/diagnostic-rides/ws/{ride_id}?client_type=mobile") as mobile_ws:
        with client.websocket_connect(f"/api/v1/diagnostic-rides/ws/{ride_id}?client_type=web") as web_ws:
            
            # Mobile sends a detected event
            event_data = {
                "type": "event",
                "data": {
                    "event_type": "harsh_braking",
                    "severity": "high",
                    "timestamp": 123456789.0,
                    "location": {"lat": 49.2827, "lng": -123.1207}
                }
            }
            mobile_ws.send_json(event_data)
            
            # Web should receive the event
            received_data = web_ws.receive_json()
            assert received_data == event_data

def test_websocket_persistence(client, db):
    # 1. Create a user and a ride in DB
    user = models.User(email="test@example.com", hashed_password="hashed", full_name="Test User", role="student", phone_number="1234567890")
    db.add(user)
    db.commit()
    db.refresh(user)
    
    ride = models.DiagnosticRide(
        student_id=user.id, ride_type="parent_supervised", start_time="2026-02-20T10:00:00", status="pending", created_at="2026-02-20T10:00:00"
    )
    db.add(ride)
    db.commit()
    db.refresh(ride)
    ride_id = ride.id
    
    # 2. Connect and send telemetry
    with client.websocket_connect(f"/api/v1/diagnostic-rides/ws/{ride_id}?client_type=mobile") as mobile_ws:
        telemetry_data = {
            "type": "telemetry",
            "data": {
                "acceleration": {"x": 0.1, "y": 0.2, "z": 9.8},
                "speed": 55.0,
                "location": {"lat": 49.0, "lng": -123.0},
                "timestamp": 123456789.0
            }
        }
        mobile_ws.send_json(telemetry_data)
        
    # 3. Verify DB update
    db.refresh(ride)
    assert ride.route_coords is not None
    coords = json.loads(ride.route_coords)
    assert coords[-1] == {"lat": 49.0, "lng": -123.0}
    
    accel = json.loads(ride.acceleration_data)
    assert accel[-1] == {"x": 0.1, "y": 0.2, "z": 9.8}
    
    # 4. Send event
    with client.websocket_connect(f"/api/v1/diagnostic-rides/ws/{ride_id}?client_type=mobile") as mobile_ws:
        event_data = {
            "type": "event",
            "data": {
                "event_type": "harsh_braking",
                "severity": "high",
                "timestamp": 123456789.0
            }
        }
        mobile_ws.send_json(event_data)
        
    db.refresh(ride)
    feedback = json.loads(ride.human_feedback)
    assert feedback[-1]["event_type"] == "harsh_braking"
