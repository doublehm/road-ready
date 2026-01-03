
import sys
import os
import sqlite3

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.getcwd(), "lib"))

def migrate_student_profile():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    try:
        # Check if column exists
        cursor.execute("SELECT license_image FROM student_profiles LIMIT 1")
    except sqlite3.OperationalError:
        print("Adding license_image column...")
        cursor.execute("ALTER TABLE student_profiles ADD COLUMN license_image VARCHAR")
        
    try:
        # Check if column exists
        cursor.execute("SELECT is_verified FROM student_profiles LIMIT 1")
    except sqlite3.OperationalError:
        print("Adding is_verified column...")
        cursor.execute("ALTER TABLE student_profiles ADD COLUMN is_verified BOOLEAN DEFAULT 0")

    conn.commit()
    conn.close()
    print("Migration complete.")

if __name__ == "__main__":
    migrate_student_profile()
