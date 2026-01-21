
import sys
import os
from sqlalchemy.orm import Session
from app import models, database

def fix_payouts():
    db = database.SessionLocal()
    
    bookings = db.query(models.BookingRequest).all()
    count = 0
    
    for b in bookings:
        if b.total_amount > 0 and (b.instructor_payout == 0 or b.instructor_payout is None):
            print(f"Fixing Booking #{b.id}: Total {b.total_amount}")
            
            # 10% Platform Fee logic
            b.platform_fee = b.total_amount * 0.10
            b.instructor_payout = b.total_amount - b.platform_fee
            count += 1
            
    db.commit()
    print(f"Fixed {count} bookings.")

if __name__ == "__main__":
    fix_payouts()
