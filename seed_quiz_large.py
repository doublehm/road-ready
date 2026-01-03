
import sys
import os

# Add local lib directory to sys.path
sys.path.append(os.path.join(os.getcwd(), "lib"))

from app import models, database
from sqlalchemy.orm import Session

def seed_large_quiz_data():
    # Ensure tables exist
    models.Base.metadata.create_all(bind=database.engine)
    
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
            "question_text": "A flashing green light indicates:",
            "option_a": "It is safe to turn left.",
            "option_b": "The light is controlled by pedestrians.",
            "option_c": "Emergency vehicles are approaching.",
            "option_d": "Construction zone ahead.",
            "correct_option": "B",
            "explanation": "A flashing green light is a pedestrian-controlled light. Proceed if the intersection is clear."
        },
        {
            "question_text": "A round green signal light indicates:",
            "option_a": "You have the right of way over all other traffic.",
            "option_b": "Turn left immediately.",
            "option_c": "You may proceed straight through or turn right/left, unless a sign prohibits it.",
            "option_d": "Stop and wait.",
            "correct_option": "C",
            "explanation": "Green means go, but you must still yield to pedestrians and vehicles already in the intersection."
        },
        {
            "question_text": "A white rectangular sign with a black arrow turning left with a red circle and slash means:",
            "option_a": "Left turn permitted.",
            "option_b": "No left turn.",
            "option_c": "Merge left.",
            "option_d": "Curve ahead.",
            "correct_option": "B",
            "explanation": "A red circle with a slash always means the action is prohibited."
        },
        {
            "question_text": "What does a 'Yield' sign mean?",
            "option_a": "Stop only if there is a police officer.",
            "option_b": "Slow down or stop to let other traffic go first.",
            "option_c": "Maintain speed and merge.",
            "option_d": "Stop for at least 3 seconds.",
            "correct_option": "B",
            "explanation": "Yield means you must give the right-of-way to traffic on the intersecting road."
        },
        {
            "question_text": "A sign showing a truck on a steep hill means:",
            "option_a": "Trucks only.",
            "option_b": "Steep hill ahead, check brakes.",
            "option_c": "Runaway lane.",
            "option_d": "Construction vehicles crossing.",
            "correct_option": "B",
            "explanation": "This warns of a steep grade ahead."
        },
        {
            "question_text": "Construction zone signs are generally what color?",
            "option_a": "Yellow.",
            "option_b": "Orange.",
            "option_c": "Green.",
            "option_d": "Blue.",
            "correct_option": "B",
            "explanation": "Orange is the standard color for construction and maintenance warning signs."
        },
        {
            "question_text": "A slow-moving vehicle sign is:",
            "option_a": "A red circle.",
            "option_b": "An orange triangle with a red border.",
            "option_c": "A yellow square.",
            "option_d": "A blue rectangle.",
            "correct_option": "B",
            "explanation": "This sign is placed on the back of vehicles traveling less than 40 km/h."
        },

        # --- RIGHT OF WAY ---
        {
            "question_text": "At a 4-way stop, who goes first?",
            "option_a": "The vehicle that is biggest.",
            "option_b": "The vehicle that arrived first.",
            "option_c": "The vehicle to the left.",
            "option_d": "The vehicle turning right.",
            "correct_option": "B",
            "explanation": "First to arrive, first to go. If two arrive at the same time, yield to the right."
        },
        {
            "question_text": "If two vehicles arrive at a 4-way stop at the same time:",
            "option_a": "The vehicle on the left yields to the vehicle on the right.",
            "option_b": "The vehicle on the right yields to the vehicle on the left.",
            "option_c": "The faster vehicle goes first.",
            "option_d": "Signal and go.",
            "correct_option": "A",
            "explanation": "Yield to the vehicle on your right."
        },
        {
            "question_text": "When entering a traffic circle (roundabout):",
            "option_a": "Yield to traffic already in the circle.",
            "option_b": "Enter immediately; they must stop for you.",
            "option_c": "Stop and wait for a green light.",
            "option_d": "Speed up to merge.",
            "correct_option": "A",
            "explanation": "Traffic inside the circle has the right of way."
        },
        {
            "question_text": "When a school bus has its red lights flashing and stop arm extended:",
            "option_a": "You may pass if you honk.",
            "option_b": "You must stop in both directions (unless divided by a median).",
            "option_c": "Slow down to 20 km/h.",
            "option_d": "Stop only if you are behind it.",
            "correct_option": "B",
            "explanation": "You must stop to ensure children can cross safely."
        },
        {
            "question_text": "You are backing out of a driveway. You must:",
            "option_a": "Honk and back out quickly.",
            "option_b": "Yield to all traffic and pedestrians on the road.",
            "option_c": "Back out halfway and wait.",
            "option_d": "Expect others to stop for you.",
            "correct_option": "B",
            "explanation": "Vehicles entering a road from a private driveway must yield to all traffic."
        },
        {
            "question_text": "When turning left, you must yield to:",
            "option_a": "Oncoming traffic and pedestrians.",
            "option_b": "Only pedestrians.",
            "option_c": "Only oncoming trucks.",
            "option_d": "Traffic behind you.",
            "correct_option": "A",
            "explanation": "Left turners must yield to oncoming traffic and pedestrians crossing the street."
        },
        {
            "question_text": "If you hear an emergency vehicle siren but cannot see it:",
            "option_a": "Speed up to clear the road.",
            "option_b": "Stop immediately in your lane.",
            "option_c": "Pull over to the nearest curb and stop.",
            "option_d": "Slow down and keep driving.",
            "correct_option": "C",
            "explanation": "Pull over safely to the right (or nearest curb) and stop until it passes."
        },
        {
            "question_text": "A pedestrian is crossing at an unmarked crosswalk. You must:",
            "option_a": "Honk to warn them.",
            "option_b": "Yield the right-of-way.",
            "option_c": "Drive around them.",
            "option_d": "Speed up to pass before they enter your lane.",
            "correct_option": "B",
            "explanation": "Pedestrians have the right of way at all intersections, marked or unmarked."
        },
        {
            "question_text": "When a funeral procession is passing:",
            "option_a": "Join the procession.",
            "option_b": "Yield the right-of-way to the procession.",
            "option_c": "Pass them on the right.",
            "option_d": "Honk your horn.",
            "correct_option": "B",
            "explanation": "It is courteous and often required to yield to funeral processions."
        },
        {
            "question_text": "If a transit bus signals to re-enter traffic:",
            "option_a": "Speed up to pass.",
            "option_b": "Yield and let it in (in speed zones of 60 km/h or less).",
            "option_c": "Ignore the signal.",
            "option_d": "Honk.",
            "correct_option": "B",
            "explanation": "You must yield to BC Transit buses signaling to leave a stop in areas where the speed limit is 60 km/h or lower."
        },

        # --- RULES OF THE ROAD ---
        {
            "question_text": "Unless otherwise posted, the speed limit in cities and towns is:",
            "option_a": "30 km/h",
            "option_b": "50 km/h",
            "option_c": "60 km/h",
            "option_d": "80 km/h",
            "correct_option": "B",
            "explanation": "The default speed limit in urban areas is 50 km/h."
        },
        {
            "question_text": "Unless otherwise posted, the speed limit on a highway outside a city is:",
            "option_a": "60 km/h",
            "option_b": "80 km/h",
            "option_c": "100 km/h",
            "option_d": "110 km/h",
            "correct_option": "B",
            "explanation": "The default rural highway speed limit is 80 km/h."
        },
        {
            "question_text": "School zone speed limit (when in effect) is:",
            "option_a": "30 km/h",
            "option_b": "40 km/h",
            "option_c": "20 km/h",
            "option_d": "50 km/h",
            "correct_option": "A",
            "explanation": "30 km/h from 8am to 5pm on school days."
        },
        {
            "question_text": "Playground zone speed limit is:",
            "option_a": "30 km/h from dawn to dusk.",
            "option_b": "30 km/h all day.",
            "option_c": "40 km/h.",
            "option_d": "50 km/h unless children are present.",
            "correct_option": "A",
            "explanation": "30 km/h every day from dawn to dusk."
        },
        {
            "question_text": "When parking uphill with a curb, turn your wheels:",
            "option_a": "Right (towards the curb).",
            "option_b": "Left (away from the curb).",
            "option_c": "Parallel to the curb.",
            "option_d": "It doesn't matter.",
            "correct_option": "B",
            "explanation": "Turn wheels AWAY from the curb so if the car rolls back, the tires hit the curb."
        },
        {
            "question_text": "When parking downhill with a curb, turn your wheels:",
            "option_a": "Right (towards the curb).",
            "option_b": "Left (away from the curb).",
            "option_c": "Parallel.",
            "option_d": "Straight.",
            "correct_option": "A",
            "explanation": "Turn wheels TOWARD the curb so if the car rolls, it rolls into the curb."
        },
        {
            "question_text": "You must not park within ___ meters of a fire hydrant.",
            "option_a": "2",
            "option_b": "5",
            "option_c": "10",
            "option_d": "6",
            "correct_option": "B",
            "explanation": "5 meters is the required distance."
        },
        {
            "question_text": "You must not park within ___ meters of a stop sign.",
            "option_a": "5",
            "option_b": "6",
            "option_c": "10",
            "option_d": "15",
            "correct_option": "B",
            "explanation": "6 meters allows other drivers to see the sign clearly."
        },
        {
            "question_text": "High beam headlights must be dimmed when an oncoming car is within:",
            "option_a": "50 meters.",
            "option_b": "150 meters.",
            "option_c": "300 meters.",
            "option_d": "500 meters.",
            "correct_option": "B",
            "explanation": "Dim lights 150m for oncoming traffic, and 150m when following another vehicle."
        },
        {
            "question_text": "It is illegal to pass another vehicle when:",
            "option_a": "You are on a highway.",
            "option_b": "There is a broken yellow line.",
            "option_c": "You are approaching the crest of a hill or a curve where vision is obstructed.",
            "option_d": "The car ahead is going 10km/h under the limit.",
            "correct_option": "C",
            "explanation": "Never pass if you cannot see a safe distance ahead."
        },

        # --- HAZARDS & EMERGENCIES ---
        {
            "question_text": "If your vehicle begins to skid (hydroplane) on wet pavement:",
            "option_a": "Brake hard immediately.",
            "option_b": "Steer in the opposite direction of the skid.",
            "option_c": "Ease off the gas and steer in the direction you want to go.",
            "option_d": "Accelerate to gain traction.",
            "correct_option": "C",
            "explanation": "Ease off the accelerator and steer gently in the desired direction. Do not brake hard."
        },
        {
            "question_text": "When driving in fog, you should use:",
            "option_a": "High beam headlights.",
            "option_b": "Low beam headlights.",
            "option_c": "Parking lights only.",
            "option_d": "No lights.",
            "correct_option": "B",
            "explanation": "High beams reflect off the fog and reduce visibility. Use low beams or fog lights."
        },
        {
            "question_text": "If a tire blows out while driving:",
            "option_a": "Slam on the brakes.",
            "option_b": "Keep a firm grip on the wheel and ease off the gas.",
            "option_c": "Steer sharply to the shoulder.",
            "option_d": "Accelerate.",
            "correct_option": "B",
            "explanation": "Maintain control, slow down gradually, and pull over when safe."
        },
        {
            "question_text": "Black ice is:",
            "option_a": "Dirty snow.",
            "option_b": "A thin, invisible layer of ice on the road.",
            "option_c": "Frozen mud.",
            "option_d": "Ice mixed with oil.",
            "correct_option": "B",
            "explanation": "It looks like wet pavement but is extremely slippery. Common on bridges and overpasses."
        },
        {
            "question_text": "Following distance under normal conditions should be at least:",
            "option_a": "1 second.",
            "option_b": "2 seconds.",
            "option_c": "5 seconds.",
            "option_d": "10 seconds.",
            "correct_option": "B",
            "explanation": "The 'two-second rule' is the minimum. Increase this in bad weather."
        },
        {
            "question_text": "When large trucks turn:",
            "option_a": "They turn like cars.",
            "option_b": "They may swing wide to the left to make a right turn.",
            "option_c": "They always stay in the right lane.",
            "option_d": "They speed up.",
            "correct_option": "B",
            "explanation": "Never squeeze between a turning truck and the curb."
        },
        {
            "question_text": "If an animal runs in front of your car:",
            "option_a": "Swerve immediately.",
            "option_b": "Brake firmly and steer to avoid it, but check for traffic first.",
            "option_c": "Speed up.",
            "option_d": "Close your eyes.",
            "correct_option": "B",
            "explanation": "Swerving can be more dangerous than hitting the animal if it causes a collision with another car or a ditch."
        },

        # --- DRIVING & LIFESTYLE ---
        {
            "question_text": "In the Graduated Licensing Program (GLP), an 'L' driver must have:",
            "option_a": "Zero blood alcohol content.",
            "option_b": "Less than 0.05 blood alcohol.",
            "option_c": "Less than 0.08 blood alcohol.",
            "option_d": "One beer.",
            "correct_option": "A",
            "explanation": "Learners and Novice drivers must have 0% alcohol in their system."
        },
        {
            "question_text": "How many passengers can an 'L' driver carry?",
            "option_a": "As many as there are seatbelts.",
            "option_b": "One supervisor and one additional passenger.",
            "option_c": "Just the supervisor.",
            "option_d": "Two friends.",
            "correct_option": "B",
            "explanation": "You must have a supervisor (25+ with valid license). You are limited to one additional passenger."
        },
        {
            "question_text": "Using a hand-held electronic device while driving is:",
            "option_a": "Allowed at red lights.",
            "option_b": "Prohibited for all drivers.",
            "option_c": "Allowed for experienced drivers.",
            "option_d": "Allowed if using speakerphone in hand.",
            "correct_option": "B",
            "explanation": "It is illegal to hold or operate an electronic device while driving."
        },
        {
            "question_text": "The 'N' sign must be displayed:",
            "option_a": "In the front window.",
            "option_b": "On the back of the vehicle.",
            "option_c": "On the dashboard.",
            "option_d": "Only at night.",
            "correct_option": "B",
            "explanation": "The green N sign must be visible on the rear of the vehicle."
        },
        {
            "question_text": "If you are feeling tired while driving:",
            "option_a": "Open the window.",
            "option_b": "Turn up the radio.",
            "option_c": "Pull over and rest.",
            "option_d": "Drink coffee and keep going.",
            "correct_option": "C",
            "explanation": "The only cure for fatigue is sleep. Stop and rest."
        },
        {
            "question_text": "Which of the following is most likely to cause a skid?",
            "option_a": "Driving too fast for conditions.",
            "option_b": "Properly inflated tires.",
            "option_c": "Driving in dry weather.",
            "option_d": "Stopping slowly.",
            "correct_option": "A",
            "explanation": "Speeding, especially in poor conditions, is a primary cause of skids."
        },
        {
            "question_text": "Shoulder checking means:",
            "option_a": "Looking in your side mirror.",
            "option_b": "Looking over your shoulder to check your blind spot.",
            "option_c": "Checking the width of the road shoulder.",
            "option_d": "Leaning out the window.",
            "correct_option": "B",
            "explanation": "Mirrors don't show everything. You must physically look to check blind spots."
        },
        {
            "question_text": "The blind spots of a large truck are:",
            "option_a": "Smaller than a car's.",
            "option_b": "Only directly behind it.",
            "option_c": "In front, behind, and to the sides (No-Zones).",
            "option_d": "Trucks don't have blind spots.",
            "correct_option": "C",
            "explanation": "If you can't see the truck driver's mirrors, they can't see you."
        },
        {
            "question_text": "HOV lanes are for:",
            "option_a": "High Octane Vehicles.",
            "option_b": "Heavy Operating Vehicles.",
            "option_c": "High Occupancy Vehicles (buses, carpools).",
            "option_d": "Honda Only Vehicles.",
            "correct_option": "C",
            "explanation": "Reserved for buses and vehicles with a minimum number of passengers (usually 2+)."
        },
        {
            "question_text": "Hydroplaning can happen at speeds as low as:",
            "option_a": "100 km/h.",
            "option_b": "80 km/h.",
            "option_c": "50 km/h.",
            "option_d": "120 km/h.",
            "correct_option": "C",
            "explanation": "Depending on tire tread and water depth, hydroplaning can occur at city speeds."
        },
        {
            "question_text": "If you are involved in a crash, you must:",
            "option_a": "Leave immediately if it wasn't your fault.",
            "option_b": "Remain at the scene and exchange information.",
            "option_c": "Only stop if someone is hurt.",
            "option_d": "Call your lawyer before stopping.",
            "correct_option": "B",
            "explanation": "You are legally required to stop, help if possible, and exchange info."
        },
        {
            "question_text": "A flashing yellow arrow means:",
            "option_a": "Left turn allowed, but yield to oncoming traffic.",
            "option_b": "Stop.",
            "option_c": "Protected left turn.",
            "option_d": "No left turn.",
            "correct_option": "A",
            "explanation": "You can turn, but you don't have the right of way over oncoming traffic."
        },
        {
            "question_text": "Cyclists on the road:",
            "option_a": "Should ride on the sidewalk.",
            "option_b": "Have the same rights and duties as motor vehicles.",
            "option_c": "Always yield to cars.",
            "option_d": "Should ride against traffic.",
            "correct_option": "B",
            "explanation": "Bicycles are vehicles. Share the road."
        }
    ]

    for q in questions:
        question = models.QuizQuestion(**q)
        db.add(question)
    
    db.commit()
    print(f"Seeded {len(questions)} quiz questions.")

if __name__ == "__main__":
    seed_large_quiz_data()
