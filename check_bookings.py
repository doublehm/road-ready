import sqlite3

def check_bookings():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    cursor.execute("SELECT id, student_id, instructor_id, status FROM booking_requests")
    bookings = cursor.fetchall()
    print("Booking Requests:")
    for b in bookings:
        print(b)
    
    cursor.execute("SELECT id, user_id FROM instructor_profiles")
    instructors = cursor.fetchall()
    print("\nInstructor Profiles (id, user_id):")
    for i in instructors:
        print(i)
        
    conn.close()

if __name__ == "__main__":
    check_bookings()
