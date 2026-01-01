from pydantic import BaseModel, EmailStr
from typing import Optional, List

# --- Shared Schemas ---
class UserBase(BaseModel):
    email: EmailStr
    full_name: str
    phone_number: str

class UserCreate(UserBase):
    password: str
    role: str  # "student" or "instructor"

class User(UserBase):
    id: int
    role: str
    
    class Config:
        from_attributes = True

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
        from_attributes = True

class InstructorProfileBase(BaseModel):
    bio: str
    hourly_rate: float
    city: str
    car_model: str
    insurance_policy: str
    certification_id: str
    is_available: bool = True

class InstructorProfileCreate(InstructorProfileBase):
    pass

class InstructorProfile(InstructorProfileBase):
    id: int
    user_id: int
    is_verified: bool
    user: Optional[User] = None
    availabilities: List[InstructorAvailability] = []

    class Config:
        from_attributes = True

# --- Student Schemas ---
class StudentProfileBase(BaseModel):
    age: int
    l_license_number: str

class StudentProfileCreate(StudentProfileBase):
    pass

class StudentProfile(StudentProfileBase):
    id: int
    user_id: int
    user: Optional[User] = None

    class Config:
        from_attributes = True

# --- Search Schema ---
class SearchFilter(BaseModel):
    city: Optional[str] = None
    max_rate: Optional[float] = None
