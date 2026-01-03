import sys
import os

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.getcwd(), "lib"))

from app import models, database
from sqlalchemy.orm import Session

def clean_data():
    db = database.SessionLocal()
    try:
        print("Cleaning user-generated data...")
        
        # Delete dependent tables first to avoid FK constraint errors
        db.query(models.Message).delete()
        db.query(models.DrivingSession).delete()
        db.query(models.Review).delete()
        db.query(models.BookingRequest).delete()
        db.query(models.InstructorAvailability).delete()
        
        # Delete Profiles
        db.query(models.InstructorProfile).delete()
        db.query(models.StudentProfile).delete()
        
        # Delete Users (except maybe an admin if you had one hardcoded, but here we wipe all)
        db.query(models.User).delete()
        
        # Note: We are keeping QuizQuestion table intact
        
        db.commit()
        print("Database cleaned successfully. Quiz questions preserved.")
        
    except Exception as e:
        print(f"Error cleaning data: {e}")
        db.rollback()
    finally:
        db.close()

if __name__ == "__main__":
    clean_data()