
import sys
import os

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.getcwd(), "lib"))

from app import models, database
from sqlalchemy import text

def clean_database():
    db = database.SessionLocal()
    
    print("🧹 Cleaning up dummy data...")
    
    # 1. Delete all bookings
    db.query(models.BookingRequest).delete()
    print("- Bookings deleted.")
    
    # 2. Delete all driving sessions
    db.query(models.DrivingSession).delete()
    print("- Driving sessions deleted.")
    
    # 3. Delete all reviews
    db.query(models.Review).delete()
    print("- Reviews deleted.")
    
    # 4. Delete all messages
    db.query(models.Message).delete()
    print("- Messages deleted.")
    
    # 5. Delete all availability slots
    db.query(models.InstructorAvailability).delete()
    print("- Availability slots deleted.")
    
    # 6. Delete all instructor profiles
    db.query(models.InstructorProfile).delete()
    print("- Instructor profiles deleted.")
    
    # 7. Delete all student profiles
    db.query(models.StudentProfile).delete()
    print("- Student profiles deleted.")
    
    # 8. Delete all users
    db.query(models.User).delete()
    print("- Users deleted.")
    
    # NOTE: We are NOT deleting QuizQuestion, as that is valid app content.
    
    db.commit()
    db.close()
    print("✨ Database is now clean and ready for real users!")

if __name__ == "__main__":
    clean_database()
