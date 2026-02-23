import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch, AsyncMock
from app.main import app
from app import models
import json

def test_websocket_connection():
    client = TestClient(app)
    with client.websocket_connect("/api/v1/diagnostic-rides/live-ride-stream?ride_id=123") as websocket:
        websocket.send_json({"type": "ping"})
        data = websocket.receive_json()
        assert data == {"type": "pong"}

def test_websocket_broadcast():
    client = TestClient(app)
    ride_id = "ride_456"
    
    with patch("app.api.diagnostic_rides.nosql_repo.save_telemetry_chunk", new_callable=AsyncMock):
        with patch("app.api.diagnostic_rides.nosql_repo.save_event", new_callable=AsyncMock):
            # Connect as mobile (sender)
            with client.websocket_connect(f"/api/v1/diagnostic-rides/live-ride-stream?ride_id={ride_id}&client_type=mobile") as mobile_ws:
                # Connect as web (receiver)
                with client.websocket_connect(f"/api/v1/diagnostic-rides/live-ride-stream?ride_id={ride_id}&client_type=web") as web_ws:
                    
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
                    # Drain the broadcast echo sent back to mobile
                    mobile_ws.receive_json()
                    
                    # Web should receive the broadcast (server may add a timestamp to the data)
                    received_data = web_ws.receive_json()
                    assert received_data["type"] == telemetry_data["type"]
                    assert received_data["data"]["speed"] == telemetry_data["data"]["speed"]
                    assert received_data["data"]["location"] == telemetry_data["data"]["location"]

def test_websocket_event_detection():
    client = TestClient(app)
    ride_id = "ride_789"
    
    with patch("app.api.diagnostic_rides.nosql_repo.save_telemetry_chunk", new_callable=AsyncMock):
        with patch("app.api.diagnostic_rides.nosql_repo.save_event", new_callable=AsyncMock):
            with client.websocket_connect(f"/api/v1/diagnostic-rides/live-ride-stream?ride_id={ride_id}&client_type=mobile") as mobile_ws:
                with client.websocket_connect(f"/api/v1/diagnostic-rides/live-ride-stream?ride_id={ride_id}&client_type=web") as web_ws:
                    
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
                    # Drain the broadcast echo sent back to mobile
                    mobile_ws.receive_json()
                    
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
    
    # 2. Connect and send telemetry — telemetry is now stored in MongoDB via nosql_repo
    with patch("app.api.diagnostic_rides.nosql_repo.save_telemetry_chunk", new_callable=AsyncMock) as mock_save_telemetry:
        with client.websocket_connect(f"/api/v1/diagnostic-rides/live-ride-stream?ride_id={ride_id}&client_type=mobile") as mobile_ws:
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
        
        # Verify nosql_repo was called with the telemetry point
        assert mock_save_telemetry.called
        call_args = mock_save_telemetry.call_args
        assert call_args[0][0] == str(ride_id)  # ride_id
        saved_points = call_args[0][1]
        assert saved_points[0]["location"] == {"lat": 49.0, "lng": -123.0}
        assert saved_points[0]["acceleration"] == {"x": 0.1, "y": 0.2, "z": 9.8}
    
    # 3. Send event
    with patch("app.api.diagnostic_rides.nosql_repo.save_event", new_callable=AsyncMock) as mock_save_event:
        with client.websocket_connect(f"/api/v1/diagnostic-rides/live-ride-stream?ride_id={ride_id}&client_type=mobile") as mobile_ws:
            event_data = {
                "type": "event",
                "data": {
                    "event_type": "harsh_braking",
                    "severity": "high",
                    "timestamp": 123456789.0
                }
            }
            mobile_ws.send_json(event_data)
        
        # Verify nosql_repo was called with the event
        assert mock_save_event.called
        call_args = mock_save_event.call_args
        assert call_args[0][0] == str(ride_id)
        assert call_args[0][1]["event_type"] == "harsh_braking"

