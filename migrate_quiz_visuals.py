import sqlite3

def migrate():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()

    print("Adding columns to quiz_questions table...")
    try:
        cursor.execute("ALTER TABLE quiz_questions ADD COLUMN category TEXT")
        print("Added category column.")
    except sqlite3.OperationalError as e:
        print(f"Skipping category: {e}")

    try:
        cursor.execute("ALTER TABLE quiz_questions ADD COLUMN image_path TEXT")
        print("Added image_path column.")
    except sqlite3.OperationalError as e:
        print(f"Skipping image_path: {e}")

    conn.commit()
    conn.close()
    print("Migration complete!")

if __name__ == "__main__":
    migrate()
