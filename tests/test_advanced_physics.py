import pytest
import numpy as np
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
