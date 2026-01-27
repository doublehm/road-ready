from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File
from sqlalchemy.orm import Session
from app import schemas, models, security, database
from app.api import deps
import shutil
import os
import uuid

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

@router.post("/", response_model=schemas.UserWithProfile)
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
            age=0, # Default, to be updated later
            l_license_number="0000000"
        )
        db.add(profile)
    elif user.role == "instructor":
        profile = models.InstructorProfile(
            user_id=db_user.id,
            bio="New instructor",
            hourly_rate=0.0,
            city="Unknown",
            car_model="Unknown",
            insurance_policy="PENDING",
            certification_id="PENDING"
        )
        db.add(profile)
    
    db.commit()
    return db_user

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
        
    sensitive_fields = ["license_image", "insurance_image", "insurance_policy", "certification_id"]
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
