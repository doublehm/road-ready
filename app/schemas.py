from pydantic import BaseModel, EmailStr, validator
from typing import Optional, List
import re

# --- Shared Schemas ---
class UserBase(BaseModel):
    email: EmailStr
    full_name: str
    phone_number: str

    @validator('phone_number')
    def validate_phone(cls, v):
        # Remove common delimiters
        clean_number = re.sub(r'[\s\-\(\)\.]', '', v)
        if not re.match(r'^\+?1?\d{10,15}$', clean_number):
            raise ValueError('Phone number must be valid (10-15 digits)')
        return clean_number

class UserCreate(UserBase):
    password: str
    role: str  # "student" or "instructor"

    @validator('password')
    def validate_password(cls, v):
        if len(v) < 8:
            raise ValueError('Password must be at least 8 characters long')
        if not re.search(r'[A-Z]', v):
            raise ValueError('Password must contain at least one uppercase letter')
        if not re.search(r'[a-z]', v):
            raise ValueError('Password must contain at least one lowercase letter')
        if not re.search(r'\d', v):
            raise ValueError('Password must contain at least one number')
        if not re.search(r'[@$!%*?&]', v):
             raise ValueError('Password must contain at least one special character (@$!%*?&)')
        return v

class User(UserBase):
    id: int
    role: str
    
    class Config:
        orm_mode = True  # v1 uses orm_mode, not from_attributes

# --- Instructor Schemas ---
class InstructorAvailabilityBase(BaseModel):
    day_of_week: int
    start_time: str
    end_time: str

class InstructorAvailabilityCreate(InstructorAvailabilityBase):
    pass

class InstructorAvailability(InstructorAvailabilityBase):
    id: int
    instructor_id: int
    
    class Config:
        orm_mode = True

class InstructorProfileBase(BaseModel):
    bio: str
    hourly_rate: float
    city: str
    car_model: str
    insurance_policy: str
    certification_id: str
    is_available: bool = True

    @validator('insurance_policy')
    def validate_insurance(cls, v):
        if len(v) < 5:
             raise ValueError('Insurance policy number seems too short')
        if not re.match(r'^[A-Za-z0-9\-]+$', v):
             raise ValueError('Insurance policy must be alphanumeric')
        return v

    @validator('certification_id')
    def validate_cert(cls, v):
        if len(v) < 5:
             raise ValueError('Certification ID seems too short')
        return v

class InstructorProfileCreate(InstructorProfileBase):
    pass

class InstructorProfile(InstructorProfileBase):
    id: int
    user_id: int
    is_verified: bool
    user: Optional[User] = None
    availabilities: List[InstructorAvailability] = []

    class Config:
        orm_mode = True

# --- Student Schemas ---
class StudentProfileBase(BaseModel):
    age: int
    l_license_number: str

    @validator('l_license_number')
    def validate_license(cls, v):
        # BC Driver's License is typically 7 digits
        if not re.match(r'^\d{7}$', v):
            raise ValueError('BC Driver License must be exactly 7 digits')
        return v

class StudentProfileCreate(StudentProfileBase):
    pass

class StudentProfile(StudentProfileBase):
    id: int
    user_id: int
    license_image: Optional[str] = None
    is_verified: bool
    user: Optional[User] = None

    class Config:
        orm_mode = True

# --- Search Schema ---
class SearchFilter(BaseModel):
    city: Optional[str] = None
    max_rate: Optional[float] = None

# --- Auth Schemas ---
class Token(BaseModel):
    access_token: str
    token_type: str
    role: str
    user_id: int

class TokenData(BaseModel):
    email: Optional[str] = None

# --- Drive Log Schemas ---
class DriveLogBase(BaseModel):
    start_time: str
    end_time: str
    duration_minutes: float
    distance_km: float
    route_coords: str
    notes: Optional[str] = None

class DriveLogCreate(DriveLogBase):
    pass

class DriveLog(DriveLogBase):
    id: int
    user_id: int
    
    class Config:
        orm_mode = True

# --- Booking Schemas ---
class BookingRequestBase(BaseModel):
    instructor_id: int
    date: str
    time: str
    duration: int
    pickup_address: str
    dropoff_address: Optional[str] = None
    notes: Optional[str] = None

class BookingRequestCreate(BookingRequestBase):
    pass

class BookingRequest(BookingRequestBase):
    id: int
    student_id: int
    status: str
    total_amount: float
    instructor: Optional[InstructorProfile] = None
    
    class Config:
        orm_mode = True

# --- Session Schemas ---
class DrivingSessionBase(BaseModel):
    booking_id: int
    duration_minutes: int
    weather_condition: str
    road_type: str
    observation_data: str # JSON list of codes
    space_margin_data: str
    speed_data: str
    steering_data: str
    communication_data: str
    shared_feedback: str
    instructor_private_notes: Optional[str] = None

class DrivingSessionCreate(DrivingSessionBase):
    pass

class DrivingSession(DrivingSessionBase):
    id: int
    created_at: str
    
    class Config:
        orm_mode = True

# --- Quiz Schemas ---
class QuizQuestionBase(BaseModel):
    question_text: str
    option_a: str
    option_b: str
    option_c: str
    option_d: str
    correct_option: str
    explanation: Optional[str] = None

class QuizQuestion(QuizQuestionBase):
    id: int
    
    class Config:
        orm_mode = True
