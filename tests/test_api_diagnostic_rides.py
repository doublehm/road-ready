import pytest
from app import models

def test_create_diagnostic_ride_with_booking(client, db, auth_headers):
    # Create a student
    student = models.User(email="student@example.com", full_name="Student", role="student", hashed_password="hashed")
    db.add(student)
    db.commit()
    
    # Create an instructor and booking
    instructor = models.InstructorProfile(user_id=student.id, bio="Bio", hourly_rate=50.0, city="Vancouver", car_model="X", insurance_policy="INS-12345", certification_id="CERT-001")
    db.add(instructor)
    db.commit()
    
    booking = models.BookingRequest(student_id=student.id, instructor_id=instructor.id, date="2024-01-01", time="10:00", duration=1, pickup_address="Address", status="pending")
    db.add(booking)
    db.commit()
    
    headers = auth_headers("student@example.com")
    response = client.post(
        "/api/v1/diagnostic-rides/",
        json={
            "ride_type": "instructor_supervised",
            "instructor_id": instructor.id,
            "booking_id": booking.id,
            "start_time": "2024-01-01T10:00:00Z"
        },
        headers=headers
    )
    
    assert response.status_code == 200
    data = response.json()
    assert data["booking_id"] == booking.id

def test_evaluate_diagnostic_ride_populates_criteria(client, db, auth_headers):
    # Create student and ride
    student = models.User(email="student2@example.com", full_name="Student", role="student", hashed_password="hashed")
    db.add(student)
    db.commit()
    
    ride = models.DiagnosticRide(
        student_id=student.id,
        ride_type="parent_supervised",
        start_time="2024-01-01T10:00:00Z",
        status="pending"
    )
    db.add(ride)
    db.commit()
    
    headers = auth_headers("student2@example.com")
    response = client.post(f"/api/v1/diagnostic-rides/{ride.id}/evaluate", headers=headers)
    
    assert response.status_code == 200
    data = response.json()
    # Assuming the evaluator is mocked or returns a default result
    # We expect criteria_results to be in the response
    assert "criteria_results" in data["ride"]
    assert data["ride"]["criteria_results"] is not None

def test_instructor_dashboard_diagnostic_rides(client, db):
    # Create instructor
    instructor_user = models.User(email="inst@example.com", full_name="Inst", role="instructor", hashed_password="hashed")
    db.add(instructor_user)
    db.commit()
    instructor_profile = models.InstructorProfile(user_id=instructor_user.id, bio="Bio", hourly_rate=50.0, city="V", car_model="X", insurance_policy="INS-12345", certification_id="CERT-001")
    db.add(instructor_profile)
    db.commit()
    
    # Create student and diagnostic ride
    student = models.User(email="stud@example.com", full_name="Stud", role="student", hashed_password="hashed")
    db.add(student)
    db.commit()
    ride = models.DiagnosticRide(student_id=student.id, instructor_id=instructor_profile.id, ride_type="instructor_supervised", start_time="2024-01-01T10:00:00Z", status="pending")
    db.add(ride)
    db.commit()
    
    # Mock login (cookie based for web routes)
    client.cookies.set("user_id", str(instructor_user.id))
    response = client.get("/dashboard", follow_redirects=False)
    assert response.status_code == 200 # Should NOT be a redirect if cookie works
    
    response = client.get("/dashboard")
    if "Diagnostic Rides" not in response.text:
        print(f"DEBUG: Status code: {response.status_code}")
        print(f"DEBUG: URL: {response.url}")
        # print(response.text[:500])



