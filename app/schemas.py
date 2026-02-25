from pydantic import BaseModel, EmailStr, validator
from typing import Optional, List, Dict
import re

# --- Shared Schemas ---
class UserBase(BaseModel):
    email: EmailStr
    full_name: str
    phone_number: str

    @validator('phone_number')
    def validate_phone(cls, v):
        if v is None:
            return v
        # Remove common delimiters
        clean_number = re.sub(r'[\s\-\(\)\.]', '', v)
        if not re.match(r'^\+?1?\d{7,15}$', clean_number):
            raise ValueError('Phone number must be valid (7-15 digits)')
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

class UserUpdate(BaseModel):
    full_name: Optional[str] = None
    email: Optional[EmailStr] = None
    phone_number: Optional[str] = None
    password: Optional[str] = None

class User(UserBase):
    id: int
    role: str
    phone_number: Optional[str] = None  # DB may have NULL for existing users

    class Config:
        from_attributes = True

# --- Instructor Schemas ---
class InstructorLicenseClassBase(BaseModel):
    license_class: str
    price: float

class InstructorLicenseClassCreate(InstructorLicenseClassBase):
    pass

class InstructorLicenseClass(InstructorLicenseClassBase):
    id: int
    instructor_id: int
    
    class Config:
        from_attributes = True

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
        from_attributes = True

class InstructorProfileBase(BaseModel):
    bio: str
    hourly_rate: float
    city: str
    car_model: str
    insurance_policy: str
    certification_id: str
    certification_expiry: Optional[str] = None
    is_available: bool = True
    stripe_account_id: Optional[str] = None
    stripe_onboarding_completed: bool = False
    business_registration_number: Optional[str] = None
    tax_id: Optional[str] = None
    worksafe_bc_id: Optional[str] = None
    legal_entity_name: Optional[str] = None

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
    license_image: Optional[str] = None
    insurance_image: Optional[str] = None
    certification_image: Optional[str] = None
    license_classes: List[InstructorLicenseClassCreate] = []

class InstructorProfile(InstructorProfileBase):
    id: int
    user_id: int
    is_verified: bool
    user: Optional[User] = None
    availabilities: List[InstructorAvailability] = []
    license_classes: List[InstructorLicenseClass] = []
    license_image: Optional[str] = None
    insurance_image: Optional[str] = None
    certification_image: Optional[str] = None

    class Config:
        from_attributes = True

# --- Student Schemas ---
class StudentProfileBase(BaseModel):
    age: int
    l_license_number: str
    license_expiry: Optional[str] = None

    @validator('l_license_number')
    def validate_license(cls, v):
        # BC Driver's License is typically 7 digits, sometimes written with L-
        # Allow L- prefix or just digits
        clean_license = v.replace('L-', '').replace('l-', '')
        if not re.match(r'^\d{7}$', clean_license):
            raise ValueError('BC Driver License must be exactly 7 digits')
        return v

class StudentProfileCreate(StudentProfileBase):
    license_image: Optional[str] = None

class StudentProfile(StudentProfileBase):
    id: int
    user_id: int
    license_image: Optional[str] = None
    is_verified: bool
    user: Optional[User] = None

    class Config:
        from_attributes = True

class StudentProgress(BaseModel):
    id: int
    student_id: int
    overall_score: float
    total_lessons: int
    quizzes_completed: int
    diagnostic_ride_passed: bool
    status: str
    last_updated: Optional[str] = None

    class Config:
        from_attributes = True

class UserWithProfile(User):
    student_profile: Optional[StudentProfile] = None
    instructor_profile: Optional[InstructorProfile] = None
    comprehensive_progress: Optional[StudentProgress] = None

    class Config:
        from_attributes = True

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
        from_attributes = True

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

class BookingAction(BaseModel):
    action: str
    reason: Optional[str] = None

class BookingRequest(BookingRequestBase):
    id: int
    student_id: int
    status: str
    total_amount: float
    instructor_payout: float = 0.0
    platform_fee: float = 0.0
    instructor: Optional[InstructorProfile] = None
    student: Optional[UserWithProfile] = None
    driving_session: Optional['DrivingSession'] = None
    
    class Config:
        from_attributes = True

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
        from_attributes = True

# --- Quiz Schemas ---
class QuizQuestionBase(BaseModel):
    question_text: str
    option_a: str
    option_b: str
    option_c: str
    option_d: str
    correct_option: str
    explanation: Optional[str] = None
    category: Optional[str] = None
    image_path: Optional[str] = None

class QuizQuestion(QuizQuestionBase):
    id: int
    
    class Config:
        from_attributes = True

# --- Message Schemas ---
class MessageBase(BaseModel):
    recipient_id: int
    content: str

class MessageCreate(MessageBase):
    pass

class Message(MessageBase):
    id: int
    sender_id: int
    timestamp: str
    is_read: bool

    class Config:
        from_attributes = True

class Notification(BaseModel):
    id: int
    title: str
    message: str
    timestamp: str
    is_read: bool

    class Config:
        from_attributes = True

# --- Learning Module Schemas ---
class LearningModuleBase(BaseModel):
    name: str
    description: str
    package_price: float
    hourly_rate: float
    min_hours: int
    order: int
    skills_covered: str # JSON string
    prerequisites: str # JSON string

class LearningModuleCreate(LearningModuleBase):
    pass

class LearningModule(LearningModuleBase):
    id: int

    class Config:
        from_attributes = True

# --- Student Module Progress Schemas ---
class StudentModuleProgressBase(BaseModel):
    module_id: int
    status: str
    enrollment_type: str
    package_purchased: bool = False
    hours_completed: float = 0.0

class StudentModuleProgressCreate(StudentModuleProgressBase):
    student_id: int

class StudentModuleProgress(StudentModuleProgressBase):
    id: int
    student_id: int
    unlocked_at: Optional[str] = None
    completed_at: Optional[str] = None
    module: Optional[LearningModule] = None

    class Config:
        from_attributes = True

# --- Diagnostic Ride Schemas ---
class SensorDataPoint(BaseModel):
    timestamp: float
    x: Optional[float] = None
    y: Optional[float] = None
    z: Optional[float] = None
    speed: Optional[float] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    speed_limit: Optional[float] = None
    heading: Optional[float] = None

class DiagnosticRideBase(BaseModel):
    ride_type: str # "parent_supervised", "instructor_supervised"
    instructor_id: Optional[int] = None

class DiagnosticRideCreate(DiagnosticRideBase):
    start_time: str
    end_time: Optional[str] = None
    booking_id: Optional[int] = None
    duration_minutes: Optional[float] = None
    distance_km: Optional[float] = None
    route_coords: Optional[str] = None # JSON string
    acceleration_data: Optional[str] = None # JSON string
    rotation_data: Optional[str] = None # JSON string
    speed_data: Optional[str] = None # JSON string
    speed_limit_data: Optional[str] = None # JSON string
    heading_data: Optional[str] = None # JSON string
    criteria_results: Optional[str] = None # JSON string
    evaluator_notes: Optional[str] = None # coach notes from during ride
    human_feedback: Optional[str] = None # JSON: [{code, label, category, count, timestamps}]

class LiveEvaluationRequest(BaseModel):
    acceleration_window: List[SensorDataPoint]
    speed_window: List[SensorDataPoint]
    rotation_window: List[SensorDataPoint]

class DiagnosticRideEvaluation(BaseModel):
    braking_score: float
    speed_score: float
    cornering_score: float
    smoothness_score: float
    overall_score: float
    passed: bool
    evaluation_result: str # JSON string with detailed feedback

class InstructorReview(BaseModel):
    evaluator_notes: str
    override_passed: Optional[bool] = None
    override_braking_score: Optional[float] = None
    override_speed_score: Optional[float] = None
    override_cornering_score: Optional[float] = None

class DiagnosticRide(DiagnosticRideBase):
    id: int
    student_id: int
    booking_id: Optional[int] = None
    instructor_id: Optional[int] = None
    student: Optional[User] = None
    instructor: Optional["InstructorProfile"] = None
    start_time: str
    end_time: Optional[str] = None
    duration_minutes: Optional[float] = None
    distance_km: Optional[float] = None
    route_coords: Optional[str] = None
    acceleration_data: Optional[str] = None
    rotation_data: Optional[str] = None
    speed_data: Optional[str] = None
    speed_limit_data: Optional[str] = None
    heading_data: Optional[str] = None
    braking_score: Optional[float] = None
    speed_score: Optional[float] = None
    cornering_score: Optional[float] = None
    smoothness_score: Optional[float] = None
    overall_score: Optional[float] = None
    passed: Optional[bool] = None
    evaluation_result: Optional[str] = None
    criteria_results: Optional[str] = None
    evaluator_notes: Optional[str] = None
    human_feedback: Optional[str] = None
    instructor_override: bool = False
    status: str
    created_at: str
    evaluated_at: Optional[str] = None

    class Config:
        from_attributes = True

class LiveTelemetry(BaseModel):
    acceleration: Optional[Dict[str, float]] = None
    speed: Optional[float] = None
    location: Optional[Dict[str, float]] = None
    heading: Optional[float] = None
    timestamp: float

class LiveEvent(BaseModel):
    event_type: str # "harsh_braking", "sharp_cornering", "speeding"
    severity: str # "low", "medium", "high"
    timestamp: float
    location: Optional[Dict[str, float]] = None
    value: Optional[float] = None # e.g. G-force or speed

class LiveWebSocketMessage(BaseModel):
    type: str # "telemetry", "event", "ping", "pong", "system"
    data: Optional[Dict] = None
