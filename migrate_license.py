import sys
import os
import sqlite3

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.getcwd(), "lib"))

def migrate_license_status():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    try:
        # Check if column exists
        cursor.execute("SELECT license_status FROM student_profiles LIMIT 1")
    except sqlite3.OperationalError:
        print("Adding license_status column...")
        cursor.execute("ALTER TABLE student_profiles ADD COLUMN license_status VARCHAR DEFAULT 'pending'")
        
    try:
        # Check if column exists
        cursor.execute("SELECT rejection_reason FROM student_profiles LIMIT 1")
    except sqlite3.OperationalError:
        print("Adding rejection_reason column...")
        cursor.execute("ALTER TABLE student_profiles ADD COLUMN rejection_reason TEXT")

    # Update existing verified students
    cursor.execute("UPDATE student_profiles SET license_status = 'verified' WHERE is_verified = 1")
    
    conn.commit()
    conn.close()
    print("Migration complete.")

if __name__ == "__main__":
    migrate_license_status()