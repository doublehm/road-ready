from app.database import SessionLocal
from app.models import InstructorProfile
from sqlalchemy import func

db = SessionLocal()
duplicates = db.query(InstructorProfile.user_id, func.count(InstructorProfile.id))\
    .group_by(InstructorProfile.user_id)\
    .having(func.count(InstructorProfile.id) > 1)\
    .all()

print(f"Found {len(duplicates)} users with duplicate instructor profiles.")
for user_id, count in duplicates:
    print(f"User ID {user_id} has {count} instructor profiles.")
    profiles = db.query(InstructorProfile).filter(InstructorProfile.user_id == user_id).all()
    for p in profiles:
        print(f" - Profile ID: {p.id}, Verified: {p.is_verified}")

db.close()