
import sys
import os
import datetime

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.getcwd(), "lib"))

from app import models, database, security

db = database.SessionLocal()

def seed_booking():
    # 1. Get the Instructor (John)
    instructor = db.query(models.User).filter(models.User.email == "john@roadready.com").first()
    if not instructor:
        print("Instructor John not found. Please run seed.py first.")
        return

    # 2. Get or Create a Student
    student_email = "student@example.com"
    student = db.query(models.User).filter(models.User.email == student_email).first()
    
    if not student:
        print("Creating dummy student...")
        student = models.User(
            email=student_email,
            hashed_password=security.get_password_hash("password"),
            full_name="Alice Learner",
            role="student",
            phone_number="555-9999"
        )
        db.add(student)
        db.commit()
        db.refresh(student)
        
        # Create student profile
        s_profile = models.StudentProfile(
            user_id=student.id,
            age=18,
            l_license_number="L-1234567",
            license_status="verified"
        )
        db.add(s_profile)
        db.commit()

    # 3. Create a Booking Request
    # Date: Tomorrow
    tomorrow = (datetime.date.today() + datetime.timedelta(days=1)).isoformat()
    
    booking = models.BookingRequest(
        student_id=student.id,
        instructor_id=instructor.instructor_profile.id,
        date=tomorrow,
        time="10:00",
        duration=2,
        pickup_address="123 Main St, Toronto",
        dropoff_address="456 Queen St, Toronto",
        notes="I want to practice parallel parking.",
        total_amount=100.0, # 2 hours * $50
        instructor_payout=90.0, # 90% payout
        platform_fee=10.0,
        status="confirmed", # Directly confirmed for testing
        payment_status="paid"
    )
    
    db.add(booking)
    db.commit()
    print(f"Booking created for {tomorrow} at 10:00!")

if __name__ == "__main__":
    seed_booking()
