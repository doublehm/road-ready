import sys
import os

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "lib"))

from fastapi import FastAPI, Depends, Request, Form, status, HTTPException, UploadFile, File
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session
from typing import Optional, List
from datetime import datetime, timedelta
import json
import uuid
import shutil
import os
from passlib.context import CryptContext
from pydantic import ValidationError
import stripe

from . import models, schemas, database
from app.api import deps
from app.services.notification_service import create_notification

# Stripe Configuration
stripe.api_key = os.getenv("STRIPE_API_KEY")
STRIPE_WEBHOOK_SECRET = os.getenv("STRIPE_WEBHOOK_SECRET")

# Password hashing setup
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

def verify_password(plain_password, hashed_password):
    return pwd_context.verify(plain_password, hashed_password)

def get_password_hash(password):
    return pwd_context.hash(password)

# Create tables
models.Base.metadata.create_all(bind=database.engine)

app = FastAPI(title="Road Ready")

from app.api.router import api_router
app.include_router(api_router, prefix="/api/v1")

app.mount("/static", StaticFiles(directory="app/static"), name="static")
templates = Jinja2Templates(directory="app/templates")

@app.get('/favicon.ico', include_in_schema=False)
async def favicon():
    return FileResponse('app/favicon.ico')

# --- Constants ---
FAULT_MAP = {
    "A1": "Shoulder Check", "A2": "Scan", "A3": "Mirror Check", "A4": "360° Check",
    "A5": "Direction of Travel", "A6": "Backing", "A7": "Hazard Perception", "A8": "Other",
    "B1": "Lane Position", "B2": "Follow Distance", "B3": "Stops Too Close/Far", "B4": "Gap",
    "B5": "Blocks Crosswalk", "B6": "Turn Position", "B7": "Occupied Crosswalk", "B8": "Manoeuvre Location",
    "B9": "Other", "B10": "Stop Position", "B11": "Road Position (Parking Lot)", "B12": "3-Point/U-Turn",
    "B13": "Parking Margins", "B14": "Railroad Crossing",
    "C1": "Speed Maintenance", "C2": "Rolling Stop", "C3": "Amber Light", "C4": "Accel/Decel",
    "C5": "Shifting", "C6": "Rolling Back", "C7": "Other", "C8": "Covers Brakes", "C9": "Parking Brake",
    "D1": "General Steering", "D2": "Other", "D3": "Steering Wheel Position", "D4": "Weight Transfer",
    "E1": "Signal", "E2": "Timing", "E3": "Cancel", "E4": "Other"
}

# --- Helpers ---
def expand_codes(code_string: Optional[str]) -> str:
    if not code_string:
        return ""
    codes = [c.strip() for c in code_string.split(',') if c.strip()]
    expanded = []
    for code in codes:
        desc = FAULT_MAP.get(code, code)
        expanded.append(f"{code} - {desc}")
    return ", ".join(expanded)

def linkify_chapter(text: str) -> str:
    if not text: return ""
    import re
    
    chapter_map = {
        "1": 5, "2": 15, "3": 30, "4": 50,
        "5": 70, "6": 90, "7": 110, "8": 120
    }
    
    def replace_match(match):
        full_match = match.group(0)
        chapter_num = match.group(1)
        page = chapter_map.get(chapter_num, 1)
        link_text = full_match.strip("()")
        return f'(<a href="/education/bc?page={page}" target="_blank" class="text-decoration-none">{link_text} 🔗</a>)'

    pattern = r"\(See Chapter (\d+).*?\)"
    return re.sub(pattern, replace_match, text)

def from_json(value):
    return json.loads(value)

templates.env.filters["expand_codes"] = expand_codes
templates.env.filters["linkify_chapter"] = linkify_chapter
templates.env.filters["from_json"] = from_json


# Dependency
def get_db():
    db = database.SessionLocal()
    try:
        yield db
    finally:
        db.close()

# --- Dummy Auth Logic ---
def get_current_user_id(request: Request):
    return request.cookies.get("user_id")

def get_current_user(request: Request, db: Session = Depends(get_db)):
    user_id = get_current_user_id(request)
    if not user_id:
        return None
    try:
        uid = int(user_id)
        return db.query(models.User).filter(models.User.id == uid).first()
    except (ValueError, TypeError):
        return None

def get_unread_count(db: Session, user: models.User) -> int:
    if not user: return 0
    return db.query(models.Message).filter(
        models.Message.recipient_id == user.id,
        models.Message.is_read == False
    ).count()

# --- Routes ---

@app.get("/education", response_class=HTMLResponse)
async def education_home(request: Request, user: models.User = Depends(get_current_user), db: Session = Depends(get_db)):
    return templates.TemplateResponse("education_home.html", {
        "request": request,
        "user": user,
        "unread_count": get_unread_count(db, user) if user else 0
    })

@app.get("/education/bc", response_class=HTMLResponse)
async def education_book_bc(request: Request, user: models.User = Depends(get_current_user), db: Session = Depends(get_db)):
    return templates.TemplateResponse("education_book.html", {
        "request": request,
        "user": user,
        "unread_count": get_unread_count(db, user) if user else 0
    })

@app.get("/education/bc/html", response_class=HTMLResponse)
async def education_book_bc_html(request: Request):
    return templates.TemplateResponse("handbook_content.html", {"request": request})

@app.get("/education/quiz", response_class=HTMLResponse)
async def education_quiz(request: Request, user: models.User = Depends(get_current_user), db: Session = Depends(get_db)):
    from sqlalchemy.sql.expression import func
    questions = db.query(models.QuizQuestion).order_by(func.random()).limit(50).all()
    
    return templates.TemplateResponse("quiz.html", {
        "request": request,
        "user": user,
        "questions": questions,
        "unread_count": get_unread_count(db, user) if user else 0
    })

@app.post("/education/quiz/submit", response_class=HTMLResponse)
async def submit_quiz(
    request: Request,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    form_data = await request.form()
    score = 0
    results = []
    
    for key, value in form_data.items():
        if key.startswith("q_"):
            q_id = int(key.split("_")[1])
            selected_option = value
            question = db.query(models.QuizQuestion).filter(models.QuizQuestion.id == q_id).first()
            if question:
                is_correct = (selected_option == question.correct_option)
                if is_correct: score += 1
                results.append({
                    "question": question,
                    "selected": selected_option,
                    "is_correct": is_correct
                })
    
    return templates.TemplateResponse("quiz_result.html", {
        "request": request,
        "user": user,
        "score": score,
        "total": len(results),
        "results": results,
        "unread_count": get_unread_count(db, user) if user else 0
    })

@app.get("/terms", response_class=HTMLResponse)
async def terms_page(request: Request):
    return templates.TemplateResponse("terms.html", {"request": request})

@app.get("/privacy", response_class=HTMLResponse)
async def privacy_page(request: Request):
    return templates.TemplateResponse("privacy.html", {"request": request})

@app.get("/contact", response_class=HTMLResponse)
async def contact_page(request: Request):
    return templates.TemplateResponse("contact.html", {"request": request})

@app.post("/contact", response_class=HTMLResponse)
async def submit_contact(request: Request):
    return templates.TemplateResponse("contact.html", {"request": request, "success": True})

@app.get("/ping")
def ping():
    print("PING RECEIVED FROM CLIENT!")
    return {"message": "pong"}

@app.get("/", response_class=HTMLResponse)
async def read_root(request: Request, user: models.User = Depends(get_current_user), db: Session = Depends(get_db)):
    unread = 0
    if user:
        try:
            unread = get_unread_count(db, user)
        except Exception:
            pass
    return templates.TemplateResponse("home.html", {"request": request, "user": user, "unread_count": unread})

@app.get("/login", response_class=HTMLResponse)
async def login_page(request: Request):
    return templates.TemplateResponse("login.html", {"request": request})

@app.post("/login")
async def login(
    request: Request,
    email: str = Form(...),
    password: str = Form(...),
    db: Session = Depends(get_db)
):
    user = db.query(models.User).filter(models.User.email == email).first()
    if not user or not verify_password(password, user.hashed_password):
        return templates.TemplateResponse("login.html", {"request": request, "error": "Invalid credentials"})
    
    response = RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)
    response.set_cookie(key="user_id", value=str(user.id))
    return response

@app.get("/logout")
async def logout(request: Request):
    response = RedirectResponse(url="/", status_code=status.HTTP_303_SEE_OTHER)
    response.delete_cookie("user_id")
    return response

@app.get("/forgot-password", response_class=HTMLResponse)
async def forgot_password_page(request: Request):
    return templates.TemplateResponse("forgot_password.html", {"request": request})

@app.post("/forgot-password", response_class=HTMLResponse)
async def forgot_password(
    request: Request,
    email: str = Form(...),
    db: Session = Depends(get_db)
):
    user = db.query(models.User).filter(models.User.email == email).first()
    if user:
        token = str(uuid.uuid4())
        user.reset_token = token
        user.reset_token_expiry = (datetime.now() + timedelta(hours=1)).isoformat()
        db.commit()
    return templates.TemplateResponse("forgot_password.html", {"request": request, "message": "If an account exists, a link has been sent."})

@app.get("/reset-password", response_class=HTMLResponse)
async def reset_password_page(request: Request, token: str):
    return templates.TemplateResponse("reset_password.html", {"request": request, "token": token})

@app.post("/reset-password", response_class=HTMLResponse)
async def reset_password(
    request: Request,
    token: str = Form(...),
    password: str = Form(...),
    confirm_password: str = Form(...),
    db: Session = Depends(get_db)
):
    if password != confirm_password:
        return templates.TemplateResponse("reset_password.html", {"request": request, "token": token, "error": "Passwords do not match"})
    user = db.query(models.User).filter(models.User.reset_token == token).first()
    if not user:
        return templates.TemplateResponse("reset_password.html", {"request": request, "token": token, "error": "Invalid token"})
    
    user.hashed_password = get_password_hash(password)
    user.reset_token = None
    db.commit()
    return templates.TemplateResponse("login.html", {"request": request, "message": "Password reset successful."})

@app.get("/register", response_class=HTMLResponse)
async def register_page(request: Request):
    return templates.TemplateResponse("register.html", {"request": request})

@app.post("/register")
async def register(
    request: Request,
    email: str = Form(...),
    password: str = Form(...),
    full_name: str = Form(...),
    phone: str = Form(...),
    role: str = Form(...),
    db: Session = Depends(get_db)
):
    try:
        schemas.UserCreate(email=email, password=password, full_name=full_name, phone_number=phone, role=role)
    except ValidationError as e:
        try: error_msg = e.errors()[0]['msg']
        except: error_msg = str(e)
        return templates.TemplateResponse("register.html", {
            "request": request, "error": error_msg,
            "email_value": email, "name_value": full_name, "phone_value": phone
        })

    if db.query(models.User).filter(models.User.email == email).first():
        return templates.TemplateResponse("register.html", {"request": request, "error": "Email already registered"})
    
    new_user = models.User(email=email, hashed_password=get_password_hash(password), full_name=full_name, phone_number=phone, role=role)
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    
    response = RedirectResponse(url="/setup-profile", status_code=status.HTTP_303_SEE_OTHER)
    response.set_cookie(key="user_id", value=str(new_user.id))
    return response

@app.get("/setup-profile", response_class=HTMLResponse)
async def setup_profile_page(request: Request, user: models.User = Depends(get_current_user), db: Session = Depends(get_db)):
    if not user: return RedirectResponse(url="/login")
    if user.role == "instructor":
        return templates.TemplateResponse("setup_instructor.html", {"request": request, "user": user, "unread_count": get_unread_count(db, user)})
    else:
        return templates.TemplateResponse("setup_student.html", {"request": request, "user": user, "unread_count": get_unread_count(db, user)})

@app.post("/setup-instructor")
async def setup_instructor(
    request: Request,
    bio: str = Form(...),
    hourly_rate: float = Form(...),
    city: str = Form(...),
    car_model: str = Form(...),
    insurance_policy: str = Form(...),
    certification_id: str = Form(...),
    license_image: UploadFile = File(...),
    insurance_image: UploadFile = File(...),
    license_classes: str = Form("[]"), # JSON string: [{"license_class": "Class 5", "price": 50.0}]
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    # Save the files
    upload_dir = "app/static/uploads"
    
    # License Image
    filename_license = f"{user.id}_{license_image.filename}"
    file_path_license = os.path.join(upload_dir, filename_license)
    with open(file_path_license, "wb") as buffer:
        shutil.copyfileobj(license_image.file, buffer)

    # Insurance Image
    filename_insurance = f"{user.id}_ins_{insurance_image.filename}"
    file_path_insurance = os.path.join(upload_dir, filename_insurance)
    with open(file_path_insurance, "wb") as buffer:
        shutil.copyfileobj(insurance_image.file, buffer)
    
    profile = models.InstructorProfile(
        user_id=user.id,
        bio=bio,
        hourly_rate=hourly_rate,
        city=city,
        car_model=car_model,
        insurance_policy=insurance_policy,
        certification_id=certification_id,
        license_image=filename_license,
        insurance_image=filename_insurance,
        is_verified=False # Pending admin approval
    )
    db.add(profile)
    db.flush() # Flush to get profile.id

    # Parse and add license classes
    try:
        classes_data = json.loads(license_classes)
        for cls in classes_data:
            new_license_class = models.InstructorLicenseClass(
                instructor_id=profile.id,
                license_class=cls.get('license_class'),
                price=float(cls.get('price', hourly_rate))
            )
            db.add(new_license_class)
    except json.JSONDecodeError:
        pass # Ignore bad JSON

    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/api/v1/setup-instructor")
async def setup_instructor_api(
    request: Request,
    bio: str = Form(...),
    hourly_rate: float = Form(...),
    city: str = Form(...),
    car_model: str = Form(...),
    insurance_policy: str = Form(...),
    certification_id: str = Form(...),
    license_image: UploadFile = File(...),
    insurance_image: UploadFile = File(...),
    license_classes: str = Form("[]"),
    db: Session = Depends(get_db),
    user: models.User = Depends(deps.get_current_user)
):
    if not user: raise HTTPException(status_code=401, detail="Not authenticated")
    
    # Save the files
    upload_dir = "app/static/uploads"
    
    filename_license = f"{user.id}_{license_image.filename}"
    with open(os.path.join(upload_dir, filename_license), "wb") as buffer:
        shutil.copyfileobj(license_image.file, buffer)

    filename_insurance = f"{user.id}_ins_{insurance_image.filename}"
    with open(os.path.join(upload_dir, filename_insurance), "wb") as buffer:
        shutil.copyfileobj(insurance_image.file, buffer)
    
    # Check for existing profile
    profile = db.query(models.InstructorProfile).filter(models.InstructorProfile.user_id == user.id).first()
    
    if profile:
        # Update existing
        profile.bio = bio
        profile.hourly_rate = hourly_rate
        profile.city = city
        profile.car_model = car_model
        profile.insurance_policy = insurance_policy
        profile.certification_id = certification_id
        profile.license_image = filename_license
        profile.insurance_image = filename_insurance
        # is_verified stays as is or reset? Let's keep is_verified False on update for safety? 
        # Or maybe True for prototype. Let's reset to False to require re-approval if docs change.
        profile.is_verified = False 
    else:
        # Create new
        profile = models.InstructorProfile(
            user_id=user.id,
            bio=bio,
            hourly_rate=hourly_rate,
            city=city,
            car_model=car_model,
            insurance_policy=insurance_policy,
            certification_id=certification_id,
            license_image=filename_license,
            insurance_image=filename_insurance,
            is_verified=False
        )
        db.add(profile)
    
    db.flush()

    # Update License Classes: Delete old, add new
    db.query(models.InstructorLicenseClass).filter(models.InstructorLicenseClass.instructor_id == profile.id).delete()
    
    try:
        classes_data = json.loads(license_classes)
        for cls in classes_data:
            new_license_class = models.InstructorLicenseClass(
                instructor_id=profile.id,
                license_class=cls.get('license_class'),
                price=float(cls.get('price', hourly_rate))
            )
            db.add(new_license_class)
    except json.JSONDecodeError:
        pass

    db.commit()
    return {"status": "success"}

@app.post("/setup-student")
async def setup_student(
    request: Request,
    age: int = Form(...),
    l_license_number: str = Form(...),
    license_image: UploadFile = File(...),
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    try:
        schemas.StudentProfileBase(age=age, l_license_number=l_license_number)
    except ValidationError as e:
        try: error_msg = e.errors()[0]['msg']
        except: error_msg = str(e)
        return templates.TemplateResponse("setup_student.html", {"request": request, "user": user, "error": error_msg, "unread_count": get_unread_count(db, user)})

    if age < 16:
         return templates.TemplateResponse("setup_student.html", {"request": request, "user": user, "error": "You must be at least 16 years old."})

    upload_dir = "app/static/uploads"
    if not os.path.exists(upload_dir): os.makedirs(upload_dir)
    filename = f"student_{user.id}_{license_image.filename}"
    with open(os.path.join(upload_dir, filename), "wb") as buffer:
        shutil.copyfileobj(license_image.file, buffer)

    profile = models.StudentProfile(user_id=user.id, age=age, l_license_number=l_license_number, license_image=filename, is_verified=False)
    db.add(profile)
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.get("/admin", response_class=HTMLResponse)
async def admin_dashboard(request: Request, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    # Fetch ALL data for management
    instructors = db.query(models.InstructorProfile).join(models.User).all()
    students = db.query(models.StudentProfile).join(models.User).all()
    bookings = db.query(models.BookingRequest).order_by(models.BookingRequest.date.desc(), models.BookingRequest.time.desc()).all()
    
    return templates.TemplateResponse("admin_dashboard.html", {
        "request": request,
        "user": user,
        "instructors": instructors,
        "students": students,
        "bookings": bookings
    })

@app.post("/admin/delete-user/{user_id}")
async def admin_delete_user(request: Request, user_id: int, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    target_user = db.query(models.User).filter(models.User.id == user_id).first()
    if target_user:
        if target_user.instructor_profile: db.delete(target_user.instructor_profile)
        if target_user.student_profile: db.delete(target_user.student_profile)
        db.delete(target_user)
        db.commit()
    return RedirectResponse(url="/admin", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/admin/delete-booking/{booking_id}")
async def admin_delete_booking(request: Request, booking_id: int, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if booking:
        db.delete(booking)
        db.commit()
    return RedirectResponse(url="/admin", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/admin/update-booking/{booking_id}")
async def admin_update_booking(request: Request, booking_id: int, date: str = Form(...), time: str = Form(...), pickup_address: str = Form(...), status: str = Form(...), db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking: raise HTTPException(status_code=404)
    booking.date = date
    booking.time = time
    booking.pickup_address = pickup_address
    booking.status = status
    db.commit()
    return RedirectResponse(url="/admin", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/admin/update-profile/{user_id}")
async def admin_update_profile(request: Request, user_id: int, role: str = Form(...), full_name: str = Form(...), hourly_rate: Optional[float] = Form(None), city: Optional[str] = Form(None), is_verified: Optional[bool] = Form(False), license_status: Optional[str] = Form(None), db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    target_user = db.query(models.User).filter(models.User.id == user_id).first()
    if not target_user: raise HTTPException(status_code=404)
    target_user.full_name = full_name
    if role == "instructor" and target_user.instructor_profile:
        target_user.instructor_profile.hourly_rate = hourly_rate
        target_user.instructor_profile.city = city
        target_user.instructor_profile.is_verified = is_verified
    elif role == "student" and target_user.student_profile:
        target_user.student_profile.license_status = license_status
        target_user.student_profile.is_verified = (license_status == 'verified')
    db.commit()
    return RedirectResponse(url="/admin", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/admin/verify/{profile_id}")
async def admin_verify_instructor(request: Request, profile_id: int, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    profile = db.query(models.InstructorProfile).filter(models.InstructorProfile.id == profile_id).first()
    if profile:
        profile.is_verified = True
        db.commit()
    return RedirectResponse(url="/admin", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/admin/verify-student/{profile_id}")
async def admin_verify_student(request: Request, profile_id: int, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    profile = db.query(models.StudentProfile).filter(models.StudentProfile.id == profile_id).first()
    if profile:
        profile.is_verified = True
        db.commit()
    return RedirectResponse(url="/admin", status_code=status.HTTP_303_SEE_OTHER)

@app.get("/dashboard", response_class=HTMLResponse)
async def dashboard(request: Request, user: models.User = Depends(get_current_user), db: Session = Depends(get_db), city: Optional[str] = None, max_price: Optional[float] = None):
    if not user: return RedirectResponse(url="/login")
    
    if user.role == "student":
        query = db.query(models.InstructorProfile).join(models.User).filter(models.InstructorProfile.is_available == True, models.InstructorProfile.is_verified == True)
        if city: query = query.filter(models.InstructorProfile.city.ilike(f"%{city}%"))
        if max_price: query = query.filter(models.InstructorProfile.hourly_rate <= max_price)
        instructors = query.all()
        for inst in instructors:
            reviews = inst.reviews
            if reviews:
                inst.average_rating = round(sum(r.rating for r in reviews) / len(reviews), 1)
                inst.review_count = len(reviews)
            else:
                inst.average_rating = None
                inst.review_count = 0
        all_bookings = db.query(models.BookingRequest).filter(models.BookingRequest.student_id == user.id).order_by(models.BookingRequest.date.desc(), models.BookingRequest.time.desc()).all()
        active_bookings = [b for b in all_bookings if b.status in ['pending', 'accepted', 'cancellation_requested']]
        return templates.TemplateResponse("student_dashboard.html", {"request": request, "user": user, "instructors": instructors, "active_bookings": active_bookings, "filters": {"city": city, "max_price": max_price}, "unread_count": get_unread_count(db, user)})
    
    else: # Instructor
        all_bookings = db.query(models.BookingRequest).filter(models.BookingRequest.instructor_id == user.instructor_profile.id).order_by(models.BookingRequest.date.desc(), models.BookingRequest.time.desc()).all()
        incoming_requests = [b for b in all_bookings if b.status == 'pending']
        active_appointments = [b for b in all_bookings if b.status in ['accepted', 'cancellation_requested']]
        
        # Fetch Diagnostic Rides
        diagnostic_rides = db.query(models.DiagnosticRide).filter(
            models.DiagnosticRide.instructor_id == user.instructor_profile.id,
            models.DiagnosticRide.status != 'completed'
        ).all()
        
        my_reviews = user.instructor_profile.reviews
        avg_rating = round(sum(r.rating for r in my_reviews) / len(my_reviews), 1) if my_reviews else "New"
        calendar_events = []
        for b in [b for b in all_bookings if b.status == 'accepted']:
            try:
                start_dt = datetime.strptime(f"{b.date} {b.time}", "%Y-%m-%d %H:%M")
                end_dt = start_dt + timedelta(hours=b.duration)
                calendar_events.append({"title": f"Lesson with {b.student.full_name}", "start": start_dt.isoformat(), "end": end_dt.isoformat(), "color": "#198754"})
            except: pass
        return templates.TemplateResponse("instructor_dashboard.html", {
            "request": request, 
            "user": user, 
            "incoming_requests": incoming_requests, 
            "active_appointments": active_appointments, 
            "diagnostic_rides": diagnostic_rides,
            "bookings": all_bookings, 
            "avg_rating": avg_rating, 
            "review_count": len(my_reviews), 
            "calendar_events": json.dumps(calendar_events), 
            "unread_count": get_unread_count(db, user)
        })

@app.get("/review/{instructor_id}", response_class=HTMLResponse)
async def review_form(request: Request, instructor_id: int, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    instructor = db.query(models.InstructorProfile).filter(models.InstructorProfile.id == instructor_id).first()
    return templates.TemplateResponse("review_form.html", {"request": request, "user": user, "instructor": instructor, "unread_count": get_unread_count(db, user)})

@app.post("/review/{instructor_id}")
async def submit_review(request: Request, instructor_id: int, rating: int = Form(...), comment: str = Form(...), db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    review = models.Review(instructor_id=instructor_id, student_id=user.id, rating=rating, comment=comment, created_at=datetime.now().strftime("%Y-%m-%d"))
    db.add(review)
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/toggle-availability")
async def toggle_availability(request: Request, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if user and user.instructor_profile:
        user.instructor_profile.is_available = not user.instructor_profile.is_available
        db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/availability/add")
async def add_availability(request: Request, day_of_week: int = Form(...), start_time: str = Form(...), end_time: str = Form(...), db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    if start_time >= end_time: return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)
    availability = models.InstructorAvailability(instructor_id=user.instructor_profile.id, day_of_week=day_of_week, start_time=start_time, end_time=end_time)
    db.add(availability)
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/availability/delete/{availability_id}")
async def delete_availability(request: Request, availability_id: int, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    avail = db.query(models.InstructorAvailability).filter(models.InstructorAvailability.id == availability_id, models.InstructorAvailability.instructor_id == user.instructor_profile.id).first()
    if avail:
        db.delete(avail)
        db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.get("/book/{instructor_id}", response_class=HTMLResponse)
async def booking_form(request: Request, instructor_id: int, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    instructor = db.query(models.InstructorProfile).filter(models.InstructorProfile.id == instructor_id).first()
    if not instructor: raise HTTPException(status_code=404, detail="Instructor not found")
    existing_bookings = db.query(models.BookingRequest).filter(models.BookingRequest.student_id == user.id, models.BookingRequest.instructor_id == instructor_id, models.BookingRequest.status.in_(["pending", "accepted"])).all()
    return templates.TemplateResponse("booking_form.html", {"request": request, "user": user, "instructor": instructor, "existing_bookings": existing_bookings, "unread_count": get_unread_count(db, user)})

@app.post("/book/{instructor_id}")
async def submit_booking(
    request: Request,
    instructor_id: int,
    date: str = Form(...),
    time: str = Form(...),
    duration: int = Form(...),
    address_line: str = Form(...),
    city: str = Form(...),
    province: str = Form(...),
    postal_code: str = Form(...),
    pickup_lat: float = Form(...),
    pickup_lng: float = Form(...),
    dropoff_lat: float = Form(...),
    dropoff_lng: float = Form(...),
    extra_cost: float = Form(0.0),
    notes: str = Form(""),
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    # Concatenate address parts
    pickup_address = f"{address_line}, {city}, {province} {postal_code}"
    
    # Validation logic
    try:
        new_start = datetime.strptime(f"{date} {time}", "%Y-%m-%d %H:%M")
        new_end = new_start + timedelta(hours=duration)
        instructor = db.query(models.InstructorProfile).filter(models.InstructorProfile.id == instructor_id).first()
        availabilities = instructor.availabilities
        
        if availabilities:
            day_of_week = new_start.weekday()
            is_valid_slot = False
            for slot in availabilities:
                if slot.day_of_week == day_of_week:
                    slot_start = datetime.strptime(f"{date} {slot.start_time}", "%Y-%m-%d %H:%M")
                    slot_end = datetime.strptime(f"{date} {slot.end_time}", "%Y-%m-%d %H:%M")
                    if new_start >= slot_start and new_end <= slot_end:
                        is_valid_slot = True
                        break
            if not is_valid_slot:
                existing_bookings = db.query(models.BookingRequest).filter(models.BookingRequest.student_id == user.id, models.BookingRequest.instructor_id == instructor_id, models.BookingRequest.status.in_(["pending", "accepted"])).all()
                return templates.TemplateResponse("booking_form.html", {"request": request, "user": user, "instructor": instructor, "existing_bookings": existing_bookings, "error_message": "Instructor not available.", "unread_count": get_unread_count(db, user)})
        
        existing_accepted = db.query(models.BookingRequest).filter(models.BookingRequest.instructor_id == instructor_id, models.BookingRequest.status == "accepted", models.BookingRequest.date == date).all()
        for booking in existing_accepted:
            b_start = datetime.strptime(f"{booking.date} {booking.time}", "%Y-%m-%d %H:%M")
            b_end = b_start + timedelta(hours=booking.duration)
            if new_start < b_end and new_end > b_start:
                existing_bookings = db.query(models.BookingRequest).filter(models.BookingRequest.student_id == user.id, models.BookingRequest.instructor_id == instructor_id, models.BookingRequest.status.in_(["pending", "accepted"])).all()
                return templates.TemplateResponse("booking_form.html", {"request": request, "user": user, "instructor": instructor, "existing_bookings": existing_bookings, "error_message": "Time conflict.", "unread_count": get_unread_count(db, user)})

    except ValueError: pass

    # Calculate Fees
    instructor = db.query(models.InstructorProfile).filter(models.InstructorProfile.id == instructor_id).first()
    duration_total = instructor.hourly_rate * duration
    total = duration_total + extra_cost
    fee = total * 0.15
    payout = total - fee

    booking = models.BookingRequest(
        student_id=user.id, 
        instructor_id=instructor_id, 
        date=date, 
        time=time, 
        duration=duration, 
        pickup_address=pickup_address, 
        pickup_lat=pickup_lat,
        pickup_lng=pickup_lng,
        dropoff_lat=dropoff_lat,
        dropoff_lng=dropoff_lng,
        extra_travel_cost=extra_cost,
        notes=notes, 
        total_amount=total, 
        platform_fee=fee, 
        instructor_payout=payout, 
        status="pending_payment"
    )
    db.add(booking)
    db.commit()
    db.refresh(booking)
    return RedirectResponse(url=f"/checkout/{booking.id}", status_code=status.HTTP_303_SEE_OTHER)

@app.get("/checkout/{booking_id}", response_class=HTMLResponse)
async def checkout_page(request: Request, booking_id: int, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking or booking.student_id != user.id: raise HTTPException(status_code=403, detail="Not authorized")
    
    # Create Stripe Payment Intent if not already exists or if it needs update
    client_secret = None
    if booking.status == "pending_payment":
        try:
            # Stripe expects amounts in cents (int)
            amount_cents = int(booking.total_amount * 100)
            fee_cents = int(booking.platform_fee * 100)
            
            # Destination account (Instructor's Stripe account)
            instructor = booking.instructor
            
            intent_params = {
                "amount": amount_cents,
                "currency": "cad",
                "automatic_payment_methods": {"enabled": True},
                "application_fee_amount": fee_cents,
            }
            
            # If instructor has completed onboarding, route funds to them
            if instructor.stripe_account_id and instructor.stripe_onboarding_completed:
                intent_params["transfer_data"] = {"destination": instructor.stripe_account_id}
            
            intent = stripe.PaymentIntent.create(**intent_params)
            
            booking.stripe_payment_intent_id = intent.id
            db.commit()
            client_secret = intent.client_secret
            
        except Exception as e:
            print(f"Stripe PaymentIntent error: {str(e)}")
            # In a real app, handle this gracefully in UI
    
    return templates.TemplateResponse("checkout.html", {
        "request": request, 
        "user": user, 
        "booking": booking, 
        "stripe_key": os.getenv("STRIPE_PUBLISHABLE_KEY", "pk_test_MOCK"),
        "client_secret": client_secret
    })

@app.post("/checkout/{booking_id}/process")
async def process_payment(request: Request, booking_id: int, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    booking.payment_status = "paid"
    booking.status = "pending"
    booking.stripe_payment_intent_id = f"pi_mock_{uuid.uuid4()}"
    db.commit()
    
    # Notify Instructor
    create_notification(db, booking.instructor.user_id, "New Booking Request", f"You have a new booking request from {booking.student.full_name}.")
    
    return templates.TemplateResponse("booking_success.html", {"request": request, "user": user, "unread_count": get_unread_count(db, user)})

@app.get("/payment/success")
async def payment_success(
    request: Request,
    booking_id: int,
    session_id: str,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    
    if booking:
        # verify session via Stripe API if key exists
        if stripe.api_key:
            try:
                session = stripe.checkout.Session.retrieve(session_id)
                if session.payment_status == 'paid':
                    booking.payment_status = "paid"
                    booking.status = "pending"
                    booking.stripe_payment_intent_id = session.payment_intent
                    db.commit()
            except:
                pass
        else:
            # Assume success for mock flow
            booking.payment_status = "paid"
            booking.status = "pending"
            db.commit()

    return templates.TemplateResponse("booking_success.html", {
        "request": request,
        "user": user,
        "unread_count": get_unread_count(db, user)
    })

@app.get("/stripe-callback")
async def stripe_callback(
    request: Request,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    """
    Callback handler for Stripe Connect onboarding.
    Verifies if onboarding was completed and updates instructor profile.
    """
    if not user or user.role != "instructor":
        return RedirectResponse(url="/login")
    
    instructor_profile = db.query(models.InstructorProfile).filter(
        models.InstructorProfile.user_id == user.id
    ).first()
    
    if instructor_profile and instructor_profile.stripe_account_id:
        try:
            # Check account status from Stripe
            account = stripe.Account.retrieve(instructor_profile.stripe_account_id)
            if account.details_submitted and account.charges_enabled:
                instructor_profile.stripe_onboarding_completed = True
                db.commit()
        except Exception as e:
            print(f"Stripe callback error: {str(e)}")
            
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/booking/{booking_id}/reject-license")
async def reject_booking_license(
    request: Request,
    booking_id: int,
    reason: str = Form(...),
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    
    if not booking or booking.instructor.user_id != user.id:
        raise HTTPException(status_code=403, detail="Not authorized")
    
    # 1. Reject Booking
    booking.status = "rejected"
    db.add(booking)

    # 2. Reject License
    # Explicitly join to ensure we have the record
    student_profile = db.query(models.StudentProfile).filter(models.StudentProfile.user_id == booking.student_id).first()
    
    if student_profile:
        student_profile.license_status = "rejected"
        student_profile.rejection_reason = reason
        student_profile.is_verified = False
        db.add(student_profile)
    
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/update-license")
async def update_license(
    request: Request,
    license_image: UploadFile = File(...),
    l_license_number: str = Form(...),
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user or user.role != "student": return RedirectResponse(url="/login")
    
    # Save the file
    upload_dir = "app/static/uploads"
    filename = f"student_{user.id}_updated_{license_image.filename}"
    file_path = os.path.join(upload_dir, filename)
    
    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(license_image.file, buffer)
        
    user.student_profile.license_image = filename
    user.student_profile.l_license_number = l_license_number
    user.student_profile.license_status = "pending"
    user.student_profile.rejection_reason = None
    
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/booking/{booking_id}/route")
async def update_booking_route(
    booking_id: int,
    pickup_lat: float = Form(...),
    pickup_lng: float = Form(...),
    dropoff_lat: float = Form(...),
    dropoff_lng: float = Form(...),
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking or booking.instructor.user_id != user.id: raise HTTPException(status_code=403, detail="Not authorized")
    booking.pickup_lat = pickup_lat
    booking.pickup_lng = pickup_lng
    booking.dropoff_lat = dropoff_lat
    booking.dropoff_lng = dropoff_lng
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.get("/log-session/{booking_id}", response_class=HTMLResponse)
async def log_session_form(request: Request, booking_id: int, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking or booking.instructor.user_id != user.id: raise HTTPException(status_code=403, detail="Not authorized")
    return templates.TemplateResponse("log_session_form.html", {"request": request, "user": user, "booking": booking, "fault_map": FAULT_MAP, "unread_count": get_unread_count(db, user)})

@app.post("/log-session/{booking_id}")
async def submit_session_log(request: Request, booking_id: int, duration: int = Form(...), weather: str = Form(...), road_type: str = Form(...), shared_feedback: str = Form(...), private_notes: str = Form(""), db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking or booking.instructor.user_id != user.id: raise HTTPException(status_code=403, detail="Not authorized")
    form_data = await request.form()
    obs_list, space_list, speed_list, steering_list, communication_list = [], [], [], [], []
    for key, value in form_data.items():
        if key.startswith("counts_"):
            code = key.split("_")[1]
            try: count = int(value)
            except: count = 0
            if count > 0:
                faults = [code] * count
                if code.startswith("A"): obs_list.extend(faults)
                elif code.startswith("B"): space_list.extend(faults)
                elif code.startswith("C"): speed_list.extend(faults)
                elif code.startswith("D"): steering_list.extend(faults)
                elif code.startswith("E"): communication_list.extend(faults)
    session = models.DrivingSession(booking_id=booking_id, duration_minutes=duration, weather_condition=weather, road_type=road_type, observation_data=",".join(obs_list), space_margin_data=",".join(space_list), speed_data=",".join(speed_list), steering_data=",".join(steering_list), communication_data=",".join(communication_list), shared_feedback=shared_feedback, instructor_private_notes=private_notes, created_at=datetime.now().strftime("%Y-%m-%d"))
    db.add(session)
    booking.status = "completed"
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.get("/progress", response_class=HTMLResponse)
async def student_progress(request: Request, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user or user.role != "student": return RedirectResponse(url="/login")
    
    # Fetch Consolidated Progress
    progress = db.query(models.StudentProgress).filter(models.StudentProgress.student_id == user.id).first()
    if not progress:
        progress = models.StudentProgress(student_id=user.id, last_updated=datetime.now().isoformat())
        db.add(progress)
        db.commit()
        db.refresh(progress)



    # Fetch Diagnostic Rides
    diagnostic_rides = db.query(models.DiagnosticRide).filter(
        models.DiagnosticRide.student_id == user.id
    ).order_by(models.DiagnosticRide.created_at.desc()).all()

    bookings = db.query(models.BookingRequest).filter(models.BookingRequest.student_id == user.id).order_by(models.BookingRequest.date.desc(), models.BookingRequest.time.desc()).all()
    sessions = db.query(models.DrivingSession).join(models.BookingRequest).filter(models.BookingRequest.student_id == user.id).all()
    total_hours = round(sum(s.duration_minutes for s in sessions) / 60, 1)
    total_spent = sum(b.total_amount for b in bookings if b.status == 'completed')
    total_lessons = len(sessions)
    category_map = {"A": "Observation", "B": "Space Margin", "C": "Speed", "D": "Steering", "E": "Communication"}
    grouped_counts = {name: {} for name in category_map.values()}
    for s in sessions:
        all_data = f"{s.observation_data},{s.space_margin_data},{s.speed_data},{s.steering_data},{s.communication_data}"
        for code in [c.strip() for c in all_data.split(',') if c.strip()]:
            if code[0].upper() in category_map:
                cat = category_map[code[0].upper()]
                grouped_counts[cat][code] = grouped_counts[cat].get(code, 0) + 1
    grouped_faults = {cat: sorted([(code, FAULT_MAP.get(code, "Unknown"), count) for code, count in counts.items()], key=lambda x: x[2], reverse=True) for cat, counts in grouped_counts.items() if counts}
    category_totals = {k: sum(v.values()) for k, v in grouped_counts.items()}
    
    return templates.TemplateResponse("student_progress.html", {
        "request": request, 
        "user": user, 
        "progress": progress,
        "diagnostic_rides": diagnostic_rides,
        "bookings": bookings, 
        "stats": {"total_lessons": total_lessons, "total_hours": total_hours, "total_spent": total_spent}, 
        "grouped_faults": grouped_faults, 
        "category_totals": category_totals, 
        "unread_count": get_unread_count(db, user)
    })

@app.get("/instructor-progress", response_class=HTMLResponse)
async def instructor_progress(request: Request, student_id: Optional[int] = None, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    students = db.query(models.User).join(models.BookingRequest, models.BookingRequest.student_id == models.User.id).filter(models.BookingRequest.instructor_id == user.instructor_profile.id).distinct().all()
    session_query = db.query(models.DrivingSession).join(models.BookingRequest).filter(models.BookingRequest.instructor_id == user.instructor_profile.id)
    if student_id: session_query = session_query.filter(models.BookingRequest.student_id == student_id)
    sessions = session_query.all()
    booking_query = db.query(models.BookingRequest).filter(models.BookingRequest.instructor_id == user.instructor_profile.id).order_by(models.BookingRequest.date.desc(), models.BookingRequest.time.desc())
    if student_id: booking_query = booking_query.filter(models.BookingRequest.student_id == student_id)
    bookings = booking_query.all()
    total_earnings = sum(b.duration * user.instructor_profile.hourly_rate for b in bookings if b.status == 'completed')
    category_map = {"A": "Observation", "B": "Space Margin", "C": "Speed", "D": "Steering", "E": "Communication"}
    grouped_counts = {name: {} for name in category_map.values()}
    for s in sessions:
        all_data = f"{s.observation_data},{s.space_margin_data},{s.speed_data},{s.steering_data},{s.communication_data}"
        for code in [c.strip() for c in all_data.split(',') if c.strip()]:
            if code[0].upper() in category_map:
                cat = category_map[code[0].upper()]
                grouped_counts[cat][code] = grouped_counts[cat].get(code, 0) + 1
    grouped_faults = {cat: sorted([(code, FAULT_MAP.get(code, "Unknown"), count) for code, count in counts.items()], key=lambda x: x[2], reverse=True) for cat, counts in grouped_counts.items() if counts}
    return templates.TemplateResponse("instructor_progress.html", {"request": request, "user": user, "bookings": bookings, "students": students, "selected_student_id": student_id, "total_earnings": total_earnings, "stats": {"total_lessons": len(sessions), "total_hours": round(sum(s.duration_minutes for s in sessions)/60, 1)}, "grouped_faults": grouped_faults, "unread_count": get_unread_count(db, user)})

@app.post("/cancel-booking/{booking_id}")
async def cancel_booking(request: Request, booking_id: int, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking or booking.student_id != user.id: raise HTTPException(status_code=403, detail="Not authorized")
    if booking.status == "completed": raise HTTPException(status_code=400, detail="Cannot cancel completed")
    # Check 24h rule
    try:
        booking_dt = datetime.strptime(f"{booking.date} {booking.time}", "%Y-%m-%d %H:%M")
        time_diff = booking_dt - datetime.now()
        
        if time_diff < timedelta(hours=24):
            # Late cancellation: 25% penalty towards instructor
            # Instructor gets 25% of total amount
            booking.instructor_payout = booking.total_amount * 0.25
            print(f"PENALTY: Student {user.email} cancelled booking {booking_id} within 24h. 25% fee applies.")
        else:
            # Early cancellation: Full refund, instructor gets 0
            booking.instructor_payout = 0.0
            
        booking.status = "cancelled"
        db.commit()
        
        # Notify Instructor
        create_notification(db, booking.instructor.user_id, "Booking Cancelled", f"Student {user.full_name} has cancelled their booking for {booking.date} at {booking.time}.")
        
    except ValueError:
        pass
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/request-cancellation/{booking_id}")
async def request_cancellation(request: Request, booking_id: int, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking or booking.instructor.user_id != user.id: raise HTTPException(status_code=403, detail="Not authorized")
    if booking.status == "accepted":
        booking.status = "cancellation_requested"
        db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/respond-cancellation/{booking_id}/{action}")
async def respond_cancellation(request: Request, booking_id: int, action: str, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking or booking.student_id != user.id: raise HTTPException(status_code=403, detail="Not authorized")
    if action == "approve": booking.status = "cancelled"
    elif action == "deny": booking.status = "accepted"
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.get("/messages", response_class=HTMLResponse)
async def view_messages(request: Request, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    from sqlalchemy import or_
    sent_ids = db.query(models.Message.recipient_id).filter(models.Message.sender_id == user.id)
    received_ids = db.query(models.Message.sender_id).filter(models.Message.recipient_id == user.id)
    contact_ids = [i[0] for i in sent_ids.union(received_ids).all()]
    contacts = db.query(models.User).filter(models.User.id.in_(contact_ids)).all()
    return templates.TemplateResponse("messages_list.html", {"request": request, "user": user, "contacts": contacts, "unread_count": get_unread_count(db, user)})

@app.get("/messages/{other_user_id}", response_class=HTMLResponse)
async def chat_view(request: Request, other_user_id: int, db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    other_user = db.query(models.User).filter(models.User.id == other_user_id).first()
    from sqlalchemy import or_, and_
    messages = db.query(models.Message).filter(or_(and_(models.Message.sender_id == user.id, models.Message.recipient_id == other_user_id), and_(models.Message.sender_id == other_user_id, models.Message.recipient_id == user.id))).order_by(models.Message.timestamp.asc()).all()
    for m in messages:
        if m.recipient_id == user.id and not m.is_read: m.is_read = True
    db.commit()
    return templates.TemplateResponse("chat.html", {"request": request, "user": user, "other_user": other_user, "messages": messages, "unread_count": get_unread_count(db, user)})

@app.post("/send-message")
async def send_message(request: Request, recipient_id: int = Form(...), content: str = Form(...), db: Session = Depends(get_db), user: models.User = Depends(get_current_user)):
    if not user: return RedirectResponse(url="/login")
    msg = models.Message(sender_id=user.id, recipient_id=recipient_id, content=content, timestamp=datetime.now().isoformat())
    db.add(msg)
    db.commit()
    return RedirectResponse(url=f"/messages/{recipient_id}", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/booking/{booking_id}/{action}")
async def handle_booking(
    request: Request,
    booking_id: int,
    action: str,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking or booking.instructor.user_id != user.id: raise HTTPException(status_code=403, detail="Not authorized")
    
    if action == "accept":
        # Check conflict
        start_dt = datetime.strptime(f"{booking.date} {booking.time}", "%Y-%m-%d %H:%M")
        end_dt = start_dt + timedelta(hours=booking.duration)
        concurrent = db.query(models.BookingRequest).filter(models.BookingRequest.instructor_id == booking.instructor_id, models.BookingRequest.status == "accepted", models.BookingRequest.date == booking.date, models.BookingRequest.id != booking.id).all()
        for other in concurrent:
            other_start = datetime.strptime(f"{other.date} {other.time}", "%Y-%m-%d %H:%M")
            other_end = other_start + timedelta(hours=other.duration)
            if start_dt < other_end and end_dt > other_start:
                return templates.TemplateResponse("base.html", {"request": request, "user": user, "content": "<div class='alert alert-danger'>Conflict!</div>"})
        
        booking.status = "accepted"
        if booking.student.student_profile and not booking.student.student_profile.is_verified:
            booking.student.student_profile.is_verified = True
            booking.student.student_profile.license_status = "verified"
        
        # Notify Student
        create_notification(db, booking.student_id, "Booking Confirmed", f"Your lesson with {booking.instructor.user.full_name} has been confirmed.")
            
    elif action == "reject":
        booking.status = "rejected"
        # Notify Student
        create_notification(db, booking.student_id, "Booking Declined", f"Your lesson with {booking.instructor.user.full_name} has been declined.")
        
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/log-diagnostic-ride/{ride_id}")
async def log_diagnostic_ride(
    ride_id: int,
    passed: str = Form(...),
    overall_score: float = Form(...),
    criteria_braking: Optional[bool] = Form(False),
    criteria_speed: Optional[bool] = Form(False),
    criteria_steering: Optional[bool] = Form(False),
    notes: str = Form(""),
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user or user.role != "instructor":
        return RedirectResponse(url="/login")
    
    ride = db.query(models.DiagnosticRide).filter(models.DiagnosticRide.id == ride_id).first()
    if not ride or ride.instructor_id != user.instructor_profile.id:
        raise HTTPException(status_code=403, detail="Not authorized")
    
    ride.passed = (passed == "true")
    ride.overall_score = overall_score
    ride.evaluator_notes = notes
    ride.status = "completed"
    ride.evaluated_at = datetime.now().isoformat()
    
    criteria = {
        "criteria_met": [],
        "criteria_failed": []
    }
    
    if criteria_braking: criteria["criteria_met"].append("Smooth Braking")
    else: criteria["criteria_failed"].append("Smooth Braking")
    
    if criteria_speed: criteria["criteria_met"].append("Speed Compliance")
    else: criteria["criteria_failed"].append("Speed Compliance")
    
    if criteria_steering: criteria["criteria_met"].append("Steering/Cornering")
    else: criteria["criteria_failed"].append("Steering/Cornering")
    
    ride.criteria_results = json.dumps(criteria)
    
    # Update student profile if passed
    if ride.passed:
        student_profile = db.query(models.StudentProfile).filter(models.StudentProfile.user_id == ride.student_id).first()
        if student_profile:
            student_profile.diagnostic_completed = True
            student_profile.diagnostic_ride_id = ride.id
            student_profile.basics_skipped = True
            
            # Unlock Advanced module
            advanced_module = db.query(models.LearningModule).filter(models.LearningModule.order == 2).first()
            if advanced_module:
                advanced_progress = db.query(models.StudentModuleProgress).filter(
                    models.StudentModuleProgress.student_id == ride.student_id,
                    models.StudentModuleProgress.module_id == advanced_module.id
                ).first()
                if advanced_progress:
                    advanced_progress.status = "unlocked"
                    advanced_progress.unlocked_at = datetime.now().isoformat()

    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.get("/notifications", response_class=HTMLResponse)
async def view_notifications(
    request: Request,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    notifications = db.query(models.Notification).filter(
        models.Notification.user_id == user.id
    ).order_by(models.Notification.id.desc()).all()
    
    # Mark all as read
    for n in notifications:
        n.is_read = True
    db.commit()
    
    return templates.TemplateResponse("notifications.html", {
        "request": request, 
        "user": user, 
        "notifications": notifications,
        "unread_count": get_unread_count(db, user)
    })

@app.get("/profile/edit", response_class=HTMLResponse)
async def edit_profile_page(
    request: Request,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    return templates.TemplateResponse("profile_edit.html", {
        "request": request,
        "user": user,
        "unread_count": get_unread_count(db, user)
    })

@app.post("/profile/edit", response_class=HTMLResponse)
async def update_profile(
    request: Request,
    full_name: str = Form(...),
    email: str = Form(...),
    phone_number: str = Form(...),
    password: Optional[str] = Form(None),
    # Optional fields depending on role
    age: Optional[int] = Form(None),
    l_license_number: Optional[str] = Form(None),
    bio: Optional[str] = Form(None),
    hourly_rate: Optional[float] = Form(None),
    city: Optional[str] = Form(None),
    car_model: Optional[str] = Form(None),
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    # 1. Update User Basic Info
    user.full_name = full_name
    user.email = email
    user.phone_number = phone_number
    
    if password and len(password.strip()) > 0:
        user.hashed_password = get_password_hash(password) # Using the helper defined in main.py
    
    # 2. Update Role Specific Info
    if user.role == "student" and user.student_profile:
        if age: user.student_profile.age = age
        if l_license_number: user.student_profile.l_license_number = l_license_number
        
    elif user.role == "instructor" and user.instructor_profile:
        if bio: user.instructor_profile.bio = bio
        if hourly_rate: user.instructor_profile.hourly_rate = hourly_rate
        if city: user.instructor_profile.city = city
        if car_model: user.instructor_profile.car_model = car_model
        
    db.commit()
    
    return templates.TemplateResponse("profile_edit.html", {
        "request": request,
        "user": user,
        "message": "Profile updated successfully!",
        "unread_count": get_unread_count(db, user)
    })
