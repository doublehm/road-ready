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

    booking_data = booking.dict()
    focus_list = booking_data.pop("focus_areas", None)
    focus_str = ",".join(focus_list) if focus_list else None

    db_booking = models.BookingRequest(
        **booking_data,
        focus_areas=focus_str,
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


class FocusUpdateRequest(schemas.BaseModel):
    focus_areas: List[str]


@router.get("/{booking_id}/shared-dashboard")
async def get_shared_booking_dashboard(
    booking_id: int,
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    """
    Fetch the unified shared booking dashboard containing vehicle profiles, pre-lesson focuses,
    telemetry stats, student license details, and driving session evaluations.
    Accessible to both the student and the instructor assigned to the booking.
    """
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking:
        raise HTTPException(status_code=404, detail="Booking not found")

    # Map/Relation access:
    # student_id points to models.User (the student)
    # instructor points to models.InstructorProfile
    is_student = (booking.student_id == current_user.id)
    is_instructor = (booking.instructor.user_id == current_user.id)

    if not is_student and not is_instructor:
        raise HTTPException(status_code=403, detail="Not authorized to access this booking's shared dashboard.")

    # Gather Student Info
    student_user = booking.student
    student_profile = student_user.student_profile if student_user else None
    
    # Gather Instructor Info
    instructor_profile = booking.instructor
    instructor_user = instructor_profile.user if instructor_profile else None

    # Get student's latest diagnostic ride overall score baseline for alignment
    latest_diagnostic = db.query(models.DiagnosticRide).filter(
        models.DiagnosticRide.student_id == booking.student_id,
        models.DiagnosticRide.status == "completed"
    ).order_by(models.DiagnosticRide.evaluated_at.desc()).first()

    dashboard_data = {
        "booking_id": booking.id,
        "date": booking.date,
        "time": booking.time,
        "duration": booking.duration,
        "pickup_address": booking.pickup_address,
        "dropoff_address": booking.dropoff_address,
        "status": booking.status,
        "notes": booking.notes,
        "focus_areas": booking.focus_areas.split(",") if booking.focus_areas else [],
        "student": {
            "id": student_user.id if student_user else None,
            "full_name": student_user.full_name if student_user else None,
            "age": student_profile.age if student_profile else None,
            "basics_skipped": student_profile.basics_skipped if student_profile else False,
            "baseline_diagnostic_score": latest_diagnostic.overall_score if latest_diagnostic else None,
            "baseline_diagnostic_passed": latest_diagnostic.passed if latest_diagnostic else False
        },
        "instructor": {
            "id": instructor_profile.id if instructor_profile else None,
            "full_name": instructor_user.full_name if instructor_user else None,
            "bio": instructor_profile.bio if instructor_profile else None,
            "vehicle": {
                "make": getattr(instructor_profile, "car_make", "Unknown"),
                "model": instructor_profile.car_model,
                "year": getattr(instructor_profile, "car_year", 0)
            }
        },
        "driving_session": None
    }

    if booking.driving_session:
        session = booking.driving_session
        dashboard_data["driving_session"] = {
            "id": session.id,
            "duration_minutes": session.duration_minutes,
            "weather_condition": session.weather_condition,
            "road_type": session.road_type,
            "shared_feedback": session.shared_feedback
        }

    return dashboard_data


@router.put("/{booking_id}/focus")
async def update_booking_focus(
    booking_id: int,
    focus_data: FocusUpdateRequest,
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    """
    Allow students to set or update learning objectives / focus areas before the lesson.
    """
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking:
        raise HTTPException(status_code=404, detail="Booking not found")

    if booking.student_id != current_user.id:
        raise HTTPException(status_code=403, detail="Only the student assigned to the booking can update focus areas.")

    if booking.status in ["completed", "cancelled"]:
        raise HTTPException(status_code=400, detail="Cannot update focus areas of a completed or cancelled booking.")

    booking.focus_areas = ",".join(focus_data.focus_areas) if focus_data.focus_areas else None
    db.commit()
    return {"status": "success", "focus_areas": focus_data.focus_areas}
