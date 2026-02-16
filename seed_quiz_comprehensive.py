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

        # --- CHAPTER 4: RULES OF THE ROAD ---
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
            "question_text": "What does a flashing red light mean?",
            "option_a": "Slow down and proceed with caution.",
            "option_b": "The light is about to turn green.",
            "option_c": "Stop completely and proceed only when safe (like a stop sign).",
            "option_d": "Yield to oncoming traffic.",
            "correct_option": "C",
            "explanation": "A flashing red light means you must come to a complete stop and proceed only when it's safe."
        },
        {
            "category": "Rules of the Road",
            "question_text": "When can you make a right turn on a red light?",
            "option_a": "Never.",
            "option_b": "Only if there is no 'No Right Turn on Red' sign and you have come to a complete stop.",
            "option_c": "Anytime, as long as you slow down.",
            "option_d": "Only during daylight hours.",
            "correct_option": "B",
            "explanation": "Unless a sign prohibits it, you may turn right on a red light after stopping and yielding to traffic and pedestrians."
        },

        # --- CHAPTER 5: SEEING, THINKING & DOING ---
        {
            "category": "Safe Driving",
            "question_text": "What is the recommended following distance behind another vehicle in good weather?",
            "option_a": "One car length.",
            "option_b": "At least two seconds.",
            "option_c": "At least five seconds.",
            "option_d": "10 meters.",
            "correct_option": "B",
            "explanation": "The 'two-second rule' helps you maintain a safe following distance in ideal conditions."
        },
        {
            "category": "Safe Driving",
            "question_text": "How far ahead should you scan in city driving?",
            "option_a": "One block or about 12 seconds ahead.",
            "option_b": "The car directly in front of you.",
            "option_c": "30 seconds ahead.",
            "option_d": "Only at intersections.",
            "correct_option": "A",
            "explanation": "Scanning 12 seconds ahead in the city helps you anticipate hazards like pedestrians and turning vehicles."
        },
        {
            "category": "Safe Driving",
            "question_text": "What is 'space margin'?",
            "option_a": "The time it takes to reach your destination.",
            "option_b": "The area around your vehicle that you keep clear of other objects.",
            "option_c": "The width of your car.",
            "option_d": "The gap between your car and the curb.",
            "correct_option": "B",
            "explanation": "A space margin is a 'safety cushion' that gives you time and space to react to hazards."
        },

        # --- CHAPTER 6: SHARING THE ROAD ---
        {
            "category": "Sharing the Road",
            "question_text": "When a school bus is stopped with its red lights flashing and stop arm out, you must:",
            "option_a": "Slow down and pass carefully.",
            "option_b": "Stop, regardless of which direction you are traveling in (unless separated by a median).",
            "option_c": "Only stop if you are behind the bus.",
            "option_d": "Honk your horn and proceed.",
            "correct_option": "B",
            "explanation": "You must stop for a school bus with flashing red lights to protect children boarding or exiting."
        },
        {
            "category": "Sharing the Road",
            "question_text": "What should you do when an emergency vehicle with sirens and lights is approaching?",
            "option_a": "Speed up to get out of its way.",
            "option_b": "Pull over to the right and stop.",
            "option_c": "Stop exactly where you are.",
            "option_d": "Follow it closely to get through traffic.",
            "correct_option": "B",
            "explanation": "You must clear a path for emergency vehicles by pulling over to the right and stopping."
        },

        # --- CHAPTER 8: EMERGENCY SITUATIONS ---
        {
            "category": "Safe Driving",
            "question_text": "If your vehicle starts to skid, you should:",
            "option_a": "Slam on the brakes.",
            "option_b": "Steer in the direction you want the front of the vehicle to go.",
            "option_c": "Accelerate to gain traction.",
            "option_d": "Turn the wheel sharply in the opposite direction.",
            "correct_option": "B",
            "explanation": "To recover from a skid, look and steer where you want to go. Avoid sudden braking or acceleration."
        },
        {
            "category": "Safe Driving",
            "question_text": "What should you do if your brakes fail while driving?",
            "option_a": "Turn off the ignition.",
            "option_b": "Shift to a lower gear and pump the brake pedal.",
            "option_c": "Jump out of the car.",
            "option_d": "Only use the emergency brake at high speed.",
            "correct_option": "B",
            "explanation": "Downshifting and pumping the brakes can help slow the vehicle. Use the parking brake gently if needed."
        },

        # --- ADDITIONAL HANDBOOK CONTENT ---
        {
            "category": "Rules of the Road",
            "question_text": "When turning left at an intersection, you must yield to:",
            "option_a": "Traffic going straight and pedestrians in your path.",
            "option_b": "Only vehicles coming from the right.",
            "option_c": "Only vehicles coming from the left.",
            "option_d": "No one, you have the right of way.",
            "correct_option": "A",
            "explanation": "Left-turning vehicles must yield to oncoming traffic and pedestrians crossing the street they are entering."
        },
        {
            "category": "Rules of the Road",
            "question_text": "A solid white line between lanes means:",
            "option_a": "You are allowed to change lanes.",
            "option_b": "Lane changing is discouraged or prohibited.",
            "option_c": "It marks the edge of the road.",
            "option_d": "You must stop before the line.",
            "correct_option": "B",
            "explanation": "Solid white lines indicate that lane changes are hazardous or not permitted in that area."
        },
        {
            "category": "Rules of the Road",
            "question_text": "A 'blind spot' is:",
            "option_a": "An area you cannot see in your mirrors.",
            "option_b": "The area directly in front of your car.",
            "option_c": "A type of intersection.",
            "option_d": "An area covered by your high beams.",
            "correct_option": "A",
            "explanation": "Blind spots are areas beside and behind your vehicle that mirrors don't cover. You must shoulder check."
        },
        {
            "category": "Sharing the Road",
            "question_text": "When passing a cyclist, what is the minimum distance you should maintain?",
            "option_a": "0.5 meters.",
            "option_b": "At least 1 meter.",
            "option_c": "At least 3 meters.",
            "option_d": "Cyclists must move for cars.",
            "correct_option": "B",
            "explanation": "Maintaining at least 1 meter of space helps ensure the safety of cyclists when you pass them."
        },
        {
            "category": "Safe Driving",
            "question_text": "If you are being tailgated, you should:",
            "option_a": "Slam on your brakes to scare them.",
            "option_b": "Speed up to create distance.",
            "option_c": "Slow down gradually to encourage them to pass.",
            "option_d": "Stay at the exact same speed and ignore them.",
            "correct_option": "C",
            "explanation": "Gradually slowing down increases your space margin in front and encourages the tailgater to pass."
        },
        {
            "category": "Rules of the Road",
            "question_text": "Who has the right of way at an uncontrolled intersection?",
            "option_a": "The vehicle on the left.",
            "option_b": "The vehicle on the right.",
            "option_c": "The vehicle going faster.",
            "option_d": "The vehicle on the main road.",
            "correct_option": "B",
            "explanation": "At an intersection without signs or signals, you must yield to the vehicle on your right."
        }
    ]

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
    print(f"Seeded {len(questions)} handbook-only questions.")

if __name__ == "__main__":
    seed_comprehensive_quiz()
