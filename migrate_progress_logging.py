import sqlite3

def migrate():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()

    print("Adding columns to diagnostic_rides table...")
    try:
        cursor.execute("ALTER TABLE diagnostic_rides ADD COLUMN booking_id INTEGER REFERENCES booking_requests(id)")
        print("Added booking_id column.")
    except sqlite3.OperationalError as e:
        print(f"Skipping booking_id: {e}")

    try:
        cursor.execute("ALTER TABLE diagnostic_rides ADD COLUMN criteria_results TEXT")
        print("Added criteria_results column.")
    except sqlite3.OperationalError as e:
        print(f"Skipping criteria_results: {e}")

    print("Creating student_progress table...")
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS student_progress (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        student_id INTEGER UNIQUE REFERENCES users(id),
        overall_score FLOAT DEFAULT 0.0,
        total_lessons INTEGER DEFAULT 0,
        quizzes_completed INTEGER DEFAULT 0,
        diagnostic_ride_passed BOOLEAN DEFAULT 0,
        status TEXT DEFAULT 'new',
        last_updated TEXT
    )
    """)
    print("student_progress table ready.")

    conn.commit()
    conn.close()
    print("Migration complete!")

if __name__ == "__main__":
    migrate()
