import sqlite3

def migrate_database():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    # 1. Add insurance_image to instructor_profiles
    try:
        print("Adding column insurance_image to instructor_profiles...")
        cursor.execute("ALTER TABLE instructor_profiles ADD COLUMN insurance_image VARCHAR")
    except sqlite3.OperationalError as e:
        if "duplicate column name" in str(e):
            print("Column insurance_image already exists.")
        else:
            print(f"Error adding insurance_image: {e}")

    conn.commit()
    conn.close()
    print("Migration complete.")

if __name__ == "__main__":
    migrate_database()
