import pytest
from fastapi.testclient import TestClient
from app.main import app
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
