import pytest
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
