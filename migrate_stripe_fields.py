import sqlite3

def migrate():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()

    print("Adding Stripe columns to instructor_profiles table...")
    try:
        cursor.execute("ALTER TABLE instructor_profiles ADD COLUMN stripe_account_id TEXT")
        print("Added stripe_account_id column.")
    except sqlite3.OperationalError as e:
        print(f"Skipping stripe_account_id: {e}")

    try:
        cursor.execute("ALTER TABLE instructor_profiles ADD COLUMN stripe_onboarding_completed BOOLEAN DEFAULT 0")
        print("Added stripe_onboarding_completed column.")
    except sqlite3.OperationalError as e:
        print(f"Skipping stripe_onboarding_completed: {e}")

    conn.commit()
    conn.close()
    print("Migration complete!")

if __name__ == "__main__":
    migrate()
