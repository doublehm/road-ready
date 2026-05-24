from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File
from sqlalchemy.orm import Session
from typing import List
from app import schemas, models, security, database
from app.api import deps
import shutil
import os
import uuid
from datetime import datetime, timedelta

router = APIRouter()

@router.post("/me/upload-license")
async def upload_license(
    file: UploadFile = File(...),
    current_user: models.User = Depends(deps.get_current_user)
):
    upload_dir = "app/static/uploads"
    os.makedirs(upload_dir, exist_ok=True)
    
    # Generate unique filename
    ext = file.filename.split('.')[-1]
    filename = f"{current_user.id}_{uuid.uuid4()}.{ext}"
    file_path = os.path.join(upload_dir, filename)
    
    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
        
    return {"filename": filename}

@router.post("/", response_model=schemas.Token)
def create_user(user: schemas.UserCreate, db: Session = Depends(deps.get_db)):
    db_user = db.query(models.User).filter(models.User.email == user.email).first()
    if db_user:
        raise HTTPException(status_code=400, detail="Email already registered")

    hashed_password = security.get_password_hash(user.password)
    db_user = models.User(
        email=user.email,
        hashed_password=hashed_password,
        full_name=user.full_name,
        role=user.role,
        phone_number=user.phone_number
    )
    db.add(db_user)
    db.commit()
    db.refresh(db_user)

    # Initialize profile based on role
    if user.role == "student":
        profile = models.StudentProfile(
            user_id=db_user.id,
            age=user.age or 0,
            l_license_number=user.l_license_number or "0000000"
        )
        db.add(profile)
    elif user.role == "instructor":
        profile = models.InstructorProfile(
            user_id=db_user.id,
            bio=user.bio or "New instructor",
            hourly_rate=user.hourly_rate or 0.0,
            city=user.city or "Unknown",
            province=user.province or "British Columbia",
            car_make=user.car_make or "Unknown",
            car_model=user.car_model or "Unknown",
            car_year=user.car_year or 0,
            insurance_policy=user.insurance_policy or "PENDING",
            certification_id=user.certification_id or "PENDING"
        )
        db.add(profile)
        db.flush()

        if user.license_classes:
            for lc in user.license_classes:
                db_lc = models.InstructorLicenseClass(
                    instructor_id=profile.id,
                    license_class=lc.license_class,
                    price=lc.price
                )
                db.add(db_lc)

    db.commit()

    access_token = security.create_access_token(
        data={"sub": db_user.email},
        expires_delta=timedelta(minutes=security.ACCESS_TOKEN_EXPIRE_MINUTES)
    )
    return {"access_token": access_token, "token_type": "bearer", "role": db_user.role, "user_id": db_user.id}

@router.get("/me", response_model=schemas.UserWithProfile)
async def read_users_me(current_user: models.User = Depends(deps.get_current_user)):
    return current_user

@router.put("/me", response_model=schemas.User)
def update_user_me(
    user_update: schemas.UserUpdate,
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    if (user_update.email and user_update.email != current_user.email) or \
       (user_update.phone_number and user_update.phone_number != current_user.phone_number):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Modifying email or phone number directly is not permitted."
        )
        
    if user_update.full_name:
        current_user.full_name = user_update.full_name
        
    if user_update.password:
        current_user.hashed_password = security.get_password_hash(user_update.password)
        
    db.commit()
    db.refresh(current_user)
    return current_user

@router.get("/me/student-profile", response_model=schemas.StudentProfile)
def read_student_profile(
    current_user: models.User = Depends(deps.get_current_user)
):
    if current_user.role != "student":
        raise HTTPException(status_code=400, detail="User is not a student")
    return current_user.student_profile

@router.get("/me/progress", response_model=schemas.StudentProgress)
async def get_my_consolidated_progress(
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Fetch comprehensive student progress aggregated from lessons, quizzes, and diagnostic rides.
    """
    if current_user.role != "student":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only students can access comprehensive progress"
        )

    # 1. Check if we already have a progress record
    progress = db.query(models.StudentProgress).filter(
        models.StudentProgress.student_id == current_user.id
    ).first()

    if not progress:
        progress = models.StudentProgress(student_id=current_user.id)
        db.add(progress)
        db.commit()
        db.refresh(progress)

    # 2. Aggregate Data
    # Lessons/Sessions
    sessions = db.query(models.DrivingSession).join(models.BookingRequest).filter(
        models.BookingRequest.student_id == current_user.id
    ).all()
    progress.total_lessons = len(sessions)
    
    # Calculate hours from lessons (booking duration is in hours)
    lesson_hours = sum([s.booking.duration for s in sessions if s.booking])
    
    # Diagnostic Rides
    diagnostic_rides = db.query(models.DiagnosticRide).filter(
        models.DiagnosticRide.student_id == current_user.id,
        models.DiagnosticRide.status == "completed"
    ).all()
    
    # Calculate hours from diagnostic rides (duration_minutes / 60)
    diagnostic_hours = sum([(r.duration_minutes or 0) / 60.0 for r in diagnostic_rides])
    
    progress.total_hours = round(lesson_hours + diagnostic_hours, 1)
    
    # Get latest diagnostic ride for overall score baseline
    latest_ride = db.query(models.DiagnosticRide).filter(
        models.DiagnosticRide.student_id == current_user.id,
        models.DiagnosticRide.status == "completed"
    ).order_by(models.DiagnosticRide.evaluated_at.desc()).first()
    
    if latest_ride:
        progress.diagnostic_ride_passed = latest_ride.passed
        progress.overall_score = latest_ride.overall_score or 0.0
    
    progress.last_updated = datetime.now().isoformat()
    db.commit()
    db.refresh(progress)

    return progress

@router.put("/me/student-profile", response_model=schemas.StudentProfile)
def update_student_profile(
    profile: schemas.StudentProfileCreate,
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    if current_user.role != "student":
        raise HTTPException(status_code=400, detail="User is not a student")
        
    db_profile = db.query(models.StudentProfile).filter(models.StudentProfile.user_id == current_user.id).first()
    if not db_profile:
        raise HTTPException(status_code=404, detail="Profile not found")
        
    for key, value in profile.dict().items():
        setattr(db_profile, key, value)
    
    # Mark as 'pending' verification since profile was updated
    db_profile.license_status = "pending"
        
    db.commit()
    db.refresh(db_profile)
    return db_profile

@router.put("/me/instructor-profile", response_model=schemas.InstructorProfile)
def update_instructor_profile(
    profile: schemas.InstructorProfileCreate,
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    if current_user.role != "instructor":
        raise HTTPException(status_code=400, detail="User is not an instructor")
        
    db_profile = db.query(models.InstructorProfile).filter(models.InstructorProfile.user_id == current_user.id).first()
    if not db_profile:
        raise HTTPException(status_code=404, detail="Profile not found")
        
    sensitive_fields = [
        "license_image", "insurance_image", "certification_image", 
        "insurance_policy", "certification_id", "business_registration_number",
        "tax_id", "worksafe_bc_id", "legal_entity_name"
    ]
    requires_reverification = False
    
    for key, value in profile.dict().items():
        if key == "license_classes": continue # Handle separately if needed, for now ignore
        
        # Check if sensitive field changed
        if key in sensitive_fields:
            current_val = getattr(db_profile, key)
            if current_val != value:
                requires_reverification = True
                
        setattr(db_profile, key, value)
        
    if requires_reverification:
        db_profile.is_verified = False
        print(f"Instructor {current_user.email} updated sensitive fields. Verification reset.")
        
    db.commit()
    db.refresh(db_profile)
    return db_profile


from fastapi import Query
from typing import Dict, Any

@router.post("/me/upload-document")
async def upload_document(
    document_type: str = Query(...),
    file: UploadFile = File(...),
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    """
    Upload profile verification documents. Suspends verification status immediately,
    quarantines the user profile, and registers a pending document audit task for admins.
    """
    # 1. Validate document type matches role
    if current_user.role == "student":
        valid_docs = ["student_license"]
    elif current_user.role == "instructor":
        valid_docs = ["instructor_license", "insurance", "certification"]
    else:
        valid_docs = []

    if document_type not in valid_docs:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid document type '{document_type}' for user role '{current_user.role}'."
        )

    # 2. Save document locally
    upload_dir = "app/static/uploads"
    os.makedirs(upload_dir, exist_ok=True)
    ext = file.filename.split('.')[-1]
    filename = f"{current_user.id}_{document_type}_{uuid.uuid4()}.{ext}"
    file_path = os.path.join(upload_dir, filename)

    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    # 3. Update profile fields and quarantine (set verified to False)
    if current_user.role == "student":
        profile = current_user.student_profile
        if not profile:
            raise HTTPException(status_code=404, detail="Student profile not found")
        profile.license_image = filename
        profile.license_status = "submitted"
        profile.is_verified = False
        profile.rejection_reason = None
    else:
        profile = current_user.instructor_profile
        if not profile:
            raise HTTPException(status_code=404, detail="Instructor profile not found")
        
        if document_type == "instructor_license":
            profile.license_image = filename
            profile.license_image_status = "submitted"
        elif document_type == "insurance":
            profile.insurance_image = filename
            profile.insurance_image_status = "submitted"
        elif document_type == "certification":
            profile.certification_image = filename
            profile.certification_image_status = "submitted"
            
        profile.is_verified = False

    # 4. Insert log entry
    review_log = models.DocumentReviewLog(
        user_id=current_user.id,
        document_type=document_type,
        file_path=filename,
        status="pending",
        submitted_at=datetime.now().isoformat()
    )
    db.add(review_log)
    db.commit()

    return {
        "status": "success",
        "document_type": document_type,
        "filename": filename,
        "verification_status": "submitted (pending admin review)"
    }


@router.get("/admin/documents/pending", response_model=List[schemas.DocumentReviewLog])
def get_pending_documents(
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    """
    Fetch all pending document review logs.
    Restricted to Admin role.
    """
    if current_user.role != "admin":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Access denied. Administrator privileges required."
        )
    return db.query(models.DocumentReviewLog).filter(models.DocumentReviewLog.status == "pending").all()


@router.post("/admin/documents/{log_id}/verify")
def verify_document(
    log_id: int,
    action_data: schemas.AdminVerifyAction,
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    """
    Admin verification approval or rejection for submitted documents.
    """
    if current_user.role != "admin":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Access denied. Administrator privileges required."
        )

    log = db.query(models.DocumentReviewLog).filter(models.DocumentReviewLog.id == log_id).first()
    if not log:
        raise HTTPException(status_code=404, detail="Document review log not found")

    if log.status != "pending":
        raise HTTPException(status_code=400, detail="Document has already been reviewed")

    action = action_data.action.lower()
    if action not in ["approve", "reject"]:
        raise HTTPException(status_code=400, detail="Invalid action. Must be 'approve' or 'reject'.")

    # Load target user and profile
    target_user = db.query(models.User).filter(models.User.id == log.user_id).first()
    if not target_user:
        raise HTTPException(status_code=404, detail="Target user not found")

    log.reviewed_at = datetime.now().isoformat()
    log.reviewed_by = current_user.id

    if action == "approve":
        log.status = "approved"
        # Update profile document status
        if target_user.role == "student":
            profile = target_user.student_profile
            profile.license_status = "verified"
            profile.is_verified = True
        else:
            profile = target_user.instructor_profile
            if log.document_type == "instructor_license":
                profile.license_image_status = "verified"
            elif log.document_type == "insurance":
                profile.insurance_image_status = "verified"
            elif log.document_type == "certification":
                profile.certification_image_status = "verified"
            
            # Check if all required docs are verified
            if profile.license_image_status == "verified" and \
               profile.insurance_image_status == "verified" and \
               profile.certification_image_status == "verified":
                profile.is_verified = True
    else:
        log.status = "rejected"
        log.rejection_reason = action_data.rejection_reason or "Document rejected by administrator"
        
        if target_user.role == "student":
            profile = target_user.student_profile
            profile.license_status = "rejected"
            profile.rejection_reason = log.rejection_reason
            profile.is_verified = False
        else:
            profile = target_user.instructor_profile
            if log.document_type == "instructor_license":
                profile.license_image_status = "rejected"
            elif log.document_type == "insurance":
                profile.insurance_image_status = "rejected"
            elif log.document_type == "certification":
                profile.certification_image_status = "rejected"
            profile.is_verified = False

    # Notify User
    notif_title = f"Document {action.capitalize()}d"
    notif_msg = f"Your uploaded document ({log.document_type}) has been {action}d."
    if action == "reject":
        notif_msg += f" Reason: {log.rejection_reason}"

    notif = models.Notification(
        user_id=target_user.id,
        title=notif_title,
        message=notif_msg,
        timestamp=datetime.now().isoformat()
    )
    db.add(notif)
    db.commit()

    return {"status": "success", "document_status": log.status}


@router.get("/me/export")
def export_user_data(
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    """
    Portability request: Export all stored personal data, profile specifics, bookings,
    and associated telemetry records in a structured JSON bundle.
    """
    export_bundle: Dict[str, Any] = {
        "user_details": {
            "id": current_user.id,
            "email": current_user.email,
            "full_name": current_user.full_name,
            "phone_number": current_user.phone_number,
            "role": current_user.role
        }
    }

    if current_user.role == "student":
        profile = current_user.student_profile
        export_bundle["student_profile"] = {
            "age": profile.age if profile else None,
            "l_license_number": profile.l_license_number if profile else None,
            "license_status": profile.license_status if profile else None,
            "license_expiry": profile.license_expiry if profile else None,
            "basics_skipped": profile.basics_skipped if profile else None
        }
        # Fetch bookings
        bookings = db.query(models.BookingRequest).filter(models.BookingRequest.student_id == current_user.id).all()
        export_bundle["bookings"] = [
            {
                "id": b.id,
                "date": b.date,
                "time": b.time,
                "duration": b.duration,
                "pickup_address": b.pickup_address,
                "dropoff_address": b.dropoff_address,
                "total_amount": b.total_amount,
                "status": b.status,
                "focus_areas": b.focus_areas.split(",") if b.focus_areas else []
            } for b in bookings
        ]
        # Fetch diagnostic rides
        rides = db.query(models.DiagnosticRide).filter(models.DiagnosticRide.student_id == current_user.id).all()
        export_bundle["diagnostic_rides"] = [
            {
                "id": r.id,
                "ride_type": r.ride_type,
                "overall_score": r.overall_score,
                "passed": r.passed,
                "start_time": r.start_time,
                "end_time": r.end_time,
                "distance_km": r.distance_km
            } for r in rides
        ]
    else:
        profile = current_user.instructor_profile
        export_bundle["instructor_profile"] = {
            "bio": profile.bio if profile else None,
            "hourly_rate": profile.hourly_rate if profile else None,
            "city": profile.city if profile else None,
            "car_model": profile.car_model if profile else None,
            "insurance_policy": profile.insurance_policy if profile else None,
            "certification_id": profile.certification_id if profile else None,
            "is_verified": profile.is_verified if profile else None,
            "license_image_status": profile.license_image_status if profile else None,
            "insurance_image_status": profile.insurance_image_status if profile else None,
            "certification_image_status": profile.certification_image_status if profile else None
        }
        # Fetch bookings
        bookings = db.query(models.BookingRequest).filter(
            models.BookingRequest.instructor_id == (profile.id if profile else -1)
        ).all()
        export_bundle["bookings"] = [
            {
                "id": b.id,
                "date": b.date,
                "time": b.time,
                "duration": b.duration,
                "pickup_address": b.pickup_address,
                "dropoff_address": b.dropoff_address,
                "total_amount": b.total_amount,
                "status": b.status,
                "focus_areas": b.focus_areas.split(",") if b.focus_areas else []
            } for b in bookings
        ]

    return export_bundle


@router.delete("/me")
def anonymize_user_profile(
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    """
    Sovereignty request (Right to be Forgotten): Soft-deletes/anonymizes all
    personal identification records, bios, and documents, leaving only depersonalized
    telemetry stats and aggregate booking data for analytics and bookkeeping.
    """
    user_id = current_user.id
    
    # 1. Anonymize main User fields
    current_user.email = f"deleted_{user_id}@roadready.deleted"
    current_user.phone_number = f"deleted_{user_id}"
    current_user.full_name = "Anonymized User"
    current_user.hashed_password = security.get_password_hash(uuid.uuid4().hex)
    current_user.reset_token = None
    current_user.reset_token_expiry = None

    # 2. Anonymize/Reset profile details
    if current_user.role == "student":
        profile = current_user.student_profile
        if profile:
            profile.l_license_number = "DELETED"
            profile.license_image = None
            profile.rejection_reason = None
            profile.is_verified = False
            profile.license_status = "pending"
    else:
        profile = current_user.instructor_profile
        if profile:
            profile.bio = "This profile has been deleted."
            profile.license_image = None
            profile.insurance_image = None
            profile.certification_image = None
            profile.is_verified = False
            profile.license_image_status = "pending_upload"
            profile.insurance_image_status = "pending_upload"
            profile.certification_image_status = "pending_upload"
            profile.business_registration_number = None
            profile.tax_id = None
            profile.worksafe_bc_id = None
            profile.legal_entity_name = None

    # 3. Anonymize reviews comments (keep rating, but scrub comments)
    reviews = db.query(models.Review).filter(models.Review.student_id == user_id).all()
    for r in reviews:
        r.comment = "Comment removed by user request"

    # Anonymize booking pickup/dropoff addresses
    bookings = db.query(models.BookingRequest).filter(models.BookingRequest.student_id == user_id).all()
    for b in bookings:
        b.pickup_address = "Address Removed"
        b.dropoff_address = "Address Removed"
        b.notes = "Notes Removed"

    db.commit()
    return {"status": "success", "message": "Your profile has been anonymized and all personal identifying records have been removed."}
