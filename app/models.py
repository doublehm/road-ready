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
    license_image = Column(String, nullable=True) # Filename of the uploaded license
    is_verified = Column(Boolean, default=False)
    is_available = Column(Boolean, default=True)

    user = relationship("User", back_populates="instructor_profile")
    booking_requests = relationship("BookingRequest", back_populates="instructor")
    reviews = relationship("Review", back_populates="instructor")
    availabilities = relationship("InstructorAvailability", back_populates="instructor", cascade="all, delete-orphan")

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
    
    user = relationship("User", back_populates="student_profile")

class BookingRequest(Base):
    __tablename__ = "booking_requests"

    id = Column(Integer, primary_key=True, index=True)
    student_id = Column(Integer, ForeignKey("users.id"))
    instructor_id = Column(Integer, ForeignKey("instructor_profiles.id"))
    
    date = Column(String) # Storing as string for simplicity in prototype (YYYY-MM-DD)
    time = Column(String) # HH:MM
    duration = Column(Integer) # in hours
    pickup_address = Column(String)
    
    # Route Coordinates
    pickup_lat = Column(Float, nullable=True)
    pickup_lng = Column(Float, nullable=True)
    dropoff_lat = Column(Float, nullable=True)
    dropoff_lng = Column(Float, nullable=True)
    
    notes = Column(Text)
    
    # Payment Fields
    payment_status = Column(String, default="pending") # pending, paid, refunded
    stripe_payment_intent_id = Column(String, nullable=True)
    total_amount = Column(Float, default=0.0)
    platform_fee = Column(Float, default=0.0) # Our commission
    instructor_payout = Column(Float, default=0.0) # Amount for instructor
    
    status = Column(String, default="pending_payment") # Changed default: pending -> pending_payment -> pending (approval)

    student = relationship("User", foreign_keys=[student_id])
    instructor = relationship("InstructorProfile", foreign_keys=[instructor_id], back_populates="booking_requests")

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

# Update BookingRequest to link to a session
BookingRequest.driving_session = relationship("DrivingSession", back_populates="booking", uselist=False)

# Update InstructorProfile relationships
InstructorProfile.booking_requests = relationship("BookingRequest", back_populates="instructor")
InstructorProfile.reviews = relationship("Review", back_populates="instructor")

# Update User relationships for messages
User.sent_messages = relationship("Message", foreign_keys=[Message.sender_id], back_populates="sender")
User.received_messages = relationship("Message", foreign_keys=[Message.recipient_id], back_populates="recipient")

