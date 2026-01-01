
import sys
import os
from passlib.context import CryptContext

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.getcwd(), "lib"))

from app import models, database

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

def migrate_passwords():
    db = database.SessionLocal()
    users = db.query(models.User).all()
    
    count = 0
    for user in users:
        # Check if the password is already hashed (bcrypt hashes start with $2b$)
        if user.hashed_password and not user.hashed_password.startswith("$2b$"):
            print(f"Hashing password for user: {user.email}")
            # Truncate to 72 bytes for bcrypt compatibility and convert to bytes
            pwd = user.hashed_password[:72].encode('utf-8')
            user.hashed_password = pwd_context.hash(pwd)
            count += 1
    
    db.commit()
    db.close()
    print(f"Successfully migrated {count} passwords.")

if __name__ == "__main__":
    migrate_passwords()
