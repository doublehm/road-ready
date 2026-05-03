from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File
from sqlalchemy.orm import Session
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
    if user_update.email and user_update.email != current_user.email:
        if db.query(models.User).filter(models.User.email == user_update.email).first():
            raise HTTPException(status_code=400, detail="Email already registered")
        current_user.email = user_update.email
        
    if user_update.full_name:
        current_user.full_name = user_update.full_name
        
    if user_update.phone_number:
        current_user.phone_number = user_update.phone_number
        
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
