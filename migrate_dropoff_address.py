
import sqlite3

def migrate_db():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    try:
        cursor.execute("ALTER TABLE booking_requests ADD COLUMN dropoff_address VARCHAR")
        print("Added dropoff_address column")
    except sqlite3.OperationalError:
        print("dropoff_address column likely already exists")

    conn.commit()
    conn.close()

if __name__ == "__main__":
    migrate_db()
