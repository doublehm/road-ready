from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List
from app import schemas, models
from app.api import deps

router = APIRouter()

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

    db_booking = models.BookingRequest(
        **booking.dict(), 
        student_id=current_user.id,
        total_amount=total_amount,
        status="pending"
    )
    db.add(db_booking)
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
