
import sys
import os

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.getcwd(), "lib"))

from app import models, database
from sqlalchemy.orm import Session

def seed_quiz_data():
    db = database.SessionLocal()
    
    # Check if questions already exist
    if db.query(models.QuizQuestion).count() > 0:
        print("Quiz questions already seeded.")
        return

    questions = [
        {
            "question_text": "When a school bus displays flashing red lights and the stop arm is extended, you must:",
            "option_a": "Slow down and pass with caution.",
            "option_b": "Stop at least 5 meters away.",
            "option_c": "Stop only if you are behind the bus.",
            "option_d": "Honk to warn children.",
            "correct_option": "B",
            "explanation": "You must stop whether you are approaching from the front or the rear (unless on a divided highway)."
        },
        {
            "question_text": "What does a flashing green traffic light mean?",
            "option_a": "The light is about to turn red.",
            "option_b": "It is a pedestrian controlled light.",
            "option_c": "You have the right of way to turn left.",
            "option_d": "Stop and proceed when safe.",
            "correct_option": "B",
            "explanation": "A flashing green light indicates a pedestrian-controlled light. Proceed with caution if the intersection is clear."
        },
        {
            "question_text": "When are you allowed to make a U-turn?",
            "option_a": "On a curve where you can be seen by other drivers.",
            "option_b": "At any intersection with a traffic light.",
            "option_c": "If there is no sign prohibiting it and you can do so safely without interfering with traffic.",
            "option_d": "On the crest of a hill.",
            "correct_option": "C",
            "explanation": "U-turns are generally permitted if safe and not prohibited by a sign. Never do a U-turn on a curve or hill."
        },
        {
            "question_text": "The 2-second rule is used to determine:",
            "option_a": "How long to wait at a stop sign.",
            "option_b": "A safe following distance in good weather.",
            "option_c": "How fast to accelerate.",
            "option_d": "The time it takes to check your mirrors.",
            "correct_option": "B",
            "explanation": "Keep at least 2 seconds of distance between you and the vehicle ahead in ideal conditions."
        },
        {
            "question_text": "If your car starts to skid, what should you do?",
            "option_a": "Brake hard immediately.",
            "option_b": "Accelerate to gain traction.",
            "option_c": "Steer in the direction you want the front of the vehicle to go.",
            "option_d": "Let go of the steering wheel.",
            "correct_option": "C",
            "explanation": "Ease off the gas and steer in the direction you want to go. Do not slam on the brakes."
        }
    ]

    for q in questions:
        question = models.QuizQuestion(**q)
        db.add(question)
    
    db.commit()
    print(f"Seeded {len(questions)} quiz questions.")
    db.close()

if __name__ == "__main__":
    # Ensure tables exist (including the new one)
    models.Base.metadata.create_all(bind=database.engine)
    seed_quiz_data()
