
import sqlite3

def migrate_student_profiles():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    # List of columns to add to student_profiles
    columns = [
        ("license_image", "VARCHAR"),
        ("is_verified", "BOOLEAN DEFAULT 0"),
        ("license_status", "VARCHAR DEFAULT 'pending'"),
        ("rejection_reason", "TEXT")
    ]
    
    for col_name, col_type in columns:
        try:
            print(f"Adding {col_name} to student_profiles...")
            cursor.execute(f"ALTER TABLE student_profiles ADD COLUMN {col_name} {col_type}")
        except sqlite3.OperationalError as e:
            if "duplicate column name" in str(e):
                print(f"Column {col_name} already exists.")
            else:
                print(f"Error adding {col_name}: {e}")

    conn.commit()
    conn.close()
    print("Student Profile migration complete.")

if __name__ == "__main__":
    migrate_student_profiles()
