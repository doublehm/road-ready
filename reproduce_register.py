
import sys
import os

# Add local lib directory to sys.path
sys.path.append(os.getcwd())

from app import models, database, schemas
from sqlalchemy.orm import Session
from passlib.context import CryptContext

# Password hashing setup
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
def get_password_hash(password):
    return pwd_context.hash(password)

def test_register():
    print("Testing registration...")
    db = database.SessionLocal()
    
    email = "test_user_123@example.com"
    password = "Password123!" # Meets all criteria
    full_name = "Test User"
    phone = "555-123-4567"
    role = "student"

    # 1. Validation Logic (simulating main.py)
    try:
        print("Validating input...")
        schemas.UserCreate(email=email, password=password, full_name=full_name, phone_number=phone, role=role)
        print("Validation successful.")
    except Exception as e:
        print(f"Validation failed: {e}")
        return

    # 2. Database Insertion
    try:
        # Check if exists
        existing = db.query(models.User).filter(models.User.email == email).first()
        if existing:
            print("User already exists, deleting for test...")
            db.delete(existing)
            db.commit()
        
        print("Creating user object...")
        new_user = models.User(
            email=email, 
            hashed_password=get_password_hash(password), 
            full_name=full_name, 
            phone_number=phone, 
            role=role
        )
        db.add(new_user)
        print("Committing to database...")
        db.commit()
        print("Refreshing...")
        db.refresh(new_user)
        print(f"Registration successful! User ID: {new_user.id}")
        
    except Exception as e:
        print(f"Database error: {e}")
    finally:
        db.close()

if __name__ == "__main__":
    # Ensure tables exist
    models.Base.metadata.create_all(bind=database.engine)
    test_register()
