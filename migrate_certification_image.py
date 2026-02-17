import sqlite3

def migrate():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()

    print("Adding certification_image column to instructor_profiles table...")
    try:
        cursor.execute("ALTER TABLE instructor_profiles ADD COLUMN certification_image TEXT")
        print("Added certification_image column.")
    except sqlite3.OperationalError as e:
        print(f"Skipping certification_image: {e}")

    conn.commit()
    conn.close()
    print("Migration complete!")

if __name__ == "__main__":
    migrate()
