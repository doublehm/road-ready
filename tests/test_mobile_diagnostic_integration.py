import pytest
from app import models

def test_mobile_diagnostic_ride_submission(client, db):
    # Create instructor
    inst_user = models.User(email="inst_mob@example.com", full_name="Mob Inst", role="instructor", hashed_password="hashed")
    db.add(inst_user)
    db.commit()
    inst_profile = models.InstructorProfile(user_id=inst_user.id, bio="Bio", hourly_rate=50.0, city="V", car_model="X", insurance_policy="Y", certification_id="Z")
    db.add(inst_profile)
    db.commit()
    
    # Create student and diagnostic ride
    student = models.User(email="stud_mob@example.com", full_name="Mob Stud", role="student", hashed_password="hashed")
    db.add(student)
    db.commit()
    
    ride = models.DiagnosticRide(
        student_id=student.id, 
        instructor_id=inst_profile.id, 
        ride_type="instructor_supervised", 
        start_time="2024-01-01T10:00:00Z", 
        status="pending"
    )
    db.add(ride)
    db.commit()
    
    # Mock login
    client.cookies.set("user_id", str(inst_user.id))
    
    # Submission via multipart/form-data (as used by React Native)
    form_data = {
        "passed": "true",
        "overall_score": "92.5",
        "criteria_braking": "true",
        "criteria_speed": "false",
        "criteria_steering": "true",
        "notes": "Excellent control, watch speed."
    }
    
    response = client.post(f"/log-diagnostic-ride/{ride.id}", data=form_data, follow_redirects=False)
    
    assert response.status_code == 303 # Redirect back to dashboard
    
    # Verify DB update
    db.refresh(ride)
    assert ride.status == "completed"
    assert ride.passed is True
    assert ride.overall_score == 92.5
    assert "Smooth Braking" in ride.criteria_results
    assert "Speed Compliance" in ride.criteria_results
    assert "Excellent control" in ride.evaluator_notes
