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
        
    for key, value in profile.dict().items():
        setattr(db_profile, key, value)
        
    db.commit()
    db.refresh(db_profile)
    return db_profile
