from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List
from app import models, schemas, database
from app.api import deps
import datetime

router = APIRouter()

@router.get("/", response_model=List[schemas.Message])
def read_messages(
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user),
    skip: int = 0,
    limit: int = 100
):
    """
    Retrieve messages for the current user (both sent and received).
    """
    messages = db.query(models.Message).filter(
        (models.Message.sender_id == current_user.id) | 
        (models.Message.recipient_id == current_user.id)
    ).offset(skip).limit(limit).all()
    return messages

@router.post("/", response_model=schemas.Message)
def create_message(
    message: schemas.MessageCreate,
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user),
):
    """
    Send a new message.
    """
    # Verify recipient exists
    recipient = db.query(models.User).filter(models.User.id == message.recipient_id).first()
    if not recipient:
        raise HTTPException(status_code=404, detail="Recipient not found")

    # Check for active booking between users
    # Active statuses: pending, accepted, paid, pending_payment, cancellation_requested
    active_statuses = ["pending", "accepted", "paid", "pending_payment", "cancellation_requested"]
    
    # Check if current_user is student and recipient is instructor
    booking_as_student = db.query(models.BookingRequest).join(models.InstructorProfile).filter(
        models.BookingRequest.student_id == current_user.id,
        models.InstructorProfile.user_id == message.recipient_id,
        models.BookingRequest.status.in_(active_statuses)
    ).first()

    # Check if current_user is instructor and recipient is student
    booking_as_instructor = db.query(models.BookingRequest).join(models.InstructorProfile).filter(
        models.InstructorProfile.user_id == current_user.id,
        models.BookingRequest.student_id == message.recipient_id,
        models.BookingRequest.status.in_(active_statuses)
    ).first()

    if not booking_as_student and not booking_as_instructor:
        raise HTTPException(
            status_code=403, 
            detail="You can only message users with whom you have an active booking."
        )

    db_message = models.Message(
        sender_id=current_user.id,
        recipient_id=message.recipient_id,
        content=message.content,
        timestamp=datetime.datetime.now().isoformat(),
        is_read=False
    )
    db.add(db_message)
    db.commit()
    db.refresh(db_message)
    return db_message

@router.put("/{other_user_id}/read")
def mark_messages_read(
    other_user_id: int,
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    """
    Mark all messages from specific user as read.
    """
    # Update messages where sender is other_user and recipient is current_user
    db.query(models.Message).filter(
        models.Message.sender_id == other_user_id,
        models.Message.recipient_id == current_user.id,
        models.Message.is_read == False
    ).update({"is_read": True})
    
    db.commit()
    return {"status": "success"}
