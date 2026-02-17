import sqlite3

def migrate():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()

    print("Adding Legal & Compliance columns to instructor_profiles table...")
    
    columns = [
        ("business_registration_number", "TEXT"),
        ("tax_id", "TEXT"),
        ("worksafe_bc_id", "TEXT"),
        ("legal_entity_name", "TEXT")
    ]

    for col_name, col_type in columns:
        try:
            cursor.execute(f"ALTER TABLE instructor_profiles ADD COLUMN {col_name} {col_type}")
            print(f"Added {col_name} column.")
        except sqlite3.OperationalError as e:
            print(f"Skipping {col_name}: {e}")

    conn.commit()
    conn.close()
    print("Migration complete!")

if __name__ == "__main__":
    migrate()
