from app.database import SessionLocal
from app.models import DrivingSession
from sqlalchemy import func

db = SessionLocal()
duplicates = db.query(DrivingSession.booking_id, func.count(DrivingSession.id))\
    .group_by(DrivingSession.booking_id)\
    .having(func.count(DrivingSession.id) > 1)\
    .all()

print(f"Found {len(duplicates)} bookings with duplicate sessions.")
for booking_id, count in duplicates:
    print(f"Booking ID {booking_id} has {count} sessions.")
    sessions = db.query(DrivingSession).filter(DrivingSession.booking_id == booking_id).all()
    for s in sessions:
        print(f" - Session ID: {s.id}, Created At: {s.created_at}")

db.close()