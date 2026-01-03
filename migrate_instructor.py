
import sys
import os
import sqlite3

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.getcwd(), "lib"))

def migrate_instructor_profile():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    try:
        # Check if column exists
        cursor.execute("SELECT license_image FROM instructor_profiles LIMIT 1")
    except sqlite3.OperationalError:
        print("Adding license_image column to instructor_profiles...")
        cursor.execute("ALTER TABLE instructor_profiles ADD COLUMN license_image VARCHAR")
        
    conn.commit()
    conn.close()
    print("Migration complete.")

if __name__ == "__main__":
    migrate_instructor_profile()
