import pytest
from sqlalchemy.orm import Session
from fastapi import Depends
from app import models

def test_get_consolidated_progress(client, db, auth_headers):
    # Setup: Create student, quiz questions, lessons, and diagnostic rides
    student = models.User(email="student3@example.com", full_name="Student", role="student", hashed_password="hashed")
    db.add(student)
    db.commit()
    
    # Add a mock instructor for sessions
    instructor = models.InstructorProfile(user_id=student.id, bio="Bio", hourly_rate=50.0, city="Vancouver", car_model="X", insurance_policy="Y", certification_id="Z")
    db.add(instructor)
    db.commit()

    # Add a completed diagnostic ride
    ride = models.DiagnosticRide(
        student_id=student.id,
        ride_type="parent_supervised",
        status="completed",
        passed=True,
        overall_score=80.0,
        created_at="2024-01-01T10:00:00Z"
    )
    db.add(ride)
    
    # Add some sessions
    booking = models.BookingRequest(student_id=student.id, instructor_id=instructor.id, date="2024-01-01", time="10:00", duration=2, status="completed")
    db.add(booking)
    db.commit()
    
    session = models.DrivingSession(booking_id=booking.id, duration_minutes=120, weather_condition="Sunny", road_type="City", observation_data="", space_margin_data="", speed_data="", steering_data="", communication_data="", shared_feedback="Good", created_at="2024-01-01")
    db.add(session)
    db.commit()
    
    headers = auth_headers("student3@example.com")
    response = client.get("/api/v1/users/me/progress", headers=headers)
    
    assert response.status_code == 200
    data = response.json()
    assert data["student_id"] == student.id
    assert data["diagnostic_ride_passed"] is True
    assert data["total_lessons"] >= 1
    # Check overall_score aggregation (this logic needs to be implemented)
    assert data["overall_score"] > 0

def test_student_progress_page_rendering(client, db):
    # Create student
    student = models.User(email="stud_page@example.com", full_name="Stud Page", role="student", hashed_password="hashed")
    db.add(student)
    db.commit()
    
    profile = models.StudentProfile(user_id=student.id, age=20, l_license_number="1234567")
    db.add(profile)
    db.commit()

    
    # Create a diagnostic ride
    ride = models.DiagnosticRide(
        student_id=student.id,
        ride_type="parent_supervised",
        status="completed",
        passed=True,
        overall_score=85.0,
        criteria_results='{"criteria_met": ["Braking"], "criteria_failed": ["Speed"]}',
        created_at="2024-01-01T10:00:00Z"
    )
    db.add(ride)
    
    progress = models.StudentProgress(student_id=student.id, overall_score=85.0, diagnostic_ride_passed=True)
    db.add(progress)
    db.commit()

    
    # Mock login
    from app.main import app, get_current_user as get_web_user
    from app.api.deps import get_db
    def override_get_web_user(db: Session = Depends(get_db)):
        return db.query(models.User).filter(models.User.email == "stud_page@example.com").first()
    app.dependency_overrides[get_web_user] = override_get_web_user


    response = client.get("/progress")
    assert response.status_code == 200
    assert "My Progress Report" in response.text
    assert "Diagnostic Ride Results" in response.text
    assert "85.0%" in response.text
    assert "Braking" in response.text

def test_mobile_student_progress_fetch(client, db, auth_headers):
    # Create student with unique email
    student = models.User(email="stud_mob_unique@example.com", full_name="Stud Mob", role="student", hashed_password="hashed")
    db.add(student)
    db.commit()
    
    # Add progress record
    progress = models.StudentProgress(student_id=student.id, overall_score=72.0)
    db.add(progress)
    
    # Add diagnostic ride with all required fields
    ride = models.DiagnosticRide(
        student_id=student.id,
        ride_type="parent_supervised",
        status="completed",
        passed=False,
        overall_score=65.0,
        start_time="2024-02-01T12:00:00Z", # Ensure start_time is set
        created_at="2024-02-01T12:00:00Z"
    )
    db.add(ride)
    db.commit()
    
    headers = auth_headers("stud_mob_unique@example.com")

    
    # 1. Test /users/me/progress
    resp1 = client.get("/api/v1/users/me/progress", headers=headers)
    assert resp1.status_code == 200
    # Aggregation logic uses latest diagnostic ride score (65.0)
    assert resp1.json()["overall_score"] == 65.0

    
    # 2. Test /diagnostic-rides/
    resp2 = client.get("/api/v1/diagnostic-rides/", headers=headers)
    assert resp2.status_code == 200
    assert len(resp2.json()) >= 1
    assert resp2.json()[0]["passed"] is False




