import os
from app.database import engine, Base
from app import models

def recreate_db():
    db_path = "roadready.db"
    if os.path.exists(db_path):
        os.remove(db_path)
        print(f"Deleted existing {db_path}")

    # Create all tables according to the latest models.py
    Base.metadata.create_all(bind=engine)
    print("New database created with the latest schema.")

if __name__ == "__main__":
    recreate_db()
