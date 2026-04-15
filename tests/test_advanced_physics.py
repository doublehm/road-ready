import pytest
import numpy as np
import json
from app.services.diagnostic_evaluator import DiagnosticEvaluator

def test_calibrate_orientation_simple_rotation():
    """
    Test that the evaluator can calibrate its frame when the phone is rotated.
    Case: Phone rotated 90 degrees around Z-axis.
    """
    evaluator = DiagnosticEvaluator()
    
    calibration_data = []
    for i in range(100):
        ts = 1000 + i * 100
        noise = np.random.normal(0, 0.02, 3)
        # Phone is level: gravity points in Z direction
        accel = np.array([0.0, 0.0, 9.81]) + noise
        
        # Forward acceleration (1.0 m/s^2) on Sensor X (Vehicle Y)
        if 20 <= i <= 50:
            accel[0] += 1.0 
            
        calibration_data.append({
            'timestamp': ts, 'x': accel[0], 'y': accel[1], 'z': accel[2]
        })

    speed_data = []
    for i in range(100):
        ts = 1000 + i * 100
        speed = 0
        if i > 20:
            speed = min(30, (i - 20) * 0.5) 
        speed_data.append({'timestamp': ts, 'speed': speed})

    matrix = evaluator._calibrate_orientation(calibration_data, speed_data)
    
    assert matrix is not None
    assert matrix.shape == (3, 3)
    
    # Sensor X (1,0,0) -> Vehicle Forward (0,1,0)
    sensor_vector = np.array([1.0, 0.0, 0.0])
    vehicle_vector = np.dot(matrix, sensor_vector)
    assert np.isclose(vehicle_vector[1], 1.0, atol=0.2)

def test_jerk_calculation():
    """
    Test that jerk is calculated correctly and penalized via smoothness score.
    """
    evaluator = DiagnosticEvaluator()
    
    # Smooth braking: ramp up over 3s
    smooth_data = []
    for i in range(30):
        accel = (i / 30.0) * (0.5 * 9.81) 
        smooth_data.append({'timestamp': 1000 + i*100, 'x': 0, 'y': accel, 'z': 9.81})
        
    # Harsh "Stab" braking: 0 to 0.5g in 100ms
    stab_data = []
    for i in range(30):
        accel = 0.5 * 9.81 if i > 10 else 0
        stab_data.append({'timestamp': 5000 + i*100, 'x': 0, 'y': accel, 'z': 9.81})

    smooth_score = evaluator._calculate_jerk_score(smooth_data)
    stab_score = evaluator._calculate_jerk_score(stab_data)
    
    assert smooth_score > stab_score

def test_friction_circle_penalty():
    """
    Test combined forces violation (> 0.6G).
    """
    evaluator = DiagnosticEvaluator()
    
    # Use 0.5G + 0.5G = 0.707G
    accel = 0.5 * 9.81
    raw_data = []
    for i in range(20):
        raw_data.append({'timestamp': 1000 + i*100, 'x': 0, 'y': 0, 'z': 9.81})
    for i in range(20, 30):
        raw_data.append({'timestamp': 1000 + i*100, 'x': accel, 'y': accel, 'z': 9.81})
    
    physics_data = evaluator._process_physics(raw_data, np.eye(3))
    penalty, feedback = evaluator._evaluate_combined_dynamics(physics_data)
    
    assert penalty > 0
    assert any(e['type'] == 'friction_circle_violation' for e in feedback['events'])

def test_dynamic_cornering_thresholds():
    """
    Test speed-sensitive cornering thresholds.
    """
    evaluator = DiagnosticEvaluator()
    
    # 0.28G lateral acceleration
    accel = 0.28 * 9.81
    
    def get_ride_segment(speed_val):
        raw = []
        for i in range(20): raw.append({'timestamp': i*100, 'x': 0, 'y': 0, 'z': 9.81})
        for i in range(20, 30): raw.append({'timestamp': (20+i)*100, 'x': accel, 'y': 0, 'z': 9.81})
        physics = evaluator._process_physics(raw, np.eye(3))
        speeds = [{'timestamp': i*100, 'speed': speed_val} for i in range(100)]
        return physics, speeds

    # 20 km/h: Threshold = 0.45 - 20*0.002 = 0.41G. 0.28G is SAFE.
    phys_low, speed_low = get_ride_segment(20)
    score_low, fb_low = evaluator._evaluate_cornering(phys_low, [], speed_data=speed_low)
    
    # 110 km/h: Threshold = max(0.15, 0.45 - 110*0.002) = 0.23G. 0.28G is HARSH.
    phys_high, speed_high = get_ride_segment(110)
    score_high, fb_high = evaluator._evaluate_cornering(phys_high, [], speed_data=speed_high)
    
    assert fb_low['sharp_turns'] == 0
    assert fb_high['sharp_turns'] > 0

@pytest.mark.asyncio
async def test_full_physics_integration_pipeline():
    evaluator = DiagnosticEvaluator()
    
    # 1. Calibration (ensure it ends at rest)
    accel_data, speed_data = [], []
    for i in range(100):
        ts = i * 100
        # Acceleration from 2s to 5s
        val = 1.0 if 20 <= i <= 50 else 0.0
        accel_data.append({'timestamp': ts, 'x': val, 'y': 0, 'z': 9.81})
        speed = min(30, (i - 20) * 0.5) if i > 20 else 0
        speed_data.append({'timestamp': ts, 'speed': speed})
        
    # 2. Friction circle violation (0.5G + 0.5G = 0.707G)
    for j in range(20):
        ts_c = 15000 + j * 100
        # Sensor X was forward during calibration
        accel_data.append({'timestamp': ts_c, 'x': 0.5*9.81, 'y': 0.5*9.81, 'z': 9.81})
        speed_data.append({'timestamp': ts_c, 'speed': 30})
    
    ride_data = {
        'duration_minutes': 25, 'distance_km': 10,
        'acceleration_data': json.dumps(accel_data),
        'speed_data': json.dumps(speed_data),
        'heading_data': '[]', 'speed_limit_data': '[]'
    }
    
    result = await evaluator.evaluate("test_ride_id", ride_data)
    eval_dict = json.loads(result['evaluation_result'])
    
    assert any(e['type'] == 'friction_circle_violation' for e in eval_dict['events'])
    assert result['overall_score'] < 100

def test_gravity_compensation_on_hill():
    evaluator = DiagnosticEvaluator()
    
    raw_data = []
    # Level driving
    for i in range(100):
        raw_data.append({'timestamp': i*100, 'x': 0, 'y': 0, 'z': 9.81})
    # Transition to hill (0.1g tilt) + 0.35g braking = 0.45g sensor Y
    for i in range(100, 300):
        raw_data.append({'timestamp': i*100, 'x': 0, 'y': 0.45*9.81, 'z': 0.995*9.81})

    physics = evaluator._process_physics(raw_data, np.eye(3))
    # Check later samples
    stable_long_accels = [abs(p['long_accel']) for p in physics[250:]]
    for a in stable_long_accels:
        assert a < evaluator.HARD_BRAKING_THRESHOLD 

def test_vertical_impact_detection():
    evaluator = DiagnosticEvaluator()
    data = []
    for i in range(20):
        z = 15.0 if i == 15 else 9.81
        data.append({'timestamp': 1000 + i*100, 'x': 0, 'y': 0, 'z': z})
    
    physics = evaluator._process_physics(data, np.eye(3))
    penalty, feedback = evaluator._evaluate_impacts(physics)
    assert penalty > 0
    assert any(e['type'] == 'vertical_impact' for e in feedback['events'])
