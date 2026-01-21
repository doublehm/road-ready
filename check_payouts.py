
import sys
import os
import sqlite3

def check_payouts():
    conn = sqlite3.connect('roadready.db')
    cursor = conn.cursor()
    
    print("--- BOOKINGS (ID, Total, Payout, Status) ---")
    cursor.execute("SELECT id, total_amount, instructor_payout, status FROM booking_requests")
    bookings = cursor.fetchall()
    for b in bookings:
        print(b)
        
    conn.close()

if __name__ == "__main__":
    check_payouts()
