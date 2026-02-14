import pytest
from sqlalchemy.orm import Session
from app import models

def test_diagnostic_ride_new_fields(db: Session):
    # This test expects booking_id and criteria_results to exist on DiagnosticRide
    # It should FAIL until we update app/models.py
    ride = models.DiagnosticRide(
        student_id=1,
        ride_type="instructor_supervised",
        booking_id=123,
        criteria_results='{"braking": "pass", "speed": "fail"}',
        status="pending"
    )
    db.add(ride)
    db.commit()
    db.refresh(ride)
    
    assert ride.booking_id == 123
    assert ride.criteria_results == '{"braking": "pass", "speed": "fail"}'

def test_student_progress_model_exists(db: Session):
    # This test expects a StudentProgress model to exist
    # It should FAIL until we update app/models.py
    progress = models.StudentProgress(
        student_id=1,
        overall_score=85.5,
        total_lessons=10,
        quizzes_completed=5,
        diagnostic_ride_passed=True
    )
    db.add(progress)
    db.commit()
    db.refresh(progress)
    
    assert progress.overall_score == 85.5

def test_instructor_profile_stripe_fields(db: Session):
    # This test expects stripe fields to exist on InstructorProfile
    # It should FAIL until we update app/models.py
    profile = models.InstructorProfile(
        user_id=1,
        bio="Bio",
        hourly_rate=50.0,
        city="V",
        car_model="X",
        insurance_policy="Y",
        certification_id="Z",
        stripe_account_id="acct_123",
        stripe_onboarding_completed=True
    )
    db.add(profile)
    db.commit()
    db.refresh(profile)
    
    assert profile.stripe_account_id == "acct_123"
    assert profile.stripe_onboarding_completed is True

