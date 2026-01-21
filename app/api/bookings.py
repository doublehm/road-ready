from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List
from app import schemas, models
from app.api import deps

router = APIRouter()

from datetime import datetime

@router.post("/", response_model=schemas.BookingRequest)
async def create_booking(
    booking: schemas.BookingRequestCreate, 
    db: Session = Depends(deps.get_db), 
    current_user: models.User = Depends(deps.get_current_user)
):
    # Simple calculation for total amount (mock rate)
    instructor = db.query(models.InstructorProfile).filter(models.InstructorProfile.id == booking.instructor_id).first()
    if not instructor:
        raise HTTPException(status_code=404, detail="Instructor not found")
        
    total_amount = instructor.hourly_rate * booking.duration
    platform_fee = total_amount * 0.10
    instructor_payout = total_amount - platform_fee

    db_booking = models.BookingRequest(
        **booking.dict(), 
        student_id=current_user.id,
        total_amount=total_amount,
        platform_fee=platform_fee,
        instructor_payout=instructor_payout,
        status="pending"
    )
    db.add(db_booking)
    
    # Notify Instructor
    notif = models.Notification(
        user_id=instructor.user_id,
        title="New Booking Request",
        message=f"You have a new booking request from {current_user.full_name} for {booking.date}.",
        timestamp=datetime.now().isoformat()
    )
    db.add(notif)
    
    db.commit()
    db.refresh(db_booking)
    return db_booking

@router.get("/", response_model=List[schemas.BookingRequest])
async def read_bookings(
    skip: int = 0, 
    limit: int = 100, 
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    if current_user.role == "instructor":
        bookings = db.query(models.BookingRequest).filter(
            models.BookingRequest.instructor_id == current_user.instructor_profile.id
        ).offset(skip).limit(limit).all()
    else:
        bookings = db.query(models.BookingRequest).filter(
            models.BookingRequest.student_id == current_user.id
        ).offset(skip).limit(limit).all()
    return bookings

@router.post("/{booking_id}/action")
async def handle_booking(
    booking_id: int,
    action_data: schemas.BookingAction,
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    if current_user.role != "instructor":
        raise HTTPException(status_code=403, detail="Not authorized")
    
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    
    if not booking:
        raise HTTPException(status_code=404, detail="Booking not found")

    # Ensure this booking belongs to the logged-in instructor
    if booking.instructor.user_id != current_user.id:
        raise HTTPException(status_code=403, detail="Not authorized")
    
    action = action_data.action
    reason = action_data.reason
        
    if action == "accept":
        booking.status = "accepted"
        msg = f"Your booking on {booking.date} has been accepted by {current_user.full_name}."
    elif action == "reject":
        booking.status = "rejected"
        msg = f"Your booking on {booking.date} was declined by {current_user.full_name}."
        if reason:
            msg += f"\nReason: {reason}"
    else:
        raise HTTPException(status_code=400, detail="Invalid action")
    
    # Notify Student
    notif = models.Notification(
        user_id=booking.student_id,
        title=f"Booking {action.capitalize()}ed",
        message=msg,
        timestamp=datetime.now().isoformat()
    )
    db.add(notif)
        
    db.commit()
    return {"status": "success", "booking_status": booking.status}
