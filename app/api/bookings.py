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

    # Diagnostic ride bookings go through payment first
    is_diagnostic = booking.notes and "Diagnostic Ride" in booking.notes

    db_booking = models.BookingRequest(
        **booking.dict(),
        student_id=current_user.id,
        total_amount=total_amount,
        platform_fee=platform_fee,
        instructor_payout=instructor_payout,
        status="pending_payment" if is_diagnostic else "pending"
    )
    db.add(db_booking)

    if not is_diagnostic:
        # Notify instructor immediately for regular bookings
        # Diagnostic ride bookings notify after payment completes
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
    module_id: int = None,
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    """
    List bookings with optional module filtering.
    """
    if current_user.role == "instructor":
        query = db.query(models.BookingRequest).filter(
            models.BookingRequest.instructor_id == current_user.instructor_profile.id
        )
    else:
        query = db.query(models.BookingRequest).filter(
            models.BookingRequest.student_id == current_user.id
        )

    # Filter by module if specified
    if module_id:
        query = query.filter(models.BookingRequest.module_id == module_id)

    bookings = query.offset(skip).limit(limit).all()
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


@router.post("/{booking_id}/complete")
async def complete_booking(
    booking_id: int,
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    """
    Mark a booking as completed and update module progress hours.
    Allows instructors (for regular lessons) and students (for self-supervised/parent rides)
    to complete the booking.
    """
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()

    if not booking:
        raise HTTPException(status_code=404, detail="Booking not found")

    # Permission check: instructor of the booking OR the student themselves
    is_instructor = (current_user.role == "instructor" and booking.instructor.user_id == current_user.id)
    is_student = (current_user.role == "student" and booking.student_id == current_user.id)

    if not is_instructor and not is_student:
        raise HTTPException(status_code=403, detail="Not authorized to complete this booking")

    # Mark booking as completed
    booking.status = "completed"

    # If booking is associated with a module, update progress
    if booking.module_id:
        progress = db.query(models.StudentModuleProgress).filter(
            models.StudentModuleProgress.student_id == booking.student_id,
            models.StudentModuleProgress.module_id == booking.module_id
        ).first()

        if progress:
            progress.hours_completed += booking.duration

            # Check if module is completed
            module = db.query(models.LearningModule).filter(
                models.LearningModule.id == booking.module_id
            ).first()

            if module and progress.hours_completed >= module.min_hours:
                progress.status = "completed"
                progress.completed_at = datetime.now().isoformat()

                # Unlock next module (if any)
                next_module = db.query(models.LearningModule).filter(
                    models.LearningModule.order == module.order + 1
                ).first()

                if next_module:
                    next_progress = db.query(models.StudentModuleProgress).filter(
                        models.StudentModuleProgress.student_id == booking.student_id,
                        models.StudentModuleProgress.module_id == next_module.id
                    ).first()

                    if next_progress and next_progress.status == "locked":
                        next_progress.status = "unlocked"
                        next_progress.unlocked_at = datetime.now().isoformat()

    db.commit()
    return {"status": "success", "message": "Booking marked as completed"}
