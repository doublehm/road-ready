
import sqlite3

def migrate_license_column():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    try:
        print("Adding license_image column...")
        cursor.execute("ALTER TABLE instructor_profiles ADD COLUMN license_image VARCHAR")
        conn.commit()
        print("Migration complete.")
    except sqlite3.OperationalError as e:
        if "duplicate column name" in str(e):
            print("Column license_image already exists.")
        else:
            print(f"Error: {e}")

    conn.close()

if __name__ == "__main__":
    migrate_license_column()
