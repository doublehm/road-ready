from sqlalchemy import Boolean, Column, ForeignKey, Integer, String, Float, Text
from sqlalchemy.orm import relationship
from .database import Base

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    email = Column(String, unique=True, index=True)
    hashed_password = Column(String)
    full_name = Column(String)
    role = Column(String) # "instructor" or "student"
    phone_number = Column(String)
    reset_token = Column(String, nullable=True)
    reset_token_expiry = Column(String, nullable=True)

    instructor_profile = relationship("InstructorProfile", back_populates="user", uselist=False)
    student_profile = relationship("StudentProfile", back_populates="user", uselist=False)

class InstructorProfile(Base):
    __tablename__ = "instructor_profiles"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"))
    bio = Column(Text)
    hourly_rate = Column(Float)
    city = Column(String, index=True)
    car_model = Column(String)
    insurance_policy = Column(String)
    certification_id = Column(String)
    certification_expiry = Column(String, nullable=True) # YYYY-MM-DD
    license_image = Column(String, nullable=True) # Filename of the uploaded license
    insurance_image = Column(String, nullable=True) # Filename of the uploaded insurance
    certification_image = Column(String, nullable=True) # Filename of the uploaded ICBC certificate
    is_verified = Column(Boolean, default=False)
    is_available = Column(Boolean, default=True)
    
    # Stripe Connect fields
    stripe_account_id = Column(String, nullable=True)
    stripe_onboarding_completed = Column(Boolean, default=False)

    # Legal & Compliance fields
    business_registration_number = Column(String, nullable=True)
    tax_id = Column(String, nullable=True) # GST/PST registration
    worksafe_bc_id = Column(String, nullable=True)
    legal_entity_name = Column(String, nullable=True)

    user = relationship("User", back_populates="instructor_profile")
    booking_requests = relationship("BookingRequest", back_populates="instructor")
    reviews = relationship("Review", back_populates="instructor")
    availabilities = relationship("InstructorAvailability", back_populates="instructor", cascade="all, delete-orphan")
    license_classes = relationship("InstructorLicenseClass", back_populates="instructor", cascade="all, delete-orphan")

class InstructorLicenseClass(Base):
    __tablename__ = "instructor_license_classes"

    id = Column(Integer, primary_key=True, index=True)
    instructor_id = Column(Integer, ForeignKey("instructor_profiles.id"))
    license_class = Column(String) # "Class 5", "Class 7", "Class 4", etc.
    price = Column(Float)

    instructor = relationship("InstructorProfile", back_populates="license_classes")

class InstructorAvailability(Base):
    __tablename__ = "instructor_availabilities"

    id = Column(Integer, primary_key=True, index=True)
    instructor_id = Column(Integer, ForeignKey("instructor_profiles.id"))
    day_of_week = Column(Integer) # 0=Monday, 6=Sunday
    start_time = Column(String) # HH:MM
    end_time = Column(String) # HH:MM

    instructor = relationship("InstructorProfile", back_populates="availabilities")

class StudentProfile(Base):
    __tablename__ = "student_profiles"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"))
    age = Column(Integer)
    l_license_number = Column(String)
    license_image = Column(String, nullable=True) # Path to uploaded file
    is_verified = Column(Boolean, default=False) # Deprecated in favor of status, but kept for compat
    license_status = Column(String, default="pending") # pending, verified, rejected
    license_expiry = Column(String, nullable=True) # YYYY-MM-DD
    rejection_reason = Column(Text, nullable=True)

    # Diagnostic ride fields
    diagnostic_completed = Column(Boolean, default=False)
    diagnostic_ride_id = Column(Integer, ForeignKey("diagnostic_rides.id"), nullable=True)
    current_module_id = Column(Integer, ForeignKey("learning_modules.id"), nullable=True)
    basics_skipped = Column(Boolean, default=False)

    user = relationship("User", back_populates="student_profile")
    diagnostic_ride = relationship("DiagnosticRide", foreign_keys=[diagnostic_ride_id])
    current_module = relationship("LearningModule", foreign_keys=[current_module_id])

class BookingRequest(Base):
    __tablename__ = "booking_requests"

    id = Column(Integer, primary_key=True, index=True)
    student_id = Column(Integer, ForeignKey("users.id"))
    instructor_id = Column(Integer, ForeignKey("instructor_profiles.id"))

    date = Column(String) # Storing as string for simplicity in prototype (YYYY-MM-DD)
    time = Column(String) # HH:MM
    duration = Column(Integer) # in hours
    pickup_address = Column(String)
    dropoff_address = Column(String, nullable=True)
    
    # Coordinates for Map
    pickup_lat = Column(Float, nullable=True)
    pickup_lng = Column(Float, nullable=True)
    dropoff_lat = Column(Float, nullable=True)
    dropoff_lng = Column(Float, nullable=True)
    
    extra_travel_cost = Column(Float, default=0.0)
    
    notes = Column(Text)

    # Payment Fields
    payment_status = Column(String, default="pending") # pending, paid, refunded
    stripe_payment_intent_id = Column(String, nullable=True)
    total_amount = Column(Float, default=0.0)
    platform_fee = Column(Float, default=0.0)
    instructor_payout = Column(Float, default=0.0)

    status = Column(String, default="pending_payment") # Changed default: pending -> pending_payment -> pending (approval)
    
    module_id = Column(Integer, ForeignKey("learning_modules.id"), nullable=True)

    student = relationship("User", foreign_keys=[student_id])
    instructor = relationship("InstructorProfile", foreign_keys=[instructor_id], back_populates="booking_requests")
    module = relationship("LearningModule", foreign_keys=[module_id])

class Review(Base):
    __tablename__ = "reviews"

    id = Column(Integer, primary_key=True, index=True)
    instructor_id = Column(Integer, ForeignKey("instructor_profiles.id"))
    student_id = Column(Integer, ForeignKey("users.id"))
    rating = Column(Integer) # 1-5
    comment = Column(Text)
    created_at = Column(String) # Storing as string for simplicity

    instructor = relationship("InstructorProfile", back_populates="reviews")
    student = relationship("User", foreign_keys=[student_id])

class DrivingSession(Base):
    __tablename__ = "driving_sessions"

    id = Column(Integer, primary_key=True, index=True)
    booking_id = Column(Integer, ForeignKey("booking_requests.id"))
    
    # Session Details
    duration_minutes = Column(Integer)
    weather_condition = Column(String) 
    road_type = Column(String)
    
    # Official Test Criteria Data (Stored as JSON strings: e.g. ["A1", "A3"])
    # Storing which faults were committed or areas worked on
    observation_data = Column(Text) # A1-A8
    space_margin_data = Column(Text) # B1-B14
    speed_data = Column(Text) # C1-C9
    steering_data = Column(Text) # D1-D4
    communication_data = Column(Text) # E1-E4
    
    # Qualitative Feedback
    shared_feedback = Column(Text) 
    instructor_private_notes = Column(Text)
    student_private_notes = Column(Text)
    
    created_at = Column(String)

    booking = relationship("BookingRequest", back_populates="driving_session")

class Message(Base):
    __tablename__ = "messages"

    id = Column(Integer, primary_key=True, index=True)
    sender_id = Column(Integer, ForeignKey("users.id"))
    recipient_id = Column(Integer, ForeignKey("users.id"))
    content = Column(Text)
    timestamp = Column(String)
    is_read = Column(Boolean, default=False)

    sender = relationship("User", foreign_keys=[sender_id], back_populates="sent_messages")
    recipient = relationship("User", foreign_keys=[recipient_id], back_populates="received_messages")

class QuizQuestion(Base):
    __tablename__ = "quiz_questions"

    id = Column(Integer, primary_key=True, index=True)
    question_text = Column(Text)
    option_a = Column(String)
    option_b = Column(String)
    option_c = Column(String)
    option_d = Column(String)
    correct_option = Column(String) # "A", "B", "C", "D"
    explanation = Column(Text)
    category = Column(String, nullable=True)
    image_path = Column(String, nullable=True)

class Notification(Base):
    __tablename__ = "notifications"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"))
    title = Column(String)
    message = Column(Text)
    timestamp = Column(String)
    is_read = Column(Boolean, default=False)
    
    user = relationship("User", back_populates="notifications")

# Update BookingRequest to link to a session
BookingRequest.driving_session = relationship("DrivingSession", back_populates="booking", uselist=False)

# Update InstructorProfile relationships
InstructorProfile.booking_requests = relationship("BookingRequest", back_populates="instructor")
InstructorProfile.reviews = relationship("Review", back_populates="instructor")

# Update User relationships for messages
User.sent_messages = relationship("Message", foreign_keys=[Message.sender_id], back_populates="sender")
User.received_messages = relationship("Message", foreign_keys=[Message.recipient_id], back_populates="recipient")
User.notifications = relationship("Notification", back_populates="user")

class LearningModule(Base):
    __tablename__ = "learning_modules"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String) # "Basics", "Advanced/Test Prep"
    description = Column(Text)
    package_price = Column(Float) # Discounted package cost
    hourly_rate = Column(Float) # Individual lesson rate
    min_hours = Column(Integer) # Minimum hours for completion
    order = Column(Integer) # Display order
    skills_covered = Column(Text) # JSON string
    prerequisites = Column(Text) # JSON string - module IDs

class StudentModuleProgress(Base):
    __tablename__ = "student_module_progress"

    id = Column(Integer, primary_key=True, index=True)
    student_id = Column(Integer, ForeignKey("users.id"))
    module_id = Column(Integer, ForeignKey("learning_modules.id"))
    status = Column(String) # "locked", "unlocked", "in_progress", "completed"
    enrollment_type = Column(String) # "package", "hourly", "none"
    package_purchased = Column(Boolean, default=False)
    unlocked_at = Column(String, nullable=True) # ISO timestamp
    completed_at = Column(String, nullable=True) # ISO timestamp
    hours_completed = Column(Float, default=0.0)

    student = relationship("User", foreign_keys=[student_id])
    module = relationship("LearningModule", foreign_keys=[module_id])

class DiagnosticRide(Base):
    __tablename__ = "diagnostic_rides"

    id = Column(Integer, primary_key=True, index=True)
    student_id = Column(Integer, ForeignKey("users.id"))
    booking_id = Column(Integer, ForeignKey("booking_requests.id"), nullable=True)
    ride_type = Column(String) # "parent_supervised", "instructor_supervised"
    instructor_id = Column(Integer, ForeignKey("instructor_profiles.id"), nullable=True)

    start_time = Column(String) # ISO format
    end_time = Column(String, nullable=True) # ISO format
    duration_minutes = Column(Float, nullable=True)
    distance_km = Column(Float, nullable=True)

    route_coords = Column(Text, nullable=True) # JSON GPS coordinates
    acceleration_data = Column(Text, nullable=True) # JSON sensor data
    rotation_data = Column(Text, nullable=True) # JSON sensor data
    speed_data = Column(Text, nullable=True) # JSON GPS speed + coords

    braking_score = Column(Float, nullable=True) # 0-100
    speed_score = Column(Float, nullable=True) # 0-100
    cornering_score = Column(Float, nullable=True) # 0-100
    overall_score = Column(Float, nullable=True) # 0-100
    passed = Column(Boolean, nullable=True)

    evaluation_result = Column(Text, nullable=True) # JSON detailed feedback
    criteria_results = Column(Text, nullable=True) # JSON pass/fail per criteria
    evaluator_notes = Column(Text, nullable=True) # instructor notes
    instructor_override = Column(Boolean, default=False)

    status = Column(String, default="pending") # "pending", "evaluating", "completed"
    created_at = Column(String) # ISO timestamp
    evaluated_at = Column(String, nullable=True) # ISO timestamp

    student = relationship("User", foreign_keys=[student_id])
    instructor = relationship("InstructorProfile", foreign_keys=[instructor_id])
    booking = relationship("BookingRequest", foreign_keys=[booking_id])

class DriveLog(Base):
    __tablename__ = "drive_logs"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"))
    start_time = Column(String)
    end_time = Column(String)
    duration_minutes = Column(Float)
    distance_km = Column(Float)
    route_coords = Column(Text) # JSON string of coordinates
    notes = Column(Text, nullable=True)
    
    user = relationship("User", back_populates="drive_logs")

User.drive_logs = relationship("DriveLog", back_populates="user")

class StudentProgress(Base):
    __tablename__ = "student_progress"

    id = Column(Integer, primary_key=True, index=True)
    student_id = Column(Integer, ForeignKey("users.id"), unique=True)
    
    overall_score = Column(Float, default=0.0)
    total_lessons = Column(Integer, default=0)
    quizzes_completed = Column(Integer, default=0)
    diagnostic_ride_passed = Column(Boolean, default=False)
    
    # Comprehensive status
    status = Column(String, default="new") # "new", "in_progress", "ready_for_test", "licensed"
    
    last_updated = Column(String) # ISO timestamp

    student = relationship("User", foreign_keys=[student_id], back_populates="comprehensive_progress")

# Update User model to include comprehensive_progress
User.comprehensive_progress = relationship("StudentProgress", uselist=False, back_populates="student")


