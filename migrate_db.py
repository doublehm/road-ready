
import sqlite3

def migrate_database():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    # 1. Add columns to booking_requests
    columns_to_add = [
        ("pickup_lat", "FLOAT"),
        ("pickup_lng", "FLOAT"),
        ("dropoff_lat", "FLOAT"),
        ("dropoff_lng", "FLOAT")
    ]
    
    for col_name, col_type in columns_to_add:
        try:
            print(f"Adding column {col_name}...")
            cursor.execute(f"ALTER TABLE booking_requests ADD COLUMN {col_name} {col_type}")
        except sqlite3.OperationalError as e:
            if "duplicate column name" in str(e):
                print(f"Column {col_name} already exists.")
            else:
                print(f"Error adding {col_name}: {e}")

    # 2. Add columns to instructor_profiles (if we missed any previously)
    # Checking for availabilities table creation - that is handled by create_all normally, 
    # but let's ensure the relationship works. The table 'instructor_availabilities' should exist.
    
    conn.commit()
    conn.close()
    print("Migration complete.")

if __name__ == "__main__":
    migrate_database()
