import pytest
import numpy as np
import json
from app.services.diagnostic_evaluator import DiagnosticEvaluator

def test_calibrate_orientation_simple_rotation():
    """
    Test that the evaluator can calibrate its frame when the phone is rotated.
    Case: Phone rotated 90 degrees around Z-axis.
    Vehicle Forward (Y) = Sensor X
    Vehicle Right (X) = Sensor -Y
    Vehicle Up (Z) = Sensor Z
    """
    evaluator = DiagnosticEvaluator()
    
    # 1. Gravity is primarily on Z axis in both frames (phone is level but rotated)
    # 2. Vehicle accelerates forward (Vehicle Y). Sensor sees this on X.
    
    # Mock data for calibration (first 5 seconds)
    # 10Hz = 50 samples
    calibration_data = []
    for i in range(50):
        # Gravity (down is -Z)
        # Random noise +/- 0.05
        noise = np.random.normal(0, 0.05, 3)
        accel = np.array([0.0, 0.0, -9.81]) + noise
        
        # Add forward acceleration (0.5 m/s^2) on Sensor X (which is Vehicle Y)
        if 10 <= i <= 30:
            accel[0] += 1.0 
            
        calibration_data.append({
            'timestamp': 1000 + i * 100,
            'x': accel[0], 'y': accel[1], 'z': accel[2]
        })

    # Speed data showing acceleration (0 to 18 km/h over 2 seconds)
    speed_data = []
    for i in range(50):
        speed = 0
        if i > 10:
            speed = min(18, (i - 10) * 0.9) # 0.9 km/h per 100ms = 9 km/h/s = 2.5 m/s^2? 
            # Wait, 1.0 m/s^2 = 3.6 km/h / s. 
            # 100ms interval -> 0.36 km/h per step.
        speed_data.append({
            'timestamp': 1000 + i * 100,
            'speed': speed
        })

    matrix = evaluator._calibrate_orientation(calibration_data, speed_data)
    
    assert matrix is not None
    assert matrix.shape == (3, 3)
    
    # Test transformation
    # A vector on Sensor X (1, 0, 0) should be transformed to Vehicle Forward (0, 1, 0)
    sensor_vector = np.array([1.0, 0.0, 0.0])
    vehicle_vector = np.dot(matrix, sensor_vector)
    
    # Check if vehicle_vector is close to [0, 1, 0]
    # (Allowing some tolerance for noise)
    assert np.isclose(vehicle_vector[1], 1.0, atol=0.2)
    assert np.isclose(vehicle_vector[0], 0.0, atol=0.2)
    assert np.isclose(vehicle_vector[2], 0.0, atol=0.2)

def test_jerk_calculation():
    """
    Test that jerk is calculated correctly and penalized.
    """
    evaluator = DiagnosticEvaluator()
    
    # Smooth braking: 0 to 0.5g over 2 seconds (20 samples)
    # Jerk = 0.5g / 2s = 0.25g/s = 2.45 m/s^3
    smooth_data = []
    for i in range(20):
        accel = (i / 20.0) * (-0.5 * 9.81) # Linear ramp
        smooth_data.append({'timestamp': 1000 + i*100, 'x': 0, 'y': accel, 'z': -9.81})
        
    # Harsh "Stab" braking: 0 to 0.5g over 0.2 seconds (2 samples)
    # Jerk = 0.5g / 0.2s = 2.5g/s = 24.5 m/s^3
    stab_data = []
    for i in range(20):
        accel = 0
        if i < 2:
            accel = (i / 2.0) * (-0.5 * 9.81)
        else:
            accel = -0.5 * 9.81
        stab_data.append({'timestamp': 3000 + i*100, 'x': 0, 'y': accel, 'z': -9.81})

    smooth_jerk_score = evaluator._calculate_jerk_score(smooth_data)
    stab_jerk_score = evaluator._calculate_jerk_score(stab_data)
    
    assert smooth_jerk_score > stab_jerk_score

def test_friction_circle_penalty():
    """
    Test that combined lateral and longitudinal forces are penalized.
    Case: 0.45g braking AND 0.45g cornering.
    Individual: Below thresholds (0.6g braking, 0.45g cornering).
    Combined: sqrt(0.45^2 + 0.45^2) ≈ 0.64g -> Exceeds 0.6g total grip threshold.
    """
    evaluator = DiagnosticEvaluator()
    
    # 0.45g on Y (Forward/Braking) and 0.45g on X (Lateral)
    # Using the calibrated frame for this test
    # (Simplified for the internal method check)
    accel = -0.45 * 9.81
    data = [
        {'timestamp': 1000, 'x': accel, 'y': accel, 'z': -9.81},
        {'timestamp': 1100, 'x': accel, 'y': accel, 'z': -9.81}
    ]
    
    # We expect a penalty even though braking is 0.45g (< 0.6g)
    # and cornering is 0.45g (<= 0.45g).
    # The new friction circle method should flag this.
    score, feedback = evaluator._evaluate_combined_dynamics(data)
    
    assert score < 100
    assert any(e['type'] == 'friction_circle_violation' for e in feedback['events'])
    assert feedback['max_total_g'] > 0.6

def test_dynamic_cornering_thresholds():
    """
    Test that cornering thresholds are speed-sensitive.
    Case: 0.28g lateral acceleration.
    At 20 km/h: Safe (below dynamic threshold of 0.35 - 20*0.002 = 0.31g).
    At 100 km/h: Dangerous (threshold lowers to floor of 0.15g).
    """
    evaluator = DiagnosticEvaluator()
    
    # 0.28g lateral acceleration — between low-speed and high-speed thresholds
    accel = 0.28 * 9.81
    accel_data = [{'timestamp': 1000, 'x': accel, 'y': 0, 'z': -9.81}]
    
    # 1. Test at low speed (20 km/h) — threshold = 0.35 - 0.04 = 0.31g
    speed_low = [{'timestamp': 1000, 'speed': 20}]
    score_low, feedback_low = evaluator._evaluate_cornering(accel_data, [], speed_data=speed_low)
    
    # 2. Test at high speed (100 km/h) — threshold = max(0.15, 0.35 - 0.20) = 0.15g
    speed_high = [{'timestamp': 1000, 'speed': 100}]
    score_high, feedback_high = evaluator._evaluate_cornering(accel_data, [], speed_data=speed_high)
    
    # High speed should have penalty, low speed should not
    assert feedback_low['sharp_turns'] == 0
    assert feedback_high['sharp_turns'] > 0
    assert score_high < score_low

@pytest.mark.asyncio
async def test_full_physics_integration_pipeline():
    """
    Test the integrated pipeline with a complex scenario.
    1. Calibration window (accel in straight line)
    2. Uphill section (compensate gravity)
    3. High-jerk "stab" braking
    4. Combined braking/turning (friction circle)
    """
    evaluator = DiagnosticEvaluator()
    
    # 1. Calibration: 25 points of 1 m/s^2 forward accel
    accel_data = []
    speed_data = []
    for i in range(25):
        ts = i * 100
        # Phone mounted sideways: Forward is Sensor X
        accel_data.append({'timestamp': ts, 'x': 1.0, 'y': 0, 'z': 9.81})
        speed_data.append({'timestamp': ts, 'speed': (i * 0.36)}) # 3.6 km/h per second = 1 m/s^2
        
    # 2. Uphill (10% grade) + 0.45g Sensor Braking
    # After gravity compensation (subtracting g*sin(θ) ≈ 0.10g for 10% grade),
    # the true braking force is ~0.35g, below the 0.40g threshold.
    # This verifies that incline compensation correctly prevents false positives.
    ts_hill = 3000
    accel_data.append({'timestamp': ts_hill, 'x': -0.45 * 9.81, 'y': 0, 'z': 9.81})
    speed_data.append({'timestamp': ts_hill, 'speed': 20, 'altitude': 100, 'latitude': 49.0, 'longitude': -123.0})
    # Add a point before for incline calc
    speed_data.insert(-1, {'timestamp': 2000, 'speed': 25, 'altitude': 90, 'latitude': 48.9991, 'longitude': -123.0})

    # 3. High Jerk "Stab" braking
    # 0 to 0.5g in 100ms
    accel_data.append({'timestamp': 4000, 'x': 0, 'y': 0, 'z': 9.81})
    accel_data.append({'timestamp': 4100, 'x': -0.5 * 9.81, 'y': 0, 'z': 9.81})
    
    # 4. Friction Circle violation: sustained 0.40g braking AND 0.40g turning
    # Combined = sqrt(0.40² + 0.40²) = 0.566g > 0.50g friction threshold.
    # Individual components stay below 0.40g harsh braking threshold.
    for j in range(6):
        ts_combined = 6000 + j * 100
        accel_data.append({'timestamp': ts_combined, 'x': -0.40 * 9.81, 'y': 0.40 * 9.81, 'z': 9.81})
    
    ride_data = {
        'duration_minutes': 25,
        'distance_km': 10,
        # Sensors handled by evaluator internally via mock injection or JSON
        'acceleration_data': json.dumps(accel_data),
        'speed_data': json.dumps(speed_data),
        'rotation_data': '[]'
    }
    
    # We await the main evaluate method
    result = await evaluator.evaluate("test_ride_id", ride_data)
    eval_dict = json.loads(result['evaluation_result'])
    
    # VERIFICATIONS
    
    # 1. Calibration should have happened (result uses orientation_matrix)
    # 2. Harsh braking at 3000ms should be GONE (compensated)
    # The hill braking event (0.45g sensor at 10% grade → ~0.35g true) should NOT be flagged.
    hill_braking_events = [e for e in eval_dict['events']
                          if e['type'] == 'harsh_braking' and abs(e.get('timestamp', 0) - 3000) < 500]
    assert len(hill_braking_events) == 0
    
    # 3. Friction Circle violation should be present
    friction_events = [e for e in eval_dict['events'] if e['type'] == 'friction_circle_violation']
    assert len(friction_events) > 0
    
    # 4. Smoothness score should be impacted by the "Stab"
    assert result['smoothness_score'] < 100
    
    # 5. Overall score should be a weighted combination
    assert 0 < result['overall_score'] < 100

def test_gravity_compensation_on_hill():
    """
    Test that gravity is compensated on hills.
    Case: Steep uphill grade (10% or ~5.7 degrees).
    Driver brakes at 0.35g (safe under 0.40g threshold).
    Sensor sees 0.35g + (g * sin(5.7)) ≈ 0.45g (looks harsh).
    Compensation should subtract the gravity leak and avoid penalty.
    """
    evaluator = DiagnosticEvaluator()
    
    # Sensor sees 0.45g deceleration on an uphill
    # (Vehicle Forward Y = -0.45g)
    sensor_accel = -0.45 * 9.81
    # Z should be +9.81 for a level phone (pointing up)
    accel_data = [{'timestamp': 1000, 'x': 0, 'y': sensor_accel, 'z': 9.81}]
    
    # 1. Test WITHOUT compensation (baseline)
    # This should trigger harsh braking since 0.45g > 0.40g
    score_raw, feedback_raw = evaluator._evaluate_braking(accel_data, [])
    assert feedback_raw['harsh_braking_events'] == 1
    
    # 2. Test WITH compensation
    # We provide GPS altitude data showing a 10m gain over 100m distance (10% grade)
    # sin(theta) = 10/100 = 0.1
    # a_true = -0.45g + 0.1g = -0.35g (safe!)
    gps_data = [
        {'timestamp': 0, 'latitude': 49.0, 'longitude': -123.0, 'altitude': 90, 'speed': 50},
        {'timestamp': 1000, 'latitude': 49.0009, 'longitude': -123.0, 'altitude': 100, 'speed': 30}
    ]
    
    score_comp, feedback_comp = evaluator._evaluate_braking(accel_data, [], gps_data=gps_data)
    
    assert feedback_comp['harsh_braking_events'] == 0
    assert score_comp > score_raw

def test_vertical_impact_detection():
    """
    Test that vertical impacts (potholes/speed bumps) are detected.
    Case: Sharp vertical spike of 0.5g on the Z axis.
    """
    evaluator = DiagnosticEvaluator()
    
    # 0.5g spike on Z (relative to 1.0g gravity)
    # 9.81 * 1.5 ≈ 14.7
    data = [
        {'timestamp': 1000, 'x': 0, 'y': 0, 'z': 9.81},
        {'timestamp': 1100, 'x': 0, 'y': 0, 'z': 14.7}, # Spike
        {'timestamp': 1200, 'x': 0, 'y': 0, 'z': 9.81}
    ]
    
    score, feedback = evaluator._evaluate_vertical_impacts(data)
    
    assert score < 100
    assert any(e['type'] == 'vertical_impact' for e in feedback['events'])
    assert feedback['impact_count'] == 1
