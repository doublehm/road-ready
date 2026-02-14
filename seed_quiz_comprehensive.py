import sys
import os
import json

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.getcwd(), "lib"))

from app import models, database
from sqlalchemy.orm import Session

def seed_comprehensive_quiz():
    db = database.SessionLocal()
    
    print("Clearing existing questions...")
    db.query(models.QuizQuestion).delete()
    db.commit()

    questions = [
        # --- CHAPTER 3: SIGNS, SIGNALS & MARKINGS ---
        {
            "category": "Road Signs",
            "question_text": "What does this sign mean?",
            "image_path": "handbook_images/p41_draw_23.png",
            "option_a": "Stop completely and continue only when safe.",
            "option_b": "Slow down and yield to others.",
            "option_c": "Speed up to clear the intersection.",
            "option_d": "No entry allowed.",
            "correct_option": "A",
            "explanation": "This is a stop sign. You must come to a complete stop and wait until it's safe to proceed."
        },
        {
            "category": "Road Signs",
            "question_text": "What should you do when you see this sign?",
            "image_path": "handbook_images/p41_draw_24.png",
            "option_a": "Stop and wait for a green light.",
            "option_b": "Give the right-of-way to other vehicles and pedestrians.",
            "option_c": "Keep going at the same speed.",
            "option_d": "Turn right immediately.",
            "correct_option": "B",
            "explanation": "This is a yield sign. You must let others go first if they are in the intersection."
        },
        {
            "category": "Road Signs",
            "question_text": "In this school zone, what is the speed limit when children are present?",
            "image_path": "handbook_images/p42_draw_32.png",
            "option_a": "50 km/h",
            "option_b": "40 km/h",
            "option_c": "30 km/h",
            "option_d": "20 km/h",
            "correct_option": "C",
            "explanation": "Unless otherwise posted, the speed limit in school zones is 30 km/h when children are present."
        },

        # --- CHAPTER 4: RULES OF THE ROAD (SITUATIONAL) ---
        {
            "category": "Rules of the Road",
            "question_text": "In this four-way stop situation, which vehicle should go first?",
            "image_path": "handbook_images/p15_draw_145.png",
            "option_a": "The vehicle that arrived first and stopped completely.",
            "option_b": "The vehicle on the left.",
            "option_c": "The larger vehicle.",
            "option_d": "The vehicle going straight.",
            "correct_option": "A",
            "explanation": "At a four-way stop, the first vehicle to arrive and stop should go first."
        },
        {
            "category": "Rules of the Road",
            "question_text": "You are at a two-way stop. The blue car wants to turn left and the yellow car wants to go straight. Who must yield?",
            "image_path": "handbook_images/p56_draw_120.png",
            "option_a": "The yellow car.",
            "option_b": "The blue car.",
            "option_c": "Neither, they can both go.",
            "option_d": "Whoever honks first.",
            "correct_option": "B",
            "explanation": "At a two-way stop, the driver wanting to turn left must yield to traffic going straight."
        },
        {
            "category": "Rules of the Road",
            "question_text": "What is the correct way to enter this roundabout?",
            "image_path": "handbook_images/p59_draw_131.png",
            "option_a": "Speed up to merge quickly.",
            "option_b": "Slow down and yield to traffic already in the circle.",
            "option_c": "Stop and wait for a green light.",
            "option_d": "Turn left into the circle.",
            "correct_option": "B",
            "explanation": "You must slow down and yield to traffic already inside the roundabout."
        },

        # --- LEGAL & STARTUP (FROM PODCAST) ---
        {
            "category": "Business & Legal",
            "question_text": "Why is incorporation as a B.C. Limited Company recommended for a driving school?",
            "option_a": "To avoid paying any taxes.",
            "option_b": "To provide limited liability protection for shareholders.",
            "option_c": "Because it's required by ICBC.",
            "option_d": "To make the cars drive faster.",
            "correct_option": "B",
            "explanation": "Incorporation creates a separate legal entity, protecting the owner's personal assets from business liabilities."
        },
        {
            "category": "Business & Legal",
            "question_text": "When must a B.C. business register for a GST number?",
            "option_a": "Immediately upon starting.",
            "option_b": "Once annual revenue exceeds $30,000.",
            "option_c": "Only if they have employees.",
            "option_d": "Never, driving schools are exempt.",
            "correct_option": "B",
            "explanation": "Businesses in Canada must register for GST once their gross revenue exceeds $30,000 in a 12-month period."
        },
        {
            "category": "Business & Legal",
            "question_text": "Is WorkSafeBC coverage mandatory for a driving school with instructors?",
            "option_a": "Yes, it is mandatory to ensure health and safety coverage.",
            "option_b": "No, it's optional.",
            "option_c": "Only if the instructors drive their own cars.",
            "option_d": "Only for schools with more than 10 instructors.",
            "correct_option": "A",
            "explanation": "WorkSafeBC registration is mandatory for most B.C. businesses that hire workers or contractors."
        },

        # --- SAFETY (NEW MANDATE) ---
        {
            "category": "Safety",
            "question_text": "When should a student driver operate the Road Ready app?",
            "option_a": "While stopped at a red light.",
            "option_b": "Never while driving; the supervisor should handle all app interactions.",
            "option_c": "To check their speed during the diagnostic ride.",
            "option_d": "Only when using a hands-free mount.",
            "correct_option": "B",
            "explanation": "Safety first! The student must focus on the road. All app interactions during a ride must be handled by the supervisor in the passenger seat."
        }
    ]

    # Add more general questions to reach a decent number for now
    # (Expanding to 50 for the prototype test)
    for i in range(40):
        questions.append({
            "category": "General Knowledge",
            "question_text": f"Safe driving includes which of the following? (Sample Question {i+1})",
            "option_a": "Scanning 12 seconds ahead.",
            "option_b": "Driving as fast as possible.",
            "option_c": "Tailgating to save fuel.",
            "option_d": "Using your phone while driving.",
            "correct_option": "A",
            "explanation": "Looking well ahead allows you to predict hazards and react in time."
        })

    for q in questions:
        db_q = models.QuizQuestion(
            question_text=q["question_text"],
            option_a=q["option_a"],
            option_b=q["option_b"],
            option_c=q["option_c"],
            option_d=q["option_d"],
            correct_option=q["correct_option"],
            explanation=q.get("explanation"),
            category=q.get("category"),
            image_path=q.get("image_path")
        )
        db.add(db_q)
    
    db.commit()
    print(f"Seeded {len(questions)} comprehensive questions.")

if __name__ == "__main__":
    seed_comprehensive_quiz()
