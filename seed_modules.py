"""
Seed script to populate initial learning modules in the database.

Run this after starting the app for the first time to create the Basics and Advanced modules.
"""

import sys
import os
import json

# Add the project root to the path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from app import models, database

def seed_modules():
    """Create initial learning modules if they don't exist."""
    db = database.SessionLocal()

    try:
        # Check if modules already exist
        existing_modules = db.query(models.LearningModule).count()

        if existing_modules > 0:
            print(f"✓ Learning modules already exist ({existing_modules} modules found)")
            return

        # Create Basics Module
        basics_skills = [
            "Vehicle controls and instruments",
            "Starting and stopping smoothly",
            "Steering and lane positioning",
            "Basic turns and intersections",
            "Parking (parallel, angle, reverse)",
            "Mirror and blind spot checks",
            "Speed control and braking",
            "Traffic signs and signals",
            "Road markings and lane discipline",
            "Defensive driving basics"
        ]

        basics_module = models.LearningModule(
            name="Basics",
            description="Master fundamental driving skills including vehicle control, basic maneuvers, parking, and road safety. Perfect for learners starting their driving journey.",
            package_price=800.0,  # 10 hours at $80/hr, discounted to $800
            hourly_rate=80.0,
            min_hours=10,
            order=1,
            skills_covered=json.dumps(basics_skills),
            prerequisites=json.dumps([])  # No prerequisites
        )
        db.add(basics_module)

        # Create Advanced/Test Prep Module
        advanced_skills = [
            "Highway driving and merging",
            "Advanced lane changes",
            "Complex intersections and roundabouts",
            "Emergency maneuvers",
            "Adverse weather driving",
            "Night driving techniques",
            "Advanced defensive driving",
            "Road test preparation",
            "Evaluation criteria review",
            "Mock road tests"
        ]

        advanced_module = models.LearningModule(
            name="Advanced/Test Prep",
            description="Prepare for your road test with advanced skills, highway driving, and comprehensive test preparation. Includes mock tests and final evaluation.",
            package_price=600.0,  # 8 hours at $75/hr, discounted to $600
            hourly_rate=75.0,
            min_hours=8,
            order=2,
            skills_covered=json.dumps(advanced_skills),
            prerequisites=json.dumps([1])  # Requires Basics module (id: 1)
        )
        db.add(advanced_module)

        db.commit()

        print("✓ Successfully created learning modules:")
        print("  1. Basics - $800 package (10 hours) or $80/hr")
        print("  2. Advanced/Test Prep - $600 package (8 hours) or $75/hr")
        print("\nTotal potential savings by passing diagnostic: $800")

    except Exception as e:
        print(f"✗ Error seeding modules: {str(e)}")
        db.rollback()
    finally:
        db.close()


if __name__ == "__main__":
    print("Seeding learning modules...")
    print("-" * 50)

    # Ensure tables are created first
    print("Creating database tables if they don't exist...")
    models.Base.metadata.create_all(bind=database.engine)
    print("✓ Database tables ready\n")

    seed_modules()
    print("-" * 50)
    print("Seeding complete!")
