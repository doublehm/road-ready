
import sys
import os

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.getcwd(), "lib"))

from app import models, database

def check_users():
    db = database.SessionLocal()
    users = db.query(models.User).all()
    
    print(f"Total Users Found: {len(users)}")
    for user in users:
        print(f" - {user.email} (ID: {user.id})")
        
    db.close()

if __name__ == "__main__":
    check_users()
