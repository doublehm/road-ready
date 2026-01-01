import sys
import os

sys.path.append(os.path.join(os.path.dirname(os.path.abspath(__file__)), "lib"))

from app import models, database
from sqlalchemy.orm import Session

db = database.SessionLocal()

# Ensure tables exist
models.Base.metadata.create_all(bind=database.engine)

def seed_data():
    # Check if data exists
    if db.query(models.User).count() > 0:
        print("Data already exists. Skipping seed.")
        return

    # Instructor 1
    inst1 = models.User(
        email="john@roadready.com",
        hashed_password="password",
        full_name="John Driver",
        role="instructor",
        phone_number="555-0101"
    )
    db.add(inst1)
    db.commit()
    db.refresh(inst1)
    
    prof1 = models.InstructorProfile(
        user_id=inst1.id,
        bio="10 years of experience teaching defensive driving. Patient and calm.",
        hourly_rate=50.0,
        city="Toronto",
        car_model="2021 Honda Civic",
        insurance_policy="INS-12345",
        certification_id="CERT-999",
        is_verified=True,
        is_available=True
    )
    db.add(prof1)

    # Instructor 2
    inst2 = models.User(
        email="sarah@roadready.com",
        hashed_password="password",
        full_name="Sarah Speed",
        role="instructor",
        phone_number="555-0102"
    )
    db.add(inst2)
    db.commit()
    db.refresh(inst2)
    
    prof2 = models.InstructorProfile(
        user_id=inst2.id,
        bio="Specializing in highway driving and parallel parking.",
        hourly_rate=45.0,
        city="Vancouver",
        car_model="2022 Toyota Prius",
        insurance_policy="INS-67890",
        certification_id="CERT-888",
        is_verified=True,
        is_available=True
    )
    db.add(prof2)

    # Instructor 3
    inst3 = models.User(
        email="mike@roadready.com",
        hashed_password="password",
        full_name="Mike Moto",
        role="instructor",
        phone_number="555-0103"
    )
    db.add(inst3)
    db.commit()
    db.refresh(inst3)
    
    prof3 = models.InstructorProfile(
        user_id=inst3.id,
        bio="Affordable lessons for beginners. Flexible schedule.",
        hourly_rate=35.0,
        city="Toronto",
        car_model="2019 Hyundai Elantra",
        insurance_policy="INS-11223",
        certification_id="CERT-777",
        is_verified=True,
        is_available=True
    )
    db.add(prof3)

    db.commit()
    print("Dummy data seeded successfully!")

if __name__ == "__main__":
    seed_data()
