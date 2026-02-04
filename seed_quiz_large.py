
import sys
import os

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.getcwd(), "lib"))

from app import models, database
from sqlalchemy.orm import Session

def seed_large_quiz_data():
    db = database.SessionLocal()
    
    # Clear existing questions to avoid duplicates/confusion
    db.query(models.QuizQuestion).delete()
    db.commit()

    questions = [
        # --- SIGNS & SIGNALS ---
        {
            "question_text": "What does a flashing red traffic light mean?",
            "option_a": "Stop, then proceed when safe (like a Stop sign).",
            "option_b": "Slow down and proceed with caution.",
            "option_c": "The traffic light is broken.",
            "option_d": "Stop and wait for the green light.",
            "correct_option": "A",
            "explanation": "A flashing red light acts exactly like a stop sign."
        },
        {
            "question_text": "A yellow diamond-shaped sign with a black cross indicates:",
            "option_a": "Hospital ahead.",
            "option_b": "Intersection ahead.",
            "option_c": "Railway crossing.",
            "option_d": "First aid station.",
            "correct_option": "B",
            "explanation": "Yellow diamond signs are warning signs. A cross indicates an intersection."
        },
        {
            "question_text": "What does a steady yellow traffic light mean?",
            "option_a": "Speed up to clear the intersection.",
            "option_b": "Stop if you can do so safely; otherwise, proceed with caution.",
            "option_c": "Stop immediately, regardless of position.",
            "option_d": "The light will turn red in 5 seconds.",
            "correct_option": "B",
            "explanation": "You must stop if it is safe. If you are too close to stop safely, continue through."
        },
        {
            "question_text": "A round green signal light means:",
            "option_a": "Drivers facing the light may turn left, go straight, or turn right.",
            "option_b": "Drivers must go straight.",
            "option_c": "Drivers can only turn right.",
            "option_d": "Stop and wait for a flashing green light.",
            "correct_option": "A",
            "explanation": "Green means go. You can turn left, go straight, or turn right, unless a sign prohibits it. Yield to pedestrians and oncoming traffic when turning left."
        },
        {
            "question_text": "A flashing green light indicates:",
            "option_a": "The light is about to turn red.",
            "option_b": "A pedestrian-controlled light.",
            "option_c": "You have the right of way to turn left.",
            "option_d": "Stop and proceed when safe.",
            "correct_option": "B",
            "explanation": "In BC, a flashing green light at an intersection means it is pedestrian-controlled. It acts like a regular green light for drivers unless a pedestrian activates the crosswalk."
        },
        {
            "question_text": "What does a 'Yield' sign mean?",
            "option_a": "Stop immediately.",
            "option_b": "Slow down and stop if necessary to let other traffic go.",
            "option_c": "Maintain speed and merge.",
            "option_d": "It applies only to trucks.",
            "correct_option": "B",
            "explanation": "Yield means you must let traffic on the through road go first. Stop if necessary."
        },
        {
            "question_text": "A white rectangular sign with a black arrow curving left indicates:",
            "option_a": "Left turn only lane.",
            "option_b": "Curve ahead.",
            "option_c": "Keep left.",
            "option_d": "No left turn.",
            "correct_option": "A",
            "explanation": "Rectangular white signs with black markings are regulatory signs. An arrow indicates the allowed lane movement."
        },
        {
            "question_text": "What does a sign with a red circle and a slash over a U-turn symbol mean?",
            "option_a": "U-turns allowed.",
            "option_b": "U-turn permitted after stopping.",
            "option_c": "No U-turns allowed.",
            "option_d": "U-turn route ahead.",
            "correct_option": "C",
            "explanation": "A red circle with a slash is a prohibition sign. It means the action shown is forbidden."
        },
        {
            "question_text": "A pentagon-shaped sign (shaped like a school house) means:",
            "option_a": "School zone or school crosswalk.",
            "option_b": "Playground zone.",
            "option_c": "Construction zone.",
            "option_d": "Hospital zone.",
            "correct_option": "A",
            "explanation": "The pentagon shape is exclusively used for school zones and school crosswalks."
        },
        {
            "question_text": "An orange diamond-shaped sign indicates:",
            "option_a": "School zone.",
            "option_b": "Construction or road maintenance ahead.",
            "option_c": "Hazardous materials.",
            "option_d": "Scenic route.",
            "correct_option": "B",
            "explanation": "Orange signs are used for temporary conditions like construction and road maintenance."
        },
        {
            "question_text": "A round green sign with a white circle around a bicycle means:",
            "option_a": "Bicycles are prohibited.",
            "option_b": "Bicycle crossing ahead.",
            "option_c": "Bicycle lane - bicycles only.",
            "option_d": "Share the road with bicycles.",
            "correct_option": "C",
            "explanation": "A green circle usually indicates a permissive regulation, in this case, a lane reserved for cyclists."
        },
        {
            "question_text": "What does a 'Merge' sign look like?",
            "option_a": "Yellow diamond with two lines converging.",
            "option_b": "Red octagon.",
            "option_c": "White rectangle.",
            "option_d": "Orange triangle.",
            "correct_option": "A",
            "explanation": "Merge signs are warning signs (yellow diamond) showing two paths becoming one."
        },
        
        # --- RIGHT OF WAY ---
        {
            "question_text": "At an uncontrolled intersection (no signs or lights), who has the right of way?",
            "option_a": "The vehicle on the left.",
            "option_b": "The vehicle on the right.",
            "option_c": "The vehicle that arrives first.",
            "option_d": "The faster vehicle.",
            "correct_option": "B",
            "explanation": "At uncontrolled intersections, yield to the vehicle on your right."
        },
        {
            "question_text": "When two vehicles arrive at a 4-way stop at the same time, who goes first?",
            "option_a": "The vehicle on the left.",
            "option_b": "The vehicle on the right.",
            "option_c": "The vehicle turning left.",
            "option_d": "The largest vehicle.",
            "correct_option": "B",
            "explanation": "Courtesy dictates the vehicle on the right goes first if arrival is simultaneous."
        },
        {
            "question_text": "When turning left at an intersection, you must yield to:",
            "option_a": "Oncoming traffic and pedestrians.",
            "option_b": "Only pedestrians.",
            "option_c": "Only oncoming traffic.",
            "option_d": "Traffic behind you.",
            "correct_option": "A",
            "explanation": "Left turners must yield to everyone (oncoming cars and pedestrians crossing)."
        },
        {
            "question_text": "You are exiting a driveway onto a main road. Who has the right of way?",
            "option_a": "You do, if you signal.",
            "option_b": "Traffic on the main road and pedestrians on the sidewalk.",
            "option_c": "You do, if you sound your horn.",
            "option_d": "The main road traffic only.",
            "correct_option": "B",
            "explanation": "You must stop and yield to all traffic and pedestrians before entering the road."
        },
        
        # --- DRIVING RULES & REGULATIONS ---
        {
            "question_text": "When a school bus displays flashing red lights and the stop arm is extended, you must:",
            "option_a": "Slow down and pass with caution.",
            "option_b": "Stop at least 5 meters away.",
            "option_c": "Stop only if you are behind the bus.",
            "option_d": "Stop regardless of your direction of travel.",
            "correct_option": "D",
            "explanation": "You must stop whether approaching from the front or rear, unless on a divided highway with a physical barrier."
        },
        {
            "question_text": "Unless otherwise posted, the speed limit in a city or municipality is:",
            "option_a": "30 km/h",
            "option_b": "50 km/h",
            "option_c": "60 km/h",
            "option_d": "80 km/h",
            "correct_option": "B",
            "explanation": "The default speed limit in urban areas (cities, towns) is 50 km/h unless signs say otherwise."
        },
        {
            "question_text": "Unless otherwise posted, the speed limit outside a city or municipality is:",
            "option_a": "50 km/h",
            "option_b": "60 km/h",
            "option_c": "80 km/h",
            "option_d": "100 km/h",
            "correct_option": "C",
            "explanation": "On rural roads and highways outside municipalities, the default limit is 80 km/h."
        },
        {
            "question_text": "When are you allowed to make a U-turn?",
            "option_a": "On a curve where you can be seen by other drivers.",
            "option_b": "At any intersection with a traffic light.",
            "option_c": "If safe and not prohibited by signs or local bylaws.",
            "option_d": "On the crest of a hill.",
            "correct_option": "C",
            "explanation": "U-turns are generally permitted if they can be done safely and without interfering with traffic, and no signs prohibit them. They are illegal on curves, hill crests, and usually at signalized intersections."
        },
        {
            "question_text": "What is the speed limit in a school zone when children are present?",
            "option_a": "50 km/h",
            "option_b": "30 km/h",
            "option_c": "20 km/h",
            "option_d": "40 km/h",
            "correct_option": "B",
            "explanation": "School zones are 30 km/h on school days between 8am-5pm (unless otherwise posted)."
        },
        {
            "question_text": "What is the legal blood alcohol content (BAC) limit for Learner (L) and Novice (N) drivers?",
            "option_a": "0.05%",
            "option_b": "0.08%",
            "option_c": "0.00% (Zero Tolerance)",
            "option_d": "0.02%",
            "correct_option": "C",
            "explanation": "GLP drivers (L and N) must have zero alcohol in their system."
        },
        {
            "question_text": "When merging onto a highway, you should:",
            "option_a": "Stop and wait for a gap.",
            "option_b": "Slow down to 30 km/h.",
            "option_c": "Accelerate to match the speed of highway traffic.",
            "option_d": "Honk to let others know you are coming.",
            "correct_option": "C",
            "explanation": "Use the acceleration lane to match the speed of traffic before merging."
        },
        {
            "question_text": "You are driving in the left lane of a highway. A faster car is approaching from behind. You should:",
            "option_a": "Speed up to match their speed.",
            "option_b": "Tap your brakes to warn them.",
            "option_c": "Move to the right lane when safe.",
            "option_d": "Stay in your lane; you are driving the speed limit.",
            "correct_option": "C",
            "explanation": "The left lane is for passing. If you are blocking traffic, move to the right."
        },
        {
            "question_text": "How far must you park from a fire hydrant?",
            "option_a": "3 meters",
            "option_b": "5 meters",
            "option_c": "6 meters",
            "option_d": "10 meters",
            "correct_option": "B",
            "explanation": "Do not park within 5 meters of a fire hydrant."
        },
        {
            "question_text": "When are you allowed to pass a school bus?",
            "option_a": "When its red lights are flashing.",
            "option_b": "When its amber lights are flashing.",
            "option_c": "When its lights are off and no children are crossing.",
            "option_d": "Never.",
            "correct_option": "C",
            "explanation": "You cannot pass when red lights are flashing. If lights are off, pass with caution."
        },
        {
            "question_text": "You must dim your high beam headlights when within ___ of an oncoming vehicle.",
            "option_a": "150 meters",
            "option_b": "200 meters",
            "option_c": "100 meters",
            "option_d": "60 meters",
            "correct_option": "A",
            "explanation": "Dim high beams within 150m of an oncoming car."
        },
        
        # --- HAZARDS & EMERGENCIES ---
        {
            "question_text": "If a tire blows out while driving, you should:",
            "option_a": "Brake hard immediately.",
            "option_b": "Steer firmly and ease off the accelerator.",
            "option_c": "Accelerate to maintain stability.",
            "option_d": "Turn the wheel sharply to the side of the road.",
            "correct_option": "B",
            "explanation": "Avoid braking. Keep a firm grip on the wheel to maintain control, then slow down gently."
        },
        {
            "question_text": "If your vehicle begins to skid, you should:",
            "option_a": "Brake hard.",
            "option_b": "Accelerate.",
            "option_c": "Steer in the direction you want the vehicle to go.",
            "option_d": "Let go of the steering wheel.",
            "correct_option": "C",
            "explanation": "Ease off the gas and steer in the direction you want the front of the car to go. Do not slam on the brakes."
        },
        {
            "question_text": "When driving in fog, you should use:",
            "option_a": "High beam headlights.",
            "option_b": "Low beam headlights.",
            "option_c": "Parking lights only.",
            "option_d": "Hazard lights.",
            "correct_option": "B",
            "explanation": "High beams reflect off fog and reduce visibility. Use low beams."
        },
        {
            "question_text": "What is 'hydroplaning'?",
            "option_a": "Driving on ice.",
            "option_b": "Tires floating on a layer of water, losing traction.",
            "option_c": "Overheating the engine.",
            "option_d": "Skidding on loose gravel.",
            "correct_option": "B",
            "explanation": "Hydroplaning occurs when water separates tires from the road surface."
        },
        {
            "question_text": "Why is it dangerous to drive in someone's blind spot?",
            "option_a": "It annoys the other driver.",
            "option_b": "They may not see you if they change lanes.",
            "option_c": "It uses more fuel.",
            "option_d": "It blocks your view ahead.",
            "correct_option": "B",
            "explanation": "Avoid lingering in blind spots; either pass or drop back."
        },
        
        # --- GENERAL KNOWLEDGE ---
        {
            "question_text": "Who is responsible for ensuring passengers under 16 are buckled up?",
            "option_a": "The passenger.",
            "option_b": "The driver.",
            "option_c": "The vehicle owner.",
            "option_d": "Their parents (even if not present).",
            "correct_option": "B",
            "explanation": "The driver is legally responsible for all passengers under 16."
        },
        {
            "question_text": "A 'Slow Moving Vehicle' sign is:",
            "option_a": "A red circle.",
            "option_b": "An orange triangle with a red border.",
            "option_c": "A yellow square.",
            "option_d": "A blue rectangle.",
            "correct_option": "B",
            "explanation": "Orange triangle indicates vehicles moving less than 40 km/h."
        },
        {
            "question_text": "When approaching a stopped emergency vehicle with flashing lights, you must:",
            "option_a": "Stop.",
            "option_b": "Slow to 70km/h (on hwy) or 40km/h (city) and move over.",
            "option_c": "Speed up to clear the area.",
            "option_d": "Honk to show support.",
            "correct_option": "B",
            "explanation": "The 'Slow Down, Move Over' law requires slowing to 70 (in 80+ zones) or 40 (in <80 zones)."
        },
        {
            "question_text": "The best way to check for vehicles in your blind spot is to:",
            "option_a": "Use your side mirrors.",
            "option_b": "Use your rearview mirror.",
            "option_c": "Shoulder check (turn your head).",
            "option_d": "Install a camera.",
            "correct_option": "C",
            "explanation": "Mirrors cannot see everything. You must physically turn your head (shoulder check)."
        },
        {
            "question_text": "What is the 'reaction time' distance?",
            "option_a": "The distance your car travels while you move your foot to the brake.",
            "option_b": "The distance after braking begins.",
            "option_c": "The total stopping distance.",
            "option_d": "5 meters.",
            "correct_option": "A",
            "explanation": "Reaction distance is how far you travel before you physically apply the brakes."
        },
        {
            "question_text": "If a pedestrian is crossing against a red light, you must:",
            "option_a": "Honk and proceed.",
            "option_b": "Yield to them.",
            "option_c": "Drive around them quickly.",
            "option_d": "Shout at them.",
            "correct_option": "B",
            "explanation": "Safety comes first. You must always yield to pedestrians to avoid hitting them."
        },
        {
            "question_text": "On a multi-lane highway, the left lane is primarily for:",
            "option_a": "Cruising.",
            "option_b": "Passing.",
            "option_c": "Trucks only.",
            "option_d": "Driving slowly.",
            "correct_option": "B",
            "explanation": "Keep right except to pass."
        },
        
        # --- MORE SIGNS ---
        {
            "question_text": "A white rectangular sign usually indicates:",
            "option_a": "A warning.",
            "option_b": "A regulatory rule (e.g., Speed Limit).",
            "option_c": "Construction.",
            "option_d": "Directions.",
            "correct_option": "B",
            "explanation": "White rectangles state the law (Speed limits, turning restrictions)."
        },
        {
            "question_text": "An orange diamond sign indicates:",
            "option_a": "School zone.",
            "option_b": "Construction or road work.",
            "option_c": "Park entrance.",
            "option_d": "Stop ahead.",
            "correct_option": "B",
            "explanation": "Orange is exclusively for construction and maintenance warnings."
        },
        {
            "question_text": "What does a solid yellow line on your side of the road mean?",
            "option_a": "Pass with caution.",
            "option_b": "Do not pass.",
            "option_c": "Passing allowed only during the day.",
            "option_d": "The road is ending.",
            "correct_option": "B",
            "explanation": "Solid lines mean do not cross/pass."
        },
        {
            "question_text": "What does a broken yellow line mean?",
            "option_a": "Passing is permitted if safe.",
            "option_b": "Do not pass.",
            "option_c": "One-way traffic.",
            "option_d": "Merge ahead.",
            "correct_option": "A",
            "explanation": "Broken lines allow passing."
        },
        {
            "question_text": "A pentagon (house) shaped sign indicates:",
            "option_a": "School zone.",
            "option_b": "Hospital.",
            "option_c": "Stop.",
            "option_d": "Yield.",
            "correct_option": "A",
            "explanation": "Pentagons specifically mark school zones and crossings."
        },
        
        # --- MANOEUVRES ---
        {
            "question_text": "When parking facing downhill with a curb, turn your wheels:",
            "option_a": "Towards the curb (right).",
            "option_b": "Away from the curb (left).",
            "option_c": "Straight.",
            "option_d": "It doesn't matter.",
            "correct_option": "A",
            "explanation": "Turn wheels right so if the brakes fail, the car rolls into the curb."
        },
        {
            "question_text": "When parking facing uphill with a curb, turn your wheels:",
            "option_a": "Towards the curb (right).",
            "option_b": "Away from the curb (left).",
            "option_c": "Straight.",
            "option_d": "Towards the road.",
            "correct_option": "B",
            "explanation": "Turn left (away). If the car rolls back, the tires hit the curb and stop."
        },
        {
            "question_text": "You must signal your intention to turn at least ___ before the turn.",
            "option_a": "100 meters",
            "option_b": "30 meters",
            "option_c": "10 meters",
            "option_d": "Whenever you feel like it.",
            "correct_option": "A",
            "explanation": "Give sufficient warning; generally 100m or shortly before the intersection."
        },
        {
            "question_text": "Before backing up, you should:",
            "option_a": "Check mirrors only.",
            "option_b": "Honk.",
            "option_c": "Do a 360-degree vision check.",
            "option_d": "Turn on the radio.",
            "correct_option": "C",
            "explanation": "Walk around or look all around (360 check) to ensure no obstacles are behind you."
        },
        {
            "question_text": "If you miss your exit on a highway, you should:",
            "option_a": "Stop and back up.",
            "option_b": "Make a U-turn through the median.",
            "option_c": "Continue to the next exit.",
            "option_d": "Drive across the grass.",
            "correct_option": "C",
            "explanation": "Never back up on a highway. Go to the next exit."
        },
        
        # --- CONDITIONS ---
        {
            "question_text": "Black ice is:",
            "option_a": "Dirty snow.",
            "option_b": "Frozen oil.",
            "option_c": "Invisible ice on the road surface.",
            "option_d": "Gravel.",
            "correct_option": "C",
            "explanation": "It looks like wet asphalt but is a thin, invisible layer of ice."
        },
        {
            "question_text": "When driving in heavy rain, your tires can lose contact with the road. This is called:",
            "option_a": "Hydroplaning.",
            "option_b": "Aeroplaning.",
            "option_c": "Skating.",
            "option_d": "Drifting.",
            "correct_option": "A",
            "explanation": "Hydroplaning reduces steering and braking control."
        },
        {
            "question_text": "Most skids are caused by:",
            "option_a": "Bad tires.",
            "option_b": "Driver error (driving too fast for conditions).",
            "option_c": "Wind.",
            "option_d": "Other drivers.",
            "correct_option": "B",
            "explanation": "Adjusting speed to conditions prevents most skids."
        },
        {
            "question_text": "If you are feeling tired while driving, the best action is:",
            "option_a": "Turn up the radio.",
            "option_b": "Open the window.",
            "option_c": "Stop and rest.",
            "option_d": "Drink coffee and keep going.",
            "correct_option": "C",
            "explanation": "The only cure for fatigue is sleep. Stop and rest."
        },
        {
            "question_text": "Using a handheld electronic device while driving is:",
            "option_a": "Allowed at red lights.",
            "option_b": "Allowed for checking maps.",
            "option_c": "Prohibited.",
            "option_d": "Allowed if you hold it low.",
            "correct_option": "C",
            "explanation": "BC has a ban on handheld devices. Use hands-free or pull over."
        },
        
        # --- CYCLISTS & PEDESTRIANS ---
        {
            "question_text": "When passing a cyclist, you should leave at least:",
            "option_a": "0.5 meters.",
            "option_b": "1 meter.",
            "option_c": "20 cm.",
            "option_d": "No space needed.",
            "correct_option": "B",
            "explanation": "Leave a safe distance, generally at least 1 meter."
        },
        {
            "question_text": "Shoulder checking means:",
            "option_a": "Looking at your shoulders.",
            "option_b": "Checking side mirrors.",
            "option_c": "Glancing back over your shoulder to check the blind spot.",
            "option_d": "Checking the road shoulder.",
            "correct_option": "C",
            "explanation": "It's the only way to see what mirrors miss."
        },
        {
            "question_text": "A white cane indicates:",
            "option_a": "An elderly person.",
            "option_b": "A person with visual impairment.",
            "option_c": "A traffic warden.",
            "option_d": "A hiker.",
            "correct_option": "B",
            "explanation": "Exercise extra caution for visually impaired pedestrians."
        },
        {
            "question_text": "You see a ball roll into the street. You should:",
            "option_a": "Ignore it.",
            "option_b": "Honk.",
            "option_c": "Slow down and prepare to stop; a child may follow.",
            "option_d": "Swerve.",
            "correct_option": "C",
            "explanation": "Predict the hazard: children often follow toys."
        },
        {
            "question_text": "Cyclists are entitled to:",
            "option_a": "Use the full lane if necessary.",
            "option_b": "Ride on the sidewalk always.",
            "option_c": "Ignore stop signs.",
            "option_d": "Ride facing traffic.",
            "correct_option": "A",
            "explanation": "Cyclists are vehicles and can take the lane for safety."
        },
        
        # --- INTERSECTIONS ---
        {
            "question_text": "A steady red light with a green arrow means:",
            "option_a": "Stop.",
            "option_b": "Proceed only in the direction of the arrow.",
            "option_c": "Turn right only.",
            "option_d": "The light is broken.",
            "correct_option": "B",
            "explanation": "You have the right of way to turn in the arrow's direction."
        },
        {
            "question_text": "Can you turn right on a red light?",
            "option_a": "Never.",
            "option_b": "Always.",
            "option_c": "Yes, after stopping and yielding, unless a sign prohibits it.",
            "option_d": "Only on Sundays.",
            "correct_option": "C",
            "explanation": "Right on red is legal in BC unless posted otherwise."
        },
        {
            "question_text": "Can you turn left on a red light?",
            "option_a": "Never.",
            "option_b": "Only from a one-way street onto another one-way street.",
            "option_c": "Yes, if clear.",
            "option_d": "Only at night.",
            "correct_option": "B",
            "explanation": "This is the only exception for left on red."
        },
        {
            "question_text": "Approaching a stale green light (one that has been green for a while), you should:",
            "option_a": "Speed up.",
            "option_b": "Prepare to stop.",
            "option_c": "Ignore it.",
            "option_d": "Flash your lights.",
            "correct_option": "B",
            "explanation": "It will likely turn yellow soon. Be ready."
        },
        {
            "question_text": "If a traffic light is totally dark (power outage), treat it as:",
            "option_a": "A green light.",
            "option_b": "A yield sign.",
            "option_c": "A 4-way stop.",
            "option_d": "A free-for-all.",
            "correct_option": "C",
            "explanation": "Stop, scan, and proceed when safe (4-way stop rules)."
        }
    ]

    for q in questions:
        question = models.QuizQuestion(**q)
        db.add(question)
    
    db.commit()
    print(f"Seeded {len(questions)} quiz questions.")
    db.close()

if __name__ == "__main__":
    # Ensure tables exist
    models.Base.metadata.create_all(bind=database.engine)
    seed_large_quiz_data()
