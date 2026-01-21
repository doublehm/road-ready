from app.database import SessionLocal
from app.models import InstructorProfile

db = SessionLocal()
# User 18 has duplicates 8, 9, 10. We keep 10.
profiles_to_delete = db.query(InstructorProfile).filter(InstructorProfile.id.in_([8, 9])).all()

for p in profiles_to_delete:
    print(f"Deleting profile {p.id}")
    db.delete(p)

db.commit()
db.close()
print("Duplicates deleted.")
