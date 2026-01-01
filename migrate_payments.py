
import sqlite3

def migrate_payments():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    columns = [
        ("payment_status", "VARCHAR DEFAULT 'pending'"),
        ("stripe_payment_intent_id", "VARCHAR"),
        ("total_amount", "FLOAT DEFAULT 0.0"),
        ("platform_fee", "FLOAT DEFAULT 0.0"),
        ("instructor_payout", "FLOAT DEFAULT 0.0")
    ]
    
    for col_name, col_type in columns:
        try:
            print(f"Adding {col_name}...")
            cursor.execute(f"ALTER TABLE booking_requests ADD COLUMN {col_name} {col_type}")
        except sqlite3.OperationalError as e:
            if "duplicate column name" in str(e):
                print(f"Column {col_name} already exists.")
            else:
                print(f"Error adding {col_name}: {e}")

    conn.commit()
    conn.close()
    print("Payment migration complete.")

if __name__ == "__main__":
    migrate_payments()
