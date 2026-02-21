import pytest
from app import models

def test_diagnostic_ride_detail_contains_leaflet(client, db, auth_headers):
    # Setup: Create a student and a ride
    user = models.User(email="test_map@example.com", full_name="Map User", role="student", hashed_password="pw")
    db.add(user)
    db.commit()
    
    ride = models.DiagnosticRide(
        student_id=user.id,
        ride_type="parent_supervised",
        start_time="2024-01-01T10:00:00Z",
        status="completed",
        overall_score=85.0,
        passed=True,
        evaluation_result='{"summary": "Good", "events": []}',
        route_coords='[]'
    )
    db.add(ride)
    db.commit()
    
    headers = auth_headers("test_map@example.com")
    response = client.get(f"/diagnostic-ride/{ride.id}", headers=headers)
    
    assert response.status_code == 200
    content = response.text
    
    # Verify Leaflet is loaded
    assert "leaflet.css" in content
    assert "leaflet.js" in content
    # Verify our custom map helper (should FAIL now because it's not added yet)
    assert 'src="/static/maps.js"' in content

def test_booking_form_contains_leaflet(client, db, auth_headers):
    # Setup: Create an instructor
    user = models.User(email="instr_map@example.com", full_name="Instr Map", role="instructor", hashed_password="pw")
    db.add(user)
    db.commit()
    instructor = models.InstructorProfile(user_id=user.id, hourly_rate=60.0, city="Test")
    db.add(instructor)
    db.commit()
    
    # Create a student to view the form
    student = models.User(email="stud_map@example.com", full_name="Stud Map", role="student", hashed_password="pw")
    db.add(student)
    db.commit()
    
    headers = auth_headers("stud_map@example.com")
    response = client.get(f"/book/{instructor.id}", headers=headers)
    
    assert response.status_code == 200
    content = response.text
    
    assert "leaflet.css" in content
    assert "leaflet.js" in content
    # Verify our custom map helper (should FAIL)
    assert 'src="/static/maps.js"' in content

def test_instructor_dashboard_contains_leaflet(client, db, auth_headers):
    # Setup: Create an instructor
    user = models.User(email="instr_dash_map@example.com", full_name="Dash Map", role="instructor", hashed_password="pw")
    db.add(user)
    db.commit()
    instructor = models.InstructorProfile(user_id=user.id, hourly_rate=60.0, city="Test", bio="Bio", car_model="X", insurance_policy="Y", certification_id="Z")
    db.add(instructor)
    db.commit()
    
    # Mock login (cookie based for web routes)
    client.cookies.set("user_id", str(user.id))
    response = client.get("/dashboard")
    
    assert response.status_code == 200
    content = response.text
    
    assert "leaflet.css" in content
    assert "leaflet.js" in content
    assert 'src="/static/maps.js"' in content
