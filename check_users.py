import sys
import os
import sqlite3

def check_db():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    print("--- USERS ---")
    cursor.execute("SELECT id, full_name, role, email FROM users")
    users = cursor.fetchall()
    for u in users:
        print(u)
        
    print("\n--- STUDENT PROFILES ---")
    cursor.execute("SELECT user_id, license_status FROM student_profiles")
    s_profiles = cursor.fetchall()
    for p in s_profiles:
        print(p)

    print("\n--- INSTRUCTOR PROFILES ---")
    cursor.execute("SELECT user_id, is_verified FROM instructor_profiles")
    i_profiles = cursor.fetchall()
    for p in i_profiles:
        print(p)
        
    conn.close()

if __name__ == "__main__":
    check_db()