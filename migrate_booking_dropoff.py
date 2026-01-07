
import sys
import os
import sqlite3

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.getcwd(), "lib"))

def migrate_booking_dropoff():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    try:
        # Check if column exists
        cursor.execute("SELECT dropoff_address FROM booking_requests LIMIT 1")
    except sqlite3.OperationalError:
        print("Adding dropoff_address column...")
        cursor.execute("ALTER TABLE booking_requests ADD COLUMN dropoff_address VARCHAR")
        
    try:
        # Check if column exists
        cursor.execute("SELECT extra_travel_cost FROM booking_requests LIMIT 1")
    except sqlite3.OperationalError:
        print("Adding extra_travel_cost column...")
        cursor.execute("ALTER TABLE booking_requests ADD COLUMN extra_travel_cost FLOAT DEFAULT 0.0")

    conn.commit()
    conn.close()
    print("Migration complete.")

if __name__ == "__main__":
    migrate_booking_dropoff()
