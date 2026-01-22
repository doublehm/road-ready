import sqlite3

def migrate_database():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    # List of columns to add to student_profiles
    columns_to_add = [
        ("diagnostic_completed", "BOOLEAN DEFAULT 0"),
        ("diagnostic_ride_id", "INTEGER"),
        ("current_module_id", "INTEGER"),
        ("basics_skipped", "BOOLEAN DEFAULT 0"),
        ("license_image", "VARCHAR"),
        ("is_verified", "BOOLEAN DEFAULT 0"),
        ("license_status", "VARCHAR DEFAULT 'pending'"),
        ("license_expiry", "VARCHAR"),
        ("rejection_reason", "VARCHAR")
    ]
    
    for col_name, col_type in columns_to_add:
        try:
            print(f"Adding column {col_name} to student_profiles...")
            cursor.execute(f"ALTER TABLE student_profiles ADD COLUMN {col_name} {col_type}")
        except sqlite3.OperationalError as e:
            if "duplicate column name" in str(e):
                print(f"Column {col_name} already exists.")
            else:
                print(f"Error adding {col_name}: {e}")

    conn.commit()
    conn.close()
    print("Migration complete.")

if __name__ == "__main__":
    migrate_database()