import sqlite3

def migrate_database():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    # 1. Add module_id to booking_requests
    try:
        print("Adding column module_id to booking_requests...")
        cursor.execute("ALTER TABLE booking_requests ADD COLUMN module_id INTEGER")
    except sqlite3.OperationalError as e:
        if "duplicate column name" in str(e):
            print("Column module_id already exists.")
        else:
            print(f"Error adding module_id: {e}")

    # 2. Add lesson_type to booking_requests
    try:
        print("Adding column lesson_type to booking_requests...")
        cursor.execute("ALTER TABLE booking_requests ADD COLUMN lesson_type VARCHAR DEFAULT 'regular'")
    except sqlite3.OperationalError as e:
        if "duplicate column name" in str(e):
            print("Column lesson_type already exists.")
        else:
            print(f"Error adding lesson_type: {e}")

    conn.commit()
    conn.close()
    print("Migration complete.")

if __name__ == "__main__":
    migrate_database()
