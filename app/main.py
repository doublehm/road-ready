import sys
import os

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "lib"))

from fastapi import FastAPI, Depends, Request, Form, status, HTTPException, UploadFile, File
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from sqlalchemy.orm import Session
from typing import Optional
from datetime import datetime, timedelta
import json
import uuid
import shutil
import os
from passlib.context import CryptContext
import stripe

from . import models, schemas, database

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

app.mount("/static", StaticFiles(directory="app/static"), name="static")
templates = Jinja2Templates(directory="app/templates")

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
    
    # Map Chapters to estimated Page Numbers
    # (In a real app, these would be precise based on the PDF)
    chapter_map = {
        "1": 5,
        "2": 15,
        "3": 30,
        "4": 50,
        "5": 70,
        "6": 90,
        "7": 110,
        "8": 120
    }
    
    def replace_match(match):
        full_match = match.group(0) # e.g. (See Chapter 3: Signs...)
        chapter_num = match.group(1)
        
        page = chapter_map.get(chapter_num, 1)
        
        # Strip the parens for the link text
        link_text = full_match.strip("()")
        
        return f'(<a href="/education/bc?page={page}" target="_blank" class="text-decoration-none">{link_text} 🔗</a>)'

    # Regex to find "Chapter X"
    # Matches "(See Chapter X" or just "Chapter X"
    pattern = r"\(See Chapter (\d+).*?\)"
    return re.sub(pattern, replace_match, text)

templates.env.filters["expand_codes"] = expand_codes
templates.env.filters["linkify_chapter"] = linkify_chapter

# Dependency
def get_db():
    db = database.SessionLocal()
    try:
        yield db
    finally:
        db.close()

# --- Dummy Auth Logic (Cookies for simplicity in prototype) ---
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

@app.get("/education/quiz", response_class=HTMLResponse)
async def education_quiz(request: Request, user: models.User = Depends(get_current_user), db: Session = Depends(get_db)):
    # Fetch 50 random questions
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
    
    # Process each submitted answer
    for key, value in form_data.items():
        if key.startswith("q_"):
            q_id = int(key.split("_")[1])
            selected_option = value
            
            question = db.query(models.QuizQuestion).filter(models.QuizQuestion.id == q_id).first()
            if question:
                is_correct = (selected_option == question.correct_option)
                if is_correct:
                    score += 1
                
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
async def submit_contact(
    request: Request,
    name: str = Form(...),
    email: str = Form(...),
    subject: str = Form(...),
    message: str = Form(...)
):
    # In a real app, send email via SendGrid/SES here
    print(f"--- NEW CONTACT FORM SUBMISSION ---")
    print(f"From: {name} <{email}>")
    print(f"Subject: {subject}")
    print(f"Message: {message}")
    print(f"-----------------------------------")
    
    return templates.TemplateResponse("contact.html", {"request": request, "success": True})

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
        # Expire in 1 hour
        user.reset_token_expiry = (datetime.now() + timedelta(hours=1)).isoformat()
        db.commit()
        
        # Simulate sending email
        reset_link = f"{request.base_url}reset-password?token={token}"
        print(f"PASSWORD RESET LINK for {email}: {reset_link}") # Log to console
        
    return templates.TemplateResponse("forgot_password.html", {"request": request, "message": "If an account exists for this email, a reset link has been sent."})

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
        return templates.TemplateResponse("reset_password.html", {"request": request, "token": token, "error": "Invalid or expired token"})
        
    # Check expiry
    try:
        expiry = datetime.fromisoformat(user.reset_token_expiry)
        if datetime.now() > expiry:
            return templates.TemplateResponse("reset_password.html", {"request": request, "token": token, "error": "Token expired"})
    except (ValueError, TypeError):
        return templates.TemplateResponse("reset_password.html", {"request": request, "token": token, "error": "Invalid token expiry data"})

    # Update password
    user.hashed_password = get_password_hash(password)
    user.reset_token = None
    user.reset_token_expiry = None
    db.commit()
    
    return templates.TemplateResponse("login.html", {"request": request, "message": "Password reset successful. Please login."})

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
    if db.query(models.User).filter(models.User.email == email).first():
        return templates.TemplateResponse("register.html", {"request": request, "error": "Email already registered"})
    
    new_user = models.User(
        email=email,
        hashed_password=get_password_hash(password),
        full_name=full_name,
        phone_number=phone,
        role=role
    )
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    
    # Redirect to profile setup
    response = RedirectResponse(url="/setup-profile", status_code=status.HTTP_303_SEE_OTHER)
    response.set_cookie(key="user_id", value=str(new_user.id))
    return response

@app.get("/setup-profile", response_class=HTMLResponse)
async def setup_profile_page(request: Request, user: models.User = Depends(get_current_user), db: Session = Depends(get_db)):
    if not user:
        return RedirectResponse(url="/login")
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
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    # Save the file
    upload_dir = "app/static/uploads"
    filename = f"{user.id}_{license_image.filename}"
    file_path = os.path.join(upload_dir, filename)
    
    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(license_image.file, buffer)
    
    profile = models.InstructorProfile(
        user_id=user.id,
        bio=bio,
        hourly_rate=hourly_rate,
        city=city,
        car_model=car_model,
        insurance_policy=insurance_policy,
        certification_id=certification_id,
        license_image=filename,
        is_verified=False # Pending admin approval
    )
    db.add(profile)
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

# --- Admin Routes ---

@app.get("/admin", response_class=HTMLResponse)
async def admin_dashboard(
    request: Request,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    # Simple security check: In real app, check user.role == 'admin'
    # For prototype, we'll assume anyone who knows this URL is admin (or add a hardcoded check)
    if not user: return RedirectResponse(url="/login")
    
    # Fetch pending instructors
    pending_instructors = db.query(models.InstructorProfile).filter(models.InstructorProfile.is_verified == False).all()
    
    return templates.TemplateResponse("admin_dashboard.html", {
        "request": request,
        "user": user,
        "pending_instructors": pending_instructors
    })

@app.post("/admin/verify/{profile_id}")
async def admin_verify_instructor(
    request: Request,
    profile_id: int,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    profile = db.query(models.InstructorProfile).filter(models.InstructorProfile.id == profile_id).first()
    if profile:
        profile.is_verified = True
        db.commit()
        
    return RedirectResponse(url="/admin", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/setup-student")
async def setup_student(
    request: Request,
    age: int = Form(...),
    l_license_number: str = Form(...),
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    if age < 16:
         return templates.TemplateResponse("setup_student.html", {"request": request, "user": user, "error": "You must be at least 16 years old."})

    profile = models.StudentProfile(
        user_id=user.id,
        age=age,
        l_license_number=l_license_number
    )
    db.add(profile)
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.get("/dashboard", response_class=HTMLResponse)
async def dashboard(
    request: Request, 
    user: models.User = Depends(get_current_user),
    db: Session = Depends(get_db),
    city: Optional[str] = None,
    max_price: Optional[float] = None
):
    if not user:
        return RedirectResponse(url="/login")
    
    if user.role == "student":
        # Fetch matching instructors
        # ONLY show Verified instructors
        query = db.query(models.InstructorProfile).join(models.User).filter(
            models.InstructorProfile.is_available == True,
            models.InstructorProfile.is_verified == True
        )
        
        if city:
            query = query.filter(models.InstructorProfile.city.ilike(f"%{city}%"))
        
        if max_price:
            query = query.filter(models.InstructorProfile.hourly_rate <= max_price)
            
        instructors = query.all()
        
        # Calculate ratings for display
        for inst in instructors:
            reviews = inst.reviews
            if reviews:
                avg = sum(r.rating for r in reviews) / len(reviews)
                inst.average_rating = round(avg, 1)
                inst.review_count = len(reviews)
            else:
                inst.average_rating = None
                inst.review_count = 0

        # Fetch my booking requests (All history)
        all_bookings = db.query(models.BookingRequest).filter(models.BookingRequest.student_id == user.id)\
            .order_by(models.BookingRequest.date.desc(), models.BookingRequest.time.desc()).all()
        
        # Filter Active Bookings (Pending, Accepted, Cancellation Requested)
        active_bookings = [b for b in all_bookings if b.status in ['pending', 'accepted', 'cancellation_requested']]
        
        return templates.TemplateResponse("student_dashboard.html", {
            "request": request, 
            "user": user, 
            "instructors": instructors,
            "active_bookings": active_bookings,
            "filters": {"city": city, "max_price": max_price},
            "unread_count": get_unread_count(db, user)
        })
    
    else: # Instructor
        # Fetch all bookings
        all_bookings = db.query(models.BookingRequest).filter(
            models.BookingRequest.instructor_id == user.instructor_profile.id
        ).order_by(models.BookingRequest.date.desc(), models.BookingRequest.time.desc()).all()
        
        # Split bookings
        incoming_requests = [b for b in all_bookings if b.status == 'pending']
        active_appointments = [b for b in all_bookings if b.status in ['accepted', 'cancellation_requested']]
        
        # Calculate my own rating
        my_reviews = user.instructor_profile.reviews
        if my_reviews:
            avg_rating = sum(r.rating for r in my_reviews) / len(my_reviews)
            avg_rating = round(avg_rating, 1)
        else:
            avg_rating = "New"

        # Prepare Calendar Events (Accepted Bookings Only)
        calendar_events = []
        accepted_bookings = [b for b in all_bookings if b.status == 'accepted']
        
        for b in accepted_bookings:
            try:
                start_dt = datetime.strptime(f"{b.date} {b.time}", "%Y-%m-%d %H:%M")
                end_dt = start_dt + timedelta(hours=b.duration)
                calendar_events.append({
                    "title": f"Lesson with {b.student.full_name}",
                    "start": start_dt.isoformat(),
                    "end": end_dt.isoformat(),
                    "color": "#198754" # Bootstrap success green
                })
            except ValueError:
                pass # Skip invalid dates

        return templates.TemplateResponse("instructor_dashboard.html", {
            "request": request, 
            "user": user,
            "incoming_requests": incoming_requests,
            "active_appointments": active_appointments,
            "bookings": all_bookings, # Keeping strictly for backwards compat if needed, though template will change
            "avg_rating": avg_rating,
            "review_count": len(my_reviews),
            "calendar_events": json.dumps(calendar_events),
            "unread_count": get_unread_count(db, user)
        })

@app.get("/review/{instructor_id}", response_class=HTMLResponse)
async def review_form(
    request: Request, 
    instructor_id: int, 
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    instructor = db.query(models.InstructorProfile).filter(models.InstructorProfile.id == instructor_id).first()
    return templates.TemplateResponse("review_form.html", {
        "request": request,
        "user": user,
        "instructor": instructor,
        "unread_count": get_unread_count(db, user)
    })

@app.post("/review/{instructor_id}")
async def submit_review(
    request: Request,
    instructor_id: int,
    rating: int = Form(...),
    comment: str = Form(...),
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    from datetime import datetime
    
    review = models.Review(
        instructor_id=instructor_id,
        student_id=user.id,
        rating=rating,
        comment=comment,
        created_at=datetime.now().strftime("%Y-%m-%d")
    )
    db.add(review)
    db.commit()
    
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/toggle-availability")
async def toggle_availability(
    request: Request, 
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if user and user.instructor_profile:
        user.instructor_profile.is_available = not user.instructor_profile.is_available
        db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/availability/add")
async def add_availability(
    request: Request,
    day_of_week: int = Form(...),
    start_time: str = Form(...),
    end_time: str = Form(...),
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    
    # Simple validation
    if start_time >= end_time:
        # In a real app, we'd flash an error
        return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

    availability = models.InstructorAvailability(
        instructor_id=user.instructor_profile.id,
        day_of_week=day_of_week,
        start_time=start_time,
        end_time=end_time
    )
    db.add(availability)
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/availability/delete/{availability_id}")
async def delete_availability(
    request: Request,
    availability_id: int,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    
    avail = db.query(models.InstructorAvailability).filter(
        models.InstructorAvailability.id == availability_id,
        models.InstructorAvailability.instructor_id == user.instructor_profile.id
    ).first()
    
    if avail:
        db.delete(avail)
        db.commit()
        
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.get("/book/{instructor_id}", response_class=HTMLResponse)
async def booking_form(
    request: Request, 
    instructor_id: int, 
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    instructor = db.query(models.InstructorProfile).filter(models.InstructorProfile.id == instructor_id).first()
    if not instructor:
        raise HTTPException(status_code=404, detail="Instructor not found")

    # Check for existing bookings with this instructor
    existing_bookings = db.query(models.BookingRequest).filter(
        models.BookingRequest.student_id == user.id,
        models.BookingRequest.instructor_id == instructor_id,
        models.BookingRequest.status.in_(["pending", "accepted"])
    ).all()
        
    return templates.TemplateResponse("booking_form.html", {
        "request": request, 
        "user": user, 
        "instructor": instructor,
        "existing_bookings": existing_bookings,
        "unread_count": get_unread_count(db, user)
    })
            
@app.post("/book/{instructor_id}")
async def submit_booking(
    request: Request,
    instructor_id: int,
    date: str = Form(...),
    time: str = Form(...),
    duration: int = Form(...),
    pickup_address: str = Form(...),
    notes: str = Form(""),
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    # Validation for booking date and time
    try:
        new_start = datetime.strptime(f"{date} {time}", "%Y-%m-%d %H:%M")
        current_time = datetime.now()
        
        instructor = db.query(models.InstructorProfile).filter(models.InstructorProfile.id == instructor_id).first()
        existing_bookings = db.query(models.BookingRequest).filter(
            models.BookingRequest.student_id == user.id,
            models.BookingRequest.instructor_id == instructor_id,
            models.BookingRequest.status.in_(["pending", "accepted"])
        ).all()

        if new_start < current_time:
            return templates.TemplateResponse("booking_form.html", {
                "request": request,
                "user": user,
                "instructor": instructor,
                "existing_bookings": existing_bookings,
                "error_message": "Booking date and time cannot be in the past.",
                "unread_count": get_unread_count(db, user)
            })
            
        if new_start.date() == current_time.date() and new_start < current_time + timedelta(hours=2):
            return templates.TemplateResponse("booking_form.html", {
                "request": request,
                "user": user,
                "instructor": instructor,
                "existing_bookings": existing_bookings,
                "error_message": "Same-day bookings must be at least 2 hours in advance.",
                "unread_count": get_unread_count(db, user)
            })

    except ValueError:
        # Handle parsing errors for date/time if necessary
        instructor = db.query(models.InstructorProfile).filter(models.InstructorProfile.id == instructor_id).first()
        existing_bookings = db.query(models.BookingRequest).filter(
            models.BookingRequest.student_id == user.id,
            models.BookingRequest.instructor_id == instructor_id,
            models.BookingRequest.status.in_(["pending", "accepted"])
        ).all()
        return templates.TemplateResponse("booking_form.html", {
            "request": request,
            "user": user,
            "instructor": instructor,
            "existing_bookings": existing_bookings,
            "error_message": "Invalid date or time format.",
            "unread_count": get_unread_count(db, user)
        })
    
    # Conflict Check Logic
    try:
        new_start = datetime.strptime(f"{date} {time}", "%Y-%m-%d %H:%M")
        new_end = new_start + timedelta(hours=duration)
        
        # 1. Availability Check
        instructor = db.query(models.InstructorProfile).filter(models.InstructorProfile.id == instructor_id).first()
        availabilities = instructor.availabilities
        
        if availabilities:
            day_of_week = new_start.weekday() # 0=Monday
            is_valid_slot = False
            
            for slot in availabilities:
                if slot.day_of_week == day_of_week:
                    slot_start = datetime.strptime(f"{date} {slot.start_time}", "%Y-%m-%d %H:%M")
                    slot_end = datetime.strptime(f"{date} {slot.end_time}", "%Y-%m-%d %H:%M")
                    
                    # Check if booking is fully within the slot
                    if new_start >= slot_start and new_end <= slot_end:
                        is_valid_slot = True
                        break
            
            if not is_valid_slot:
                existing_bookings = db.query(models.BookingRequest).filter(
                    models.BookingRequest.student_id == user.id,
                    models.BookingRequest.instructor_id == instructor_id,
                    models.BookingRequest.status.in_(["pending", "accepted"])
                ).all()
                return templates.TemplateResponse("booking_form.html", {
                    "request": request,
                    "user": user,
                    "instructor": instructor,
                    "existing_bookings": existing_bookings,
                    "error_message": "The instructor is not available at this time. Please check their working hours.",
                    "unread_count": get_unread_count(db, user)
                })

        # 2. Check against existing ACCEPTED bookings for this instructor
        existing_accepted = db.query(models.BookingRequest).filter(
            models.BookingRequest.instructor_id == instructor_id,
            models.BookingRequest.status == "accepted",
            models.BookingRequest.date == date
        ).all()
        
        for booking in existing_accepted:
            b_start = datetime.strptime(f"{booking.date} {booking.time}", "%Y-%m-%d %H:%M")
            b_end = b_start + timedelta(hours=booking.duration)
            
            # Check for overlap: (StartA < EndB) and (EndA > StartB)
            if new_start < b_end and new_end > b_start:
                 # Fetch instructor and existing bookings again for re-rendering the form
                instructor = db.query(models.InstructorProfile).filter(models.InstructorProfile.id == instructor_id).first()
                existing_bookings = db.query(models.BookingRequest).filter(
                    models.BookingRequest.student_id == user.id,
                    models.BookingRequest.instructor_id == instructor_id,
                    models.BookingRequest.status.in_(["pending", "accepted"])
                ).all()
                
                return templates.TemplateResponse("booking_form.html", {
                    "request": request,
                    "user": user,
                    "instructor": instructor,
                    "existing_bookings": existing_bookings,
                    "error_message": "The instructor is not available at the selected time. Please choose a different slot.",
                    "unread_count": get_unread_count(db, user)
                })

    except ValueError:
        pass # Handle parsing errors if necessary

    # Calculate Fees (15% Commission)
    instructor = db.query(models.InstructorProfile).filter(models.InstructorProfile.id == instructor_id).first()
    total = instructor.hourly_rate * duration
    fee = total * 0.15
    payout = total - fee

    booking = models.BookingRequest(
        student_id=user.id,
        instructor_id=instructor_id,
        date=date,
        time=time,
        duration=duration,
        pickup_address=pickup_address,
        notes=notes,
        total_amount=total,
        platform_fee=fee,
        instructor_payout=payout,
        status="pending_payment" # Wait for payment
    )
    db.add(booking)
    db.commit()
    db.refresh(booking)
    
    return RedirectResponse(url=f"/checkout/{booking.id}", status_code=status.HTTP_303_SEE_OTHER)

@app.get("/checkout/{booking_id}", response_class=HTMLResponse)
async def checkout_page(
    request: Request,
    booking_id: int,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking or booking.student_id != user.id:
        raise HTTPException(status_code=403, detail="Not authorized")
        
    return templates.TemplateResponse("checkout.html", {
        "request": request,
        "user": user,
        "booking": booking,
        "stripe_key": "pk_test_MOCK_KEY_FOR_PROTOTYPE" # In real app, from env var
    })

@app.post("/checkout/{booking_id}/process")
async def process_payment(
    request: Request,
    booking_id: int,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    
    # Check if Stripe is configured
    if stripe.api_key and "sk_test" in stripe.api_key:
        try:
            checkout_session = stripe.checkout.Session.create(
                line_items=[{
                    'price_data': {
                        'currency': 'cad',
                        'product_data': {
                            'name': f'Driving Lesson with {booking.instructor.user.full_name}',
                        },
                        'unit_amount': int(booking.total_amount * 100), # Cents
                    },
                    'quantity': 1,
                }],
                mode='payment',
                success_url=f'{str(request.base_url)}payment/success?booking_id={booking.id}&session_id={{CHECKOUT_SESSION_ID}}',
                cancel_url=f'{str(request.base_url)}checkout/{booking.id}',
                metadata={
                    "booking_id": booking.id,
                    "student_id": user.id
                }
            )
            return RedirectResponse(url=checkout_session.url, status_code=status.HTTP_303_SEE_OTHER)
            
        except Exception as e:
            print(f"Stripe Error: {e}")
            return templates.TemplateResponse("checkout.html", {
                "request": request,
                "user": user,
                "booking": booking,
                "error": "Payment service unavailable. Please try again."
            })

    # Fallback: Mock Processing (if no API key)
    import time
    # time.sleep(1) 
    
    booking.payment_status = "paid"
    booking.status = "pending" 
    booking.stripe_payment_intent_id = f"pi_mock_{uuid.uuid4()}"
    
    db.commit()
    
    return templates.TemplateResponse("booking_success.html", {
        "request": request,
        "user": user,
        "unread_count": get_unread_count(db, user)
    })

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
    
    # Ensure this booking belongs to the logged-in instructor
    if not booking or booking.instructor.user_id != user.id:
        raise HTTPException(status_code=403, detail="Not authorized")
        
    if action == "accept":
        # Conflict Check: Ensure no other ACCEPTED booking overlaps
        start_dt = datetime.strptime(f"{booking.date} {booking.time}", "%Y-%m-%d %H:%M")
        end_dt = start_dt + timedelta(hours=booking.duration)
        
        # Find other accepted bookings for this instructor on the same day
        concurrent_bookings = db.query(models.BookingRequest).filter(
            models.BookingRequest.instructor_id == booking.instructor_id,
            models.BookingRequest.status == "accepted",
            models.BookingRequest.date == booking.date,
            models.BookingRequest.id != booking.id
        ).all()
        
        for other in concurrent_bookings:
            other_start = datetime.strptime(f"{other.date} {other.time}", "%Y-%m-%d %H:%M")
            other_end = other_start + timedelta(hours=other.duration)
            
            # Overlap formula: (StartA < EndB) and (EndA > StartB)
            if start_dt < other_end and end_dt > other_start:
                # Conflict found!
                # Render dashboard with an error message (or simple error page for now)
                # For a cleaner UI, we can redirect with an error param, but let's just return a template
                # re-fetching dashboard data is complex here, so let's do a simple error page
                return templates.TemplateResponse("base.html", {
                    "request": request, 
                    "user": user,
                    "content": f"""
                        <div class='alert alert-danger'>
                            <h4>Conflict Detected!</h4>
                            <p>You cannot accept this booking because it overlaps with another accepted session with <strong>{other.student.full_name}</strong> at {other.time}.</p>
                            <a href='/dashboard' class='btn btn-primary'>Back to Dashboard</a>
                        </div>
                    """
                })

        booking.status = "accepted"
    elif action == "reject":
        booking.status = "rejected"
        
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
    if not booking or booking.instructor.user_id != user.id:
        raise HTTPException(status_code=403, detail="Not authorized")
        
    booking.pickup_lat = pickup_lat
    booking.pickup_lng = pickup_lng
    booking.dropoff_lat = dropoff_lat
    booking.dropoff_lng = dropoff_lng
    
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.get("/log-session/{booking_id}", response_class=HTMLResponse)
async def log_session_form(
    request: Request, 
    booking_id: int, 
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking or booking.instructor.user_id != user.id:
        raise HTTPException(status_code=403, detail="Not authorized")
    
    if booking.status == "completed":
        raise HTTPException(status_code=400, detail="Session already completed. Cannot log again.")
        
    return templates.TemplateResponse("log_session_form.html", {
        "request": request,
        "user": user,
        "booking": booking,
        "fault_map": FAULT_MAP,
        "unread_count": get_unread_count(db, user)
    })

@app.post("/log-session/{booking_id}")
async def submit_session_log(
    request: Request,
    booking_id: int,
    duration: int = Form(...),
    weather: str = Form(...),
    road_type: str = Form(...),
    shared_feedback: str = Form(...),
    private_notes: str = Form(""),
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking or booking.instructor.user_id != user.id:
        raise HTTPException(status_code=403, detail="Not authorized")
        
    if booking.status == "completed":
        raise HTTPException(status_code=400, detail="Session already completed. Cannot log again.")
    
    # Process dynamic fault counts
    form_data = await request.form()
    
    obs_list = []
    space_list = []
    speed_list = []
    steering_list = []
    communication_list = []
    
    for key, value in form_data.items():
        if key.startswith("counts_"):
            code = key.split("_")[1] # Extract code e.g. "A1"
            try:
                count = int(value)
            except ValueError:
                count = 0
            
            if count > 0:
                # Add code 'count' times to the list
                faults = [code] * count
                
                if code.startswith("A"):
                    obs_list.extend(faults)
                elif code.startswith("B"):
                    space_list.extend(faults)
                elif code.startswith("C"):
                    speed_list.extend(faults)
                elif code.startswith("D"):
                    steering_list.extend(faults)
                elif code.startswith("E"):
                    communication_list.extend(faults)

    session = models.DrivingSession(
        booking_id=booking_id,
        duration_minutes=duration,
        weather_condition=weather,
        road_type=road_type,
        observation_data=",".join(obs_list),
        space_margin_data=",".join(space_list),
        speed_data=",".join(speed_list),
        steering_data=",".join(steering_list),
        communication_data=",".join(communication_list),
        shared_feedback=shared_feedback,
        instructor_private_notes=private_notes,
        created_at=datetime.now().strftime("%Y-%m-%d")
    )
    
    db.add(session)
    
    # Mark booking as completed
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if booking:
        booking.status = "completed"
        
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.get("/progress", response_class=HTMLResponse)
async def student_progress(
    request: Request,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user or user.role != "student": return RedirectResponse(url="/login")
    
    # Fetch all bookings for history (Merged view)
    bookings = db.query(models.BookingRequest).filter(models.BookingRequest.student_id == user.id)\
        .order_by(models.BookingRequest.date.desc(), models.BookingRequest.time.desc()).all()

    # Fetch sessions for stats (Keep existing logic or derive from bookings)
    # Existing logic joins BookingRequest. Let's keep it to ensure we only count legitimate sessions.
    sessions = db.query(models.DrivingSession).join(models.BookingRequest).filter(
        models.BookingRequest.student_id == user.id
    ).all()
    
    # Calculate Stats
    total_lessons = len(sessions)
    total_minutes = sum(s.duration_minutes for s in sessions)
    total_hours = round(total_minutes / 60, 1)
    
    # Calculate Costs & Total Spent
    total_spent = 0.0
    for b in bookings:
        rate = b.instructor.hourly_rate
        b.cost = b.duration * rate
        if b.status == 'completed':
            total_spent += b.cost
            
    total_spent = round(total_spent, 2)
    
    # Aggregate Faults/Criteria Data
    category_map = {
        "A": "Observation",
        "B": "Space Margin",
        "C": "Speed",
        "D": "Steering",
        "E": "Communication"
    }
    
    grouped_counts = {name: {} for name in category_map.values()}
    
    for s in sessions:
        all_data = f"{s.observation_data},{s.space_margin_data},{s.speed_data},{s.steering_data},{s.communication_data}"
        codes = [c.strip() for c in all_data.split(',') if c.strip()]
        
        for code in codes:
            prefix = code[0].upper()
            if prefix in category_map:
                cat_name = category_map[prefix]
                grouped_counts[cat_name][code] = grouped_counts[cat_name].get(code, 0) + 1

    grouped_faults = {}
    for cat_name, counts in grouped_counts.items():
        sorted_items = sorted(counts.items(), key=lambda x: x[1], reverse=True)
        formatted_list = []
        for code, count in sorted_items:
            desc = FAULT_MAP.get(code, "Unknown")
            formatted_list.append((code, desc, count))
        if formatted_list:
            grouped_faults[cat_name] = formatted_list

    # Prepare Chart Data
    category_totals = {k: sum(v.values()) for k, v in grouped_counts.items()}
    
    return templates.TemplateResponse("student_progress.html", {
        "request": request,
        "user": user,
        "bookings": bookings, # Full history
        "stats": {
            "total_lessons": total_lessons,
            "total_hours": total_hours,
            "total_spent": total_spent
        },
        "grouped_faults": grouped_faults,
        "category_totals": category_totals,
        "unread_count": get_unread_count(db, user)
    })

@app.get("/instructor-progress", response_class=HTMLResponse)
async def instructor_progress(
    request: Request,
    student_id: Optional[int] = None,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    
    # 1. Fetch Students (for the filter list)
    students = db.query(models.User).join(models.BookingRequest, models.BookingRequest.student_id == models.User.id)\
        .filter(models.BookingRequest.instructor_id == user.instructor_profile.id)\
        .distinct().all()

    # 2. Fetch Sessions (for Stats & Faults)
    session_query = db.query(models.DrivingSession).join(models.BookingRequest).filter(
        models.BookingRequest.instructor_id == user.instructor_profile.id
    )
    if student_id:
        session_query = session_query.filter(models.BookingRequest.student_id == student_id)
    sessions = session_query.all()

    # 3. Fetch Bookings (for History List)
    booking_query = db.query(models.BookingRequest).filter(
        models.BookingRequest.instructor_id == user.instructor_profile.id
    ).order_by(models.BookingRequest.date.desc(), models.BookingRequest.time.desc())
    
    if student_id:
        booking_query = booking_query.filter(models.BookingRequest.student_id == student_id)
    bookings = booking_query.all()
    
    # 4. Calculate Earnings
    hourly_rate = user.instructor_profile.hourly_rate
    total_earnings = 0.0
    
    for b in bookings:
        if b.status == 'completed':
            b.earnings = round(b.duration * hourly_rate, 2)
            total_earnings += b.earnings
        else:
            b.earnings = 0.0
            
    total_earnings = round(total_earnings, 2)
    
    # 5. Calculate Stats
    total_lessons = len(sessions)
    total_minutes = sum(s.duration_minutes for s in sessions)
    total_hours = round(total_minutes / 60, 1)
    
    # 6. Aggregate Faults by Category
    category_map = {
        "A": "Observation",
        "B": "Space Margin",
        "C": "Speed",
        "D": "Steering",
        "E": "Communication"
    }
    
    grouped_counts = {name: {} for name in category_map.values()}
    
    for s in sessions:
        all_data = f"{s.observation_data},{s.space_margin_data},{s.speed_data},{s.steering_data},{s.communication_data}"
        codes = [c.strip() for c in all_data.split(',') if c.strip()]
        
        for code in codes:
            prefix = code[0].upper()
            if prefix in category_map:
                cat_name = category_map[prefix]
                grouped_counts[cat_name][code] = grouped_counts[cat_name].get(code, 0) + 1

    grouped_faults = {}
    for cat_name, counts in grouped_counts.items():
        sorted_items = sorted(counts.items(), key=lambda x: x[1], reverse=True)
        formatted_list = []
        for code, count in sorted_items:
            desc = FAULT_MAP.get(code, "Unknown")
            formatted_list.append((code, desc, count))
        if formatted_list:
            grouped_faults[cat_name] = formatted_list
    
    return templates.TemplateResponse("instructor_progress.html", {
        "request": request,
        "user": user,
        "bookings": bookings,
        "students": students,
        "selected_student_id": student_id,
        "total_earnings": total_earnings,
        "stats": {
            "total_lessons": total_lessons,
            "total_hours": total_hours
        },
        "grouped_faults": grouped_faults,
        "unread_count": get_unread_count(db, user)
    })

# --- Cancellation Routes ---

@app.post("/cancel-booking/{booking_id}")
async def cancel_booking(
    request: Request,
    booking_id: int,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking or booking.student_id != user.id:
        raise HTTPException(status_code=403, detail="Not authorized")
        
    if booking.status == "completed":
        raise HTTPException(status_code=400, detail="Cannot cancel a completed session.")
        
    # Check 24h rule
    try:
        booking_dt = datetime.strptime(f"{booking.date} {booking.time}", "%Y-%m-%d %H:%M")
        time_diff = booking_dt - datetime.now()
        
        # Simple penalty logic (in real app, charge user)
        if time_diff < timedelta(hours=24):
            print(f"PENALTY: Student {user.email} cancelled booking {booking_id} within 24h. 25% fee applies.")
            
        booking.status = "cancelled"
        db.commit()
        
    except ValueError:
        pass 
        
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/request-cancellation/{booking_id}")
async def request_cancellation(
    request: Request,
    booking_id: int,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user or user.role != "instructor": return RedirectResponse(url="/login")
    
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking or booking.instructor.user_id != user.id:
        raise HTTPException(status_code=403, detail="Not authorized")
    
    if booking.status == "completed":
        raise HTTPException(status_code=400, detail="Cannot request cancellation for a completed session.")
        
    if booking.status == "accepted":
        booking.status = "cancellation_requested"
        db.commit()
        
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

@app.post("/respond-cancellation/{booking_id}/{action}")
async def respond_cancellation(
    request: Request,
    booking_id: int,
    action: str,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login") # Student responding
    
    booking = db.query(models.BookingRequest).filter(models.BookingRequest.id == booking_id).first()
    if not booking or booking.student_id != user.id:
        raise HTTPException(status_code=403, detail="Not authorized")
        
    if action == "approve":
        booking.status = "cancelled"
    elif action == "deny":
        booking.status = "accepted"
        
    db.commit()
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)

# --- Messaging Routes ---

@app.get("/messages", response_class=HTMLResponse)
async def view_messages(
    request: Request,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    # Find all unique users communicated with
    # (Sent to OR Received from)
    from sqlalchemy import or_
    
    # IDs of people I sent to
    sent_ids = db.query(models.Message.recipient_id).filter(models.Message.sender_id == user.id)
    # IDs of people I received from
    received_ids = db.query(models.Message.sender_id).filter(models.Message.recipient_id == user.id)
    
    contact_ids = sent_ids.union(received_ids).all()
    contact_ids = [i[0] for i in contact_ids]
    
    contacts = db.query(models.User).filter(models.User.id.in_(contact_ids)).all()
    
    return templates.TemplateResponse("messages_list.html", {
        "request": request,
        "user": user,
        "contacts": contacts,
        "unread_count": get_unread_count(db, user)
    })

@app.get("/messages/{other_user_id}", response_class=HTMLResponse)
async def chat_view(
    request: Request,
    other_user_id: int,
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    other_user = db.query(models.User).filter(models.User.id == other_user_id).first()
    if not other_user:
        raise HTTPException(status_code=404, detail="User not found")
        
    # Fetch conversation
    from sqlalchemy import or_, and_
    messages = db.query(models.Message).filter(
        or_(
            and_(models.Message.sender_id == user.id, models.Message.recipient_id == other_user_id),
            and_(models.Message.sender_id == other_user_id, models.Message.recipient_id == user.id)
        )
    ).order_by(models.Message.timestamp.asc()).all()
    
    # Mark as read
    for m in messages:
        if m.recipient_id == user.id and not m.is_read:
            m.is_read = True
    db.commit()
    
    return templates.TemplateResponse("chat.html", {
        "request": request,
        "user": user,
        "other_user": other_user,
        "messages": messages,
        "unread_count": get_unread_count(db, user)
    })

@app.post("/send-message")
async def send_message(
    request: Request,
    recipient_id: int = Form(...),
    content: str = Form(...),
    db: Session = Depends(get_db),
    user: models.User = Depends(get_current_user)
):
    if not user: return RedirectResponse(url="/login")
    
    msg = models.Message(
        sender_id=user.id,
        recipient_id=recipient_id,
        content=content,
        timestamp=datetime.now().isoformat()
    )
    db.add(msg)
    db.commit()
    
    return RedirectResponse(url=f"/messages/{recipient_id}", status_code=status.HTTP_303_SEE_OTHER)
