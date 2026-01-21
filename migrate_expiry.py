
import sqlite3

def migrate_expiry():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    try:
        cursor.execute("ALTER TABLE student_profiles ADD COLUMN license_expiry VARCHAR")
        print("Added license_expiry to student_profiles")
    except:
        pass

    try:
        cursor.execute("ALTER TABLE instructor_profiles ADD COLUMN certification_expiry VARCHAR")
        print("Added certification_expiry to instructor_profiles")
    except:
        pass

    conn.commit()
    conn.close()

if __name__ == "__main__":
    migrate_expiry()
