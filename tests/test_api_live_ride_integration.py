import pytest
from app import models
import json

def test_start_ride_and_update(client, db, auth_headers):
    # 1. Create a user
    email = "stud_int@example.com"
    student = models.User(email=email, full_name="Stud Int", role="student", hashed_password="hashed", phone_number="1234567890")
    db.add(student)
    db.commit()
    
    headers = auth_headers(email)
    
    # 2. Start ride (POST /api/v1/diagnostic-rides/)
    ride_data = {
        "ride_type": "parent_supervised",
        "start_time": "2026-02-20T10:00:00Z"
    }
    response = client.post("/api/v1/diagnostic-rides/", json=ride_data, headers=headers)
    assert response.status_code == 200
    ride_id = response.json()["id"]
    
    # 3. Update ride (PUT /api/v1/diagnostic-rides/{ride_id})
    final_data = {
        "ride_type": "parent_supervised",
        "start_time": "2026-02-20T10:00:00Z",
        "end_time": "2026-02-20T10:30:00Z",
        "duration_minutes": 30.0,
        "distance_km": 10.5,
        "route_coords": json.dumps([{"lat": 49.0, "lng": -123.0}]),
        "acceleration_data": json.dumps([{"x": 0.1, "y": 0.2, "z": 9.8}])
    }
    response = client.put(f"/api/v1/diagnostic-rides/{ride_id}", json=final_data, headers=headers)
    assert response.status_code == 200
    
    # 4. Verify DB
    ride = db.query(models.DiagnosticRide).filter(models.DiagnosticRide.id == ride_id).first()
    assert ride.distance_km == 10.5
    assert ride.duration_minutes == 30.0
    assert "49.0" in ride.route_coords
