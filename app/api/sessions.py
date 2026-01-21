from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List
from datetime import datetime
from app import schemas, models
from app.api import deps

router = APIRouter()

@router.post("/", response_model=schemas.DrivingSession)
async def create_session(
    session: schemas.DrivingSessionCreate, 
    db: Session = Depends(deps.get_db), 
    current_user: models.User = Depends(deps.get_current_user)
):
    if current_user.role != "instructor":
        raise HTTPException(status_code=403, detail="Only instructors can create sessions")

    # Fetch booking to validate
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == session.booking_id).first()
    if not booking:
        raise HTTPException(status_code=404, detail="Booking not found")
        
    if booking.instructor_id != current_user.instructor_profile.id:
        raise HTTPException(status_code=403, detail="You can only grade your own bookings")
        
    if booking.status == "completed":
        raise HTTPException(status_code=400, detail="This session has already been graded and completed")

    db_session = models.DrivingSession(
        **session.dict(),
        created_at=datetime.utcnow().isoformat()
    )
    db.add(db_session)
    
    # Mark booking as completed
    booking.status = "completed"
    
    # Notify Student
    notif = models.Notification(
        user_id=booking.student_id,
        title="Session Completed",
        message=f"Your session on {booking.date} has been graded by {current_user.full_name}. Check your progress report!",
        timestamp=datetime.now().isoformat()
    )
    db.add(notif)
    
    db.commit()
    db.refresh(db_session)
    return db_session

@router.get("/", response_model=List[schemas.DrivingSession])
async def read_sessions(
    skip: int = 0,
    limit: int = 100,
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    """
    List driving sessions.
    Instructors see sessions they taught.
    Students see sessions they participated in.
    """
    if current_user.role == "instructor":
        # Get bookings for this instructor
        instructor_bookings = db.query(models.BookingRequest.id).filter(
            models.BookingRequest.instructor_id == current_user.instructor_profile.id
        ).subquery()
        
        sessions = db.query(models.DrivingSession).filter(
            models.DrivingSession.booking_id.in_(instructor_bookings)
        ).offset(skip).limit(limit).all()
        
    else: # Student
        # Get bookings for this student
        student_bookings = db.query(models.BookingRequest.id).filter(
            models.BookingRequest.student_id == current_user.id
        ).subquery()
        
        sessions = db.query(models.DrivingSession).filter(
            models.DrivingSession.booking_id.in_(student_bookings)
        ).offset(skip).limit(limit).all()
        
    return sessions
