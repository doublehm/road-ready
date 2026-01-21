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
    # Fetch all sessions for this booking
    sessions = db.query(DrivingSession).filter(DrivingSession.booking_id == booking_id).order_by(DrivingSession.id.desc()).all()
    
    # Keep the first one (latest by ID), delete the rest
    latest_session = sessions[0]
    sessions_to_delete = sessions[1:]
    
    print(f"Booking {booking_id}: Keeping Session {latest_session.id}, Deleting {len(sessions_to_delete)} others.")
    
    for s in sessions_to_delete:
        db.delete(s)

db.commit()
print("Duplicate sessions removed.")
db.close()