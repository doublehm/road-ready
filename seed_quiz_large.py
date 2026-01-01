import sys
import os

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.getcwd(), "lib"))

from app import models, database
from sqlalchemy.orm import Session

def seed_large_quiz_data():
    db = database.SessionLocal()
    
    # Clear existing questions
    db.query(models.QuizQuestion).delete()
    db.commit()

    questions = [
        # --- SIGNS & SIGNALS (Chapter 3) ---
        {
            "question_text": "What does a flashing red traffic light mean?",
            "option_a": "Stop, then proceed when safe (like a Stop sign).",
            "option_b": "Slow down and proceed with caution.",
            "option_c": "The traffic light is broken.",
            "option_d": "Stop and wait for the green light.",
            "correct_option": "A",
            "explanation": "A flashing red light acts exactly like a stop sign. (See Chapter 3: Signs, Signals & Road Markings)"
        },
        {
            "question_text": "A yellow diamond-shaped sign with a black cross indicates:",
            "option_a": "Hospital ahead.",
            "option_b": "Intersection ahead.",
            "option_c": "Railway crossing.",
            "option_d": "First aid station.",
            "correct_option": "B",
            "explanation": "Yellow diamond signs are warning signs. A cross indicates an intersection. (See Chapter 3: Signs, Signals & Road Markings)"
        },
        {
            "question_text": "What does a steady yellow traffic light mean?",
            "option_a": "Speed up to clear the intersection.",
            "option_b": "Stop if you can do so safely; otherwise, proceed with caution.",
            "option_c": "Stop immediately, regardless of position.",
            "option_d": "The light will turn red in 5 seconds.",
            "correct_option": "B",
            "explanation": "You must stop if it is safe. If you are too close to stop safely, continue through. (See Chapter 3: Signs, Signals & Road Markings)"
        },
        {
            "question_text": "A round green sign with a white circle around a bicycle means:",
            "option_a": "Bicycles are prohibited.",
            "option_b": "Bicycle crossing ahead.",
            "option_c": "Bicycle lane - bicycles only.",
            "option_d": "Share the road with bicycles.",
            "correct_option": "C",
            "explanation": "A green circle usually indicates a permissive regulation, in this case, a lane reserved for cyclists. (See Chapter 3: Signs, Signals & Road Markings)"
        },
        {
            "question_text": "What does a 'Merge' sign look like?",
            "option_a": "Yellow diamond with two lines converging.",
            "option_b": "Red octagon.",
            "option_c": "White rectangle.",
            "option_d": "Orange triangle.",
            "correct_option": "A",
            "explanation": "Merge signs are warning signs (yellow diamond) showing two paths becoming one. (See Chapter 3: Signs, Signals & Road Markings)"
        },
        
        # --- RIGHT OF WAY (Chapter 4) ---
        {
            "question_text": "At an uncontrolled intersection (no signs or lights), who has the right of way?",
            "option_a": "The vehicle on the left.",
            "option_b": "The vehicle on the right.",
            "option_c": "The vehicle that arrives first.",
            "option_d": "The faster vehicle.",
            "correct_option": "B",
            "explanation": "At uncontrolled intersections, yield to the vehicle on your right. (See Chapter 4: Rules of the Road)"
        },
        {
            "question_text": "When two vehicles arrive at a 4-way stop at the same time, who goes first?",
            "option_a": "The vehicle on the left.",
            "option_b": "The vehicle on the right.",
            "option_c": "The vehicle turning left.",
            "option_d": "The largest vehicle.",
            "correct_option": "B",
            "explanation": "Courtesy dictates the vehicle on the right goes first if arrival is simultaneous. (See Chapter 4: Rules of the Road)"
        },
        {
            "question_text": "When turning left at an intersection, you must yield to:",
            "option_a": "Oncoming traffic and pedestrians.",
            "option_b": "Only pedestrians.",
            "option_c": "Only oncoming traffic.",
            "option_d": "Traffic behind you.",
            "correct_option": "A",
            "explanation": "Left turners must yield to everyone (oncoming cars and pedestrians crossing). (See Chapter 4: Rules of the Road)"
        },
        {
            "question_text": "You are exiting a driveway onto a main road. Who has the right of way?",
            "option_a": "You do, if you signal.",
            "option_b": "Traffic on the main road and pedestrians on the sidewalk.",
            "option_c": "You do, if you sound your horn.",
            "option_d": "The main road traffic only.",
            "correct_option": "B",
            "explanation": "You must stop and yield to all traffic and pedestrians before entering the road. (See Chapter 4: Rules of the Road)"
        },
        
        # --- DRIVING RULES & REGULATIONS (Chapter 2 & 5) ---
        {
            "question_text": "What is the speed limit in a school zone when children are present?",
            "option_a": "50 km/h",
            "option_b": "30 km/h",
            "option_c": "20 km/h",
            "option_d": "40 km/h",
            "correct_option": "B",
            "explanation": "School zones are 30 km/h on school days between 8am-5pm (unless otherwise posted). (See Chapter 5: Seeing and Thinking)"
        },
        {
            "question_text": "What is the legal blood alcohol content (BAC) limit for Learner (L) and Novice (N) drivers?",
            "option_a": "0.05%",
            "option_b": "0.08%",
            "option_c": "0.00% (Zero Tolerance)",
            "option_d": "0.02%",
            "correct_option": "C",
            "explanation": "GLP drivers (L and N) must have zero alcohol in their system. (See Chapter 2: You and Your Vehicle)"
        },
        {
            "question_text": "How far must you park from a fire hydrant?",
            "option_a": "3 meters",
            "option_b": "5 meters",
            "option_c": "6 meters",
            "option_d": "10 meters",
            "correct_option": "B",
            "explanation": "Do not park within 5 meters of a fire hydrant. (See Chapter 4: Rules of the Road - Parking)"
        },
        {
            "question_text": "When are you allowed to pass a school bus?",
            "option_a": "When its red lights are flashing.",
            "option_b": "When its amber lights are flashing.",
            "option_c": "When its lights are off and no children are crossing.",
            "option_d": "Never.",
            "correct_option": "C",
            "explanation": "You cannot pass when red lights are flashing. If lights are off, pass with caution. (See Chapter 4: Rules of the Road)"
        },
        {
            "question_text": "You must dim your high beam headlights when within ___ of an oncoming vehicle.",
            "option_a": "150 meters",
            "option_b": "200 meters",
            "option_c": "100 meters",
            "option_d": "60 meters",
            "correct_option": "A",
            "explanation": "Dim high beams within 150m of an oncoming car. (See Chapter 5: Seeing and Thinking)"
        },
        
        # --- HAZARDS & EMERGENCIES (Chapter 8) ---
        {
            "question_text": "If a tire blows out while driving, you should:",
            "option_a": "Brake hard immediately.",
            "option_b": "Steer firmly and ease off the accelerator.",
            "option_c": "Accelerate to maintain stability.",
            "option_d": "Turn the wheel sharply to the side of the road.",
            "correct_option": "B",
            "explanation": "Avoid braking. Keep a firm grip on the wheel to maintain control, then slow down gently. (See Chapter 8: Emergency Strategies)"
        },
        {
            "question_text": "When driving in fog, you should use:",
            "option_a": "High beam headlights.",
            "option_b": "Low beam headlights.",
            "option_c": "Parking lights only.",
            "option_d": "Hazard lights.",
            "correct_option": "B",
            "explanation": "High beams reflect off fog and reduce visibility. Use low beams. (See Chapter 8: Emergency Strategies)"
        },
        {
            "question_text": "What is 'hydroplaning'?",
            "option_a": "Driving on ice.",
            "option_b": "Tires floating on a layer of water, losing traction.",
            "option_c": "Overheating the engine.",
            "option_d": "Skidding on loose gravel.",
            "correct_option": "B",
            "explanation": "Hydroplaning occurs when water separates tires from the road surface. (See Chapter 8: Emergency Strategies)"
        },
        {
            "question_text": "Why is it dangerous to drive in someone's blind spot?",
            "option_a": "It annoys the other driver.",
            "option_b": "They may not see you if they change lanes.",
            "option_c": "It uses more fuel.",
            "option_d": "It blocks your view ahead.",
            "correct_option": "B",
            "explanation": "Avoid lingering in blind spots; either pass or drop back. (See Chapter 5: Seeing and Thinking)"
        },
        
        # --- GENERAL KNOWLEDGE ---
        {
            "question_text": "Who is responsible for ensuring passengers under 16 are buckled up?",
            "option_a": "The passenger.",
            "option_b": "The driver.",
            "option_c": "The vehicle owner.",
            "option_d": "Their parents (even if not present).",
            "correct_option": "B",
            "explanation": "The driver is legally responsible for all passengers under 16. (See Chapter 2: You and Your Vehicle)"
        },
        {
            "question_text": "A 'Slow Moving Vehicle' sign is:",
            "option_a": "A red circle.",
            "option_b": "An orange triangle with a red border.",
            "option_c": "A yellow square.",
            "option_d": "A blue rectangle.",
            "correct_option": "B",
            "explanation": "Orange triangle indicates vehicles moving less than 40 km/h. (See Chapter 3: Signs, Signals & Road Markings)"
        },
        {
            "question_text": "When approaching a stopped emergency vehicle with flashing lights, you must:",
            "option_a": "Stop.",
            "option_b": "Slow to 70km/h (on hwy) or 40km/h (city) and move over.",
            "option_c": "Speed up to clear the area.",
            "option_d": "Honk to show support.",
            "correct_option": "B",
            "explanation": "The 'Slow Down, Move Over' law requires slowing to 70 (in 80+ zones) or 40 (in <80 zones). (See Chapter 4: Rules of the Road)"
        },
        {
            "question_text": "The best way to check for vehicles in your blind spot is to:",
            "option_a": "Use your side mirrors.",
            "option_b": "Use your rearview mirror.",
            "option_c": "Shoulder check (turn your head).",
            "option_d": "Install a camera.",
            "correct_option": "C",
            "explanation": "Mirrors cannot see everything. You must physically turn your head (shoulder check). (See Chapter 5: Seeing and Thinking)"
        },
        {
            "question_text": "What is the 'reaction time' distance?",
            "option_a": "The distance your car travels while you move your foot to the brake.",
            "option_b": "The distance after braking begins.",
            "option_c": "The total stopping distance.",
            "option_d": "5 meters.",
            "correct_option": "A",
            "explanation": "Reaction distance is how far you travel before you physically apply the brakes. (See Chapter 5: Seeing and Thinking)"
        },
        {
            "question_text": "If a pedestrian is crossing against a red light, you must:",
            "option_a": "Honk and proceed.",
            "option_b": "Yield to them.",
            "option_c": "Drive around them quickly.",
            "option_d": "Shout at them.",
            "correct_option": "B",
            "explanation": "Safety comes first. You must always yield to pedestrians to avoid hitting them. (See Chapter 6: Sharing the Road)"
        },
        {
            "question_text": "On a multi-lane highway, the left lane is primarily for:",
            "option_a": "Cruising.",
            "option_b": "Passing.",
            "option_c": "Trucks only.",
            "option_d": "Driving slowly.",
            "correct_option": "B",
            "explanation": "Keep right except to pass. (See Chapter 4: Rules of the Road)"
        },
        
        # --- MORE SIGNS (Chapter 3) ---
        {
            "question_text": "A white rectangular sign usually indicates:",
            "option_a": "A warning.",
            "option_b": "A regulatory rule (e.g., Speed Limit).",
            "option_c": "Construction.",
            "option_d": "Directions.",
            "correct_option": "B",
            "explanation": "White rectangles state the law (Speed limits, turning restrictions). (See Chapter 3: Signs, Signals & Road Markings)"
        },
        {
            "question_text": "An orange diamond sign indicates:",
            "option_a": "School zone.",
            "option_b": "Construction or road work.",
            "option_c": "Park entrance.",
            "option_d": "Stop ahead.",
            "correct_option": "B",
            "explanation": "Orange is exclusively for construction and maintenance warnings. (See Chapter 3: Signs, Signals & Road Markings)"
        },
        {
            "question_text": "What does a solid yellow line on your side of the road mean?",
            "option_a": "Pass with caution.",
            "option_b": "Do not pass.",
            "option_c": "Passing allowed only during the day.",
            "option_d": "The road is ending.",
            "correct_option": "B",
            "explanation": "Solid lines mean do not cross/pass. (See Chapter 3: Signs, Signals & Road Markings)"
        },
        {
            "question_text": "What does a broken yellow line mean?",
            "option_a": "Passing is permitted if safe.",
            "option_b": "Do not pass.",
            "option_c": "One-way traffic.",
            "option_d": "Merge ahead.",
            "correct_option": "A",
            "explanation": "Broken lines allow passing. (See Chapter 3: Signs, Signals & Road Markings)"
        },
        {
            "question_text": "A pentagon (house) shaped sign indicates:",
            "option_a": "School zone.",
            "option_b": "Hospital.",
            "option_c": "Stop.",
            "option_d": "Yield.",
            "correct_option": "A",
            "explanation": "Pentagons specifically mark school zones and crossings. (See Chapter 3: Signs, Signals & Road Markings)"
        },
        
        # --- MANOEUVRES (Chapter 7) ---
        {
            "question_text": "When parking facing downhill with a curb, turn your wheels:",
            "option_a": "Towards the curb (right).",
            "option_b": "Away from the curb (left).",
            "option_c": "Straight.",
            "option_d": "It doesn't matter.",
            "correct_option": "A",
            "explanation": "Turn wheels right so if the brakes fail, the car rolls into the curb. (See Chapter 7: Personal Strategies)"
        },
        {
            "question_text": "When parking facing uphill with a curb, turn your wheels:",
            "option_a": "Towards the curb (right).",
            "option_b": "Away from the curb (left).",
            "option_c": "Straight.",
            "option_d": "Towards the road.",
            "correct_option": "B",
            "explanation": "Turn left (away). If the car rolls back, the tires hit the curb and stop. (See Chapter 7: Personal Strategies)"
        },
        {
            "question_text": "You must signal your intention to turn at least ___ before the turn.",
            "option_a": "100 meters",
            "option_b": "30 meters",
            "option_c": "10 meters",
            "option_d": "Whenever you feel like it.",
            "correct_option": "A",
            "explanation": "Give sufficient warning; generally 100m or shortly before the intersection. (See Chapter 4: Rules of the Road)"
        },
        {
            "question_text": "Before backing up, you should:",
            "option_a": "Check mirrors only.",
            "option_b": "Honk.",
            "option_c": "Do a 360-degree vision check.",
            "option_d": "Turn on the radio.",
            "correct_option": "C",
            "explanation": "Walk around or look all around (360 check) to ensure no obstacles are behind you. (See Chapter 5: Seeing and Thinking)"
        },
        {
            "question_text": "If you miss your exit on a highway, you should:",
            "option_a": "Stop and back up.",
            "option_b": "Make a U-turn through the median.",
            "option_c": "Continue to the next exit.",
            "option_d": "Drive across the grass.",
            "correct_option": "C",
            "explanation": "Never back up on a highway. Go to the next exit. (See Chapter 4: Rules of the Road)"
        },
        
        # --- CONDITIONS (Chapter 8) ---
        {
            "question_text": "Black ice is:",
            "option_a": "Dirty snow.",
            "option_b": "Frozen oil.",
            "option_c": "Invisible ice on the road surface.",
            "option_d": "Gravel.",
            "correct_option": "C",
            "explanation": "It looks like wet asphalt but is a thin, invisible layer of ice. (See Chapter 8: Emergency Strategies)"
        },
        {
            "question_text": "When driving in heavy rain, your tires can lose contact with the road. This is called:",
            "option_a": "Hydroplaning.",
            "option_b": "Aeroplaning.",
            "option_c": "Skating.",
            "option_d": "Drifting.",
            "correct_option": "A",
            "explanation": "Hydroplaning reduces steering and braking control. (See Chapter 8: Emergency Strategies)"
        },
        {
            "question_text": "Most skids are caused by:",
            "option_a": "Bad tires.",
            "option_b": "Driver error (driving too fast for conditions).",
            "option_c": "Wind.",
            "option_d": "Other drivers.",
            "correct_option": "B",
            "explanation": "Adjusting speed to conditions prevents most skids. (See Chapter 8: Emergency Strategies)"
        },
        {
            "question_text": "If you are feeling tired while driving, the best action is:",
            "option_a": "Turn up the radio.",
            "option_b": "Open the window.",
            "option_c": "Stop and rest.",
            "option_d": "Drink coffee and keep going.",
            "correct_option": "C",
            "explanation": "The only cure for fatigue is sleep. Stop and rest. (See Chapter 2: You and Your Vehicle)"
        },
        {
            "question_text": "Using a handheld electronic device while driving is:",
            "option_a": "Allowed at red lights.",
            "option_b": "Allowed for checking maps.",
            "option_c": "Prohibited.",
            "option_d": "Allowed if you hold it low.",
            "correct_option": "C",
            "explanation": "BC has a ban on handheld devices. Use hands-free or pull over. (See Chapter 2: You and Your Vehicle)"
        },
        
        # --- CYCLISTS & PEDESTRIANS (Chapter 6) ---
        {
            "question_text": "When passing a cyclist, you should leave at least:",
            "option_a": "0.5 meters.",
            "option_b": "1 meter.",
            "option_c": "20 cm.",
            "option_d": "No space needed.",
            "correct_option": "B",
            "explanation": "Leave a safe distance, generally at least 1 meter. (See Chapter 6: Sharing the Road)"
        },
        {
            "question_text": "Shoulder checking means:",
            "option_a": "Looking at your shoulders.",
            "option_b": "Checking side mirrors.",
            "option_c": "Glancing back over your shoulder to check the blind spot.",
            "option_d": "Checking the road shoulder.",
            "correct_option": "C",
            "explanation": "It's the only way to see what mirrors miss. (See Chapter 5: Seeing and Thinking)"
        },
        {
            "question_text": "A white cane indicates:",
            "option_a": "An elderly person.",
            "option_b": "A person with visual impairment.",
            "option_c": "A traffic warden.",
            "option_d": "A hiker.",
            "correct_option": "B",
            "explanation": "Exercise extra caution for visually impaired pedestrians. (See Chapter 6: Sharing the Road)"
        },
        {
            "question_text": "You see a ball roll into the street. You should:",
            "option_a": "Ignore it.",
            "option_b": "Honk.",
            "option_c": "Slow down and prepare to stop; a child may follow.",
            "option_d": "Swerve.",
            "correct_option": "C",
            "explanation": "Predict the hazard: children often follow toys. (See Chapter 5: Seeing and Thinking)"
        },
        {
            "question_text": "Cyclists are entitled to:",
            "option_a": "Use the full lane if necessary.",
            "option_b": "Ride on the sidewalk always.",
            "option_c": "Ignore stop signs.",
            "option_d": "Ride facing traffic.",
            "correct_option": "A",
            "explanation": "Cyclists are vehicles and can take the lane for safety. (See Chapter 6: Sharing the Road)"
        },
        
        # --- INTERSECTIONS (Chapter 4) ---
        {
            "question_text": "A steady red light with a green arrow means:",
            "option_a": "Stop.",
            "option_b": "Proceed only in the direction of the arrow.",
            "option_c": "Turn right only.",
            "option_d": "The light is broken.",
            "correct_option": "B",
            "explanation": "You have the right of way to turn in the arrow's direction. (See Chapter 3: Signs, Signals & Road Markings)"
        },
        {
            "question_text": "Can you turn right on a red light?",
            "option_a": "Never.",
            "option_b": "Always.",
            "option_c": "Yes, after stopping and yielding, unless a sign prohibits it.",
            "option_d": "Only on Sundays.",
            "correct_option": "C",
            "explanation": "Right on red is legal in BC unless posted otherwise. (See Chapter 4: Rules of the Road)"
        },
        {
            "question_text": "Can you turn left on a red light?",
            "option_a": "Never.",
            "option_b": "Only from a one-way street onto another one-way street.",
            "option_c": "Yes, if clear.",
            "option_d": "Only at night.",
            "correct_option": "B",
            "explanation": "This is the only exception for left on red. (See Chapter 4: Rules of the Road)"
        },
        {
            "question_text": "Approaching a stale green light (one that has been green for a while), you should:",
            "option_a": "Speed up.",
            "option_b": "Prepare to stop.",
            "option_c": "Ignore it.",
            "option_d": "Flash your lights.",
            "correct_option": "B",
            "explanation": "It will likely turn yellow soon. Be ready. (See Chapter 3: Signs, Signals & Road Markings)"
        },
        {
            "question_text": "If a traffic light is totally dark (power outage), treat it as:",
            "option_a": "A green light.",
            "option_b": "A yield sign.",
            "option_c": "A 4-way stop.",
            "option_d": "A free-for-all.",
            "correct_option": "C",
            "explanation": "Stop, scan, and proceed when safe (4-way stop rules). (See Chapter 4: Rules of the Road)"
        }
    ]

    for q in questions:
        question = models.QuizQuestion(**q)
        db.add(question)
    
    db.commit()
    print(f"Seeded {len(questions)} quiz questions with references.")
    db.close()

if __name__ == "__main__":
    # Ensure tables exist
    models.Base.metadata.create_all(bind=database.engine)
    seed_large_quiz_data()