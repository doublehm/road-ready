# Diagnostic Ride Feature - Implementation Summary

## Overview
The diagnostic ride feature has been successfully implemented! This feature allows students to assess their driving skills using phone sensors and potentially skip the Basics module by demonstrating proficiency, saving $800+.

## What Was Implemented

### Phase 1: Backend Foundation ✅
1. **Database Models** (`app/models.py`)
   - `LearningModule`: Defines learning modules (Basics, Advanced)
   - `StudentModuleProgress`: Tracks student progress through modules
   - `DiagnosticRide`: Stores diagnostic ride data and evaluation results
   - Updated `StudentProfile` with diagnostic-related fields
   - Updated `BookingRequest` with module support

2. **API Endpoints**
   - `app/api/modules.py`: Module management (list, enroll, progress tracking)
   - `app/api/diagnostic_rides.py`: Diagnostic ride creation, evaluation, instructor review
   - Updated `app/api/bookings.py`: Added module filtering and completion tracking

3. **Evaluation Service** (`app/services/diagnostic_evaluator.py`)
   - Automated scoring algorithm:
     - Braking Score (40% weight): Detects harsh braking, smooth stops
     - Speed Score (35% weight): Speed limit compliance, consistency
     - Cornering Score (25% weight): Turn smoothness, lateral acceleration
   - Pass criteria: 75+ overall, 70+ each category, 20+ min, 5+ km
   - Generates detailed feedback for improvement

4. **Pydantic Schemas** (`app/schemas.py`)
   - Schemas for all new models and API requests/responses

5. **Database Seeding** (`seed_modules.py`)
   - Script to populate initial modules (Basics $800, Advanced $600)

### Phase 2: Mobile Sensor Integration ✅
1. **expo-sensors Package** (`mobile-app/package.json`)
   - Added expo-sensors ~14.0.0 for accelerometer/gyroscope

2. **Custom Hooks**
   - `useDeviceMotion.js`: Collects accelerometer & gyroscope data at 10 Hz
   - `useGPSTracking.js`: Tracks GPS location, speed, distance, route

### Phase 3: Mobile UI Screens ✅
1. **Diagnostic Ride Screens**
   - `DiagnosticRideIntroScreen.js`: Explains feature, shows potential savings
   - `DiagnosticRideSetupScreen.js`: Pre-ride setup (parent/instructor selection)
   - `DiagnosticRideActiveScreen.js`: Live ride tracking with sensor integration
   - `DiagnosticRideResultsScreen.js`: Displays scores and pass/fail status

2. **Supporting Screens**
   - `ModulesScreen.js`: Lists learning modules, enrollment options
   - `ScoreGauge.js`: Circular gauge component for score visualization

3. **Updated Screens**
   - `StudentHomeScreen.js`: Added "Diagnostic Ride" quick action tile
   - `App.js`: Registered all new screens in navigation

## Setup Instructions

### Backend Setup

1. **Install Dependencies** (if needed)
   ```bash
   cd /home/Hoss/Documents/road-ready
   source venv/bin/activate
   pip install -r requirements.txt
   ```

2. **Create Database Tables**
   The app automatically creates tables on startup via `models.Base.metadata.create_all()`.
   Just restart your backend server:
   ```bash
   uvicorn app.main:app --reload
   ```

3. **Seed Learning Modules**
   After the backend is running, seed the modules:
   ```bash
   python seed_modules.py
   ```
   This creates:
   - Basics Module: $800 package (10 hours) or $80/hr
   - Advanced/Test Prep Module: $600 package (8 hours) or $75/hr

### Mobile App Setup

1. **Install Dependencies**
   ```bash
   cd mobile-app
   npm install
   ```
   This will install the new `expo-sensors` package.

2. **Run on Physical Device** (Required for sensors!)
   Sensors don't work in simulators. You must use a physical device:
   ```bash
   npm start
   # Then scan QR code with Expo Go app
   ```

## Feature Flow

### Student Journey

1. **Student Home**: Taps "Diagnostic Ride" quick action
2. **Intro Screen**: Learns about feature, sees $800 savings potential
3. **Setup Screen**: Chooses parent-supervised (free) or instructor-supervised (~$120)
4. **Active Ride**:
   - Phone tracks GPS, accelerometer, gyroscope
   - Must drive 20+ minutes, 5+ km
   - Real-time stats displayed
5. **Results Screen**:
   - **If PASS**: Unlocks Advanced module, skips Basics
   - **If FAIL**: Options to retry with instructor or start Basics

### Evaluation Process

When ride completes:
1. Sensor data uploaded to backend
2. `DiagnosticEvaluator` analyzes:
   - Braking events (harsh vs smooth)
   - Speed compliance (speeding, under-speed)
   - Cornering quality (sharp turns, smoothness)
3. Scores calculated (0-100 for each category)
4. Pass/fail determined based on thresholds
5. Detailed feedback generated

### Instructor Override

For instructor-supervised rides:
- Instructor can review automated evaluation
- Can adjust scores or change pass/fail
- Override is marked in database

## Sensor Physics & Force Calculation

### Overview

The diagnostic evaluator uses the phone's built-in accelerometer to measure forces acting on the vehicle during a ride. All forces are expressed in **g-force** (multiples of Earth's gravitational acceleration, 9.81 m/s²). The raw accelerometer readings in m/s² are converted to g using:

```
force_in_g = abs(raw_acceleration_m_s2) / 9.81
```

### Harsh Braking — Deceleration Force

**What it measures:** How abruptly the driver decelerates, measured via the forward/backward axis of the accelerometer.

**Axis mapping:** Phone orientation in the car varies, so the evaluator checks both Y and Z axes and uses the strongest deceleration:

- **Portrait mount** (phone upright in holder): Y-axis = forward/backward (braking)
- **Landscape mount** (phone on its side): Z-axis = forward/backward (braking)

```python
y_decel_g = abs(y_accel) / 9.81   # only if y_accel < 0 (deceleration)
z_decel_g = abs(z_accel) / 9.81   # only if z_accel < 0 (deceleration)
deceleration_g = max(y_decel_g, z_decel_g)
```

**Thresholds:**

| G-Force Range | Classification | Scoring Impact |
|---------------|---------------|----------------|
| 0.1 - 0.4g | Smooth braking | +2 pts bonus per event (max +20) |
| > 0.6g | Harsh braking | -10 pts per event, severity: `medium` or `high` |

> **Noise filtering:** A 5-sample sliding median filter is applied to all accelerometer data before evaluation. Single-sample spikes from potholes or vibration are suppressed. Additionally, samples with Z-axis deviation from gravity exceeding 0.4g are excluded from braking analysis (these indicate vertical impacts, not braking).

**Real-world reference:**

| G-Force | What it feels like |
|---------|--------------------|
| 0.1 - 0.3g | Normal comfortable stop at a red light |
| 0.4g | Noticeable jerk, passengers lurch forward |
| 0.6g | Emergency braking, approaching ABS activation |
| 1.0g | Full emergency stop on dry pavement |

**Source:** `app/services/diagnostic_evaluator.py` — `_evaluate_braking()` method

### Sudden Stops — Speed Drop Detection

**What it measures:** Rapid speed decrease detected via GPS speed data (complementary to accelerometer-based braking).

**Calculation:** Compares consecutive GPS speed readings within a 1.5-second window:

```python
speed_drop = previous_speed - current_speed  # km/h
```

**Threshold:** A drop exceeding 5 km/h within 1.5 seconds triggers a sudden stop event (-15 pts, severity: `high`).

**Source:** `app/services/diagnostic_evaluator.py` — `_evaluate_braking()` method

### Sharp Turns — Lateral Force

**What it measures:** The sideways (centripetal) force experienced during a turn, measured via the lateral axis of the accelerometer.

**Axis mapping:** Similar to braking, phone orientation matters:

- **Portrait mount**: X-axis = lateral (sideways force during turns)
- **Landscape mount**: Y-axis may be lateral instead

```python
lateral_g = abs(x_accel) / 9.81

# Fallback: if X is very low but Y is high, phone may be rotated
y_lateral_g = abs(y_accel) / 9.81
if y_lateral_g > lateral_g and lateral_g < 0.05:
    lateral_g = y_lateral_g
```

**Thresholds:**

| G-Force Range | Classification | Scoring Impact |
|---------------|---------------|----------------|
| 0.02 - 0.20g | Smooth turn | +2 pts bonus per event (max +20) |
| 0.20 - 0.45g | Normal turn | No penalty |
| > 0.45g (dynamic) | Sharp turn | -8 pts per event × speed multiplier |

> **Dynamic threshold:** The sharp turn threshold decreases with speed: `threshold = 0.45 - (speed_kmh × 0.0015)`, with a **floor of 0.25g** (was 0.15g). At 100 km/h the threshold is 0.30g.
>
> **GPS heading cross-check:** Before flagging a sharp turn, the system verifies that GPS heading changed by at least 5° within a ±2 second window. This prevents road vibration and phone jitter from being misclassified as cornering events on straight roads.

**Real-world reference:**

| G-Force | What it feels like |
|---------|--------------------|
| 0.15g | Gentle lane change or gradual highway curve |
| 0.3g | Aggressive turn, passengers slide sideways |
| 0.5g | Dangerously sharp turn, risk of skidding on wet roads |
| 0.8g+ | Approaching tire grip limits |

**The physics:** When a car turns, centripetal acceleration pushes occupants outward. The formula is `a = v²/r` where `v` is speed and `r` is turn radius. A tighter turn or higher speed = more lateral g-force. For example, a 50 km/h turn with a 50m radius produces ~0.39g lateral force.

**Source:** `app/services/diagnostic_evaluator.py` — `_evaluate_cornering()` method

### Jerky Steering — Rotation Rate Change

**What it measures:** Abrupt changes in the phone's gyroscope Z-axis rotation (yaw rate), indicating inconsistent steering inputs.

**Calculation:** Compares consecutive gyroscope Z-rotation readings:

```python
rotation_change = abs(current_rotation_z - previous_rotation_z)
```

**Threshold:** A change exceeding 0.5 rad/s between consecutive samples triggers a jerky steering event (-5 pts).

**Source:** `app/services/diagnostic_evaluator.py` — `_evaluate_cornering()` method

### Lane Discipline — Heading Stability

**What it measures:** How steadily the driver maintains their lane, measured via GPS heading (compass bearing) variance over 10-sample windows.

**Calculation:** Standard deviation of heading values within each window:

```python
std_dev = sqrt(sum((heading - avg_heading)² for heading in window) / len(window))
```

**Thresholds:**

| Heading Std Dev | Classification | Scoring Impact |
|----------------|---------------|----------------|
| < 3.0° | Good lane discipline | No penalty |
| 3.0 - 5.0° | Fair (some wandering) | -1 pt per weaving instance |
| > 5.0° | Poor (excessive weaving) | -2 pts per instance (max -15) |

**Source:** `app/services/diagnostic_evaluator.py` — `_evaluate_cornering()` method

### Device-Detected Event Mapping (F-Codes)

Sensor events detected during live evaluation are automatically mapped to unified fault codes for the feedback system:

| Event Type | F-Code | Label | Triggered By |
|-----------|--------|-------|-------------|
| `harsh_braking` | F1 | Harsh Braking | Deceleration > 0.6g (Y-axis only) |
| `speeding` | F2 | Speeding | Speed > posted limit + 5 km/h tolerance (sustained) |
| `sharp_turn` | F3 | Sharp Turn | Lateral force > dynamic threshold + GPS heading confirms turn |
| `sudden_stop` | F4 | Sudden Stop | Speed drop > 10 km/h in sampling window |

These F-codes appear alongside human-flagged criteria (A1-E4) in the unified feedback report.

**Source:** `mobile-app/src/data/faults.js` — `DEVICE_EVENT_TO_CODE`

### Sampling & Data Collection

| Sensor | Sample Rate | Data Collected |
|--------|-------------|----------------|
| Accelerometer | 10 Hz | X, Y, Z acceleration (m/s²) |
| Gyroscope | 10 Hz | X, Y, Z rotation (rad/s) |
| GPS | 1 Hz | Latitude, longitude, speed (km/h), heading (°) |
| Speed limits | On GPS update | Posted limit (km/h), zone type, road type |

**Live evaluation:** Every 2 seconds, the app sends the latest 3 seconds of accelerometer data (30 samples) and 3 seconds of speed data (3 samples) to the `/diagnostic-rides/live-evaluate` endpoint for real-time event detection and alerts.

**Final evaluation:** On ride completion, all collected sensor data is submitted to the backend where `DiagnosticEvaluator.evaluate()` processes the full dataset for comprehensive scoring.

## Key Files Created

### Backend
- `app/services/diagnostic_evaluator.py` (380 lines) - Evaluation algorithm
- `app/api/diagnostic_rides.py` (385 lines) - Diagnostic ride endpoints
- `app/api/modules.py` (180 lines) - Module management endpoints
- `seed_modules.py` (80 lines) - Database seeding script

### Mobile App
- `mobile-app/src/hooks/useDeviceMotion.js` (110 lines) - Sensor hook
- `mobile-app/src/hooks/useGPSTracking.js` (170 lines) - GPS tracking hook
- `mobile-app/src/screens/DiagnosticRideIntroScreen.js` (280 lines)
- `mobile-app/src/screens/DiagnosticRideSetupScreen.js` (220 lines)
- `mobile-app/src/screens/DiagnosticRideActiveScreen.js` (300 lines)
- `mobile-app/src/screens/DiagnosticRideResultsScreen.js` (280 lines)
- `mobile-app/src/screens/ModulesScreen.js` (260 lines)
- `mobile-app/src/components/ScoreGauge.js` (65 lines)

## Testing Checklist

### Backend Testing
- [ ] Start backend: `uvicorn app.main:app --reload`
- [ ] Run seed script: `python seed_modules.py`
- [ ] Test module endpoints:
  - GET `/api/v1/modules/` - List modules
  - GET `/api/v1/modules/student-progress` - Get progress
- [ ] Test diagnostic ride endpoints:
  - POST `/api/v1/diagnostic-rides/` - Create ride
  - POST `/api/v1/diagnostic-rides/{id}/evaluate` - Evaluate

### Mobile Testing (Physical Device Required!)
- [ ] Install dependencies: `npm install`
- [ ] Run app: `npm start` + scan QR code
- [ ] Navigate: Home → Diagnostic Ride
- [ ] Test parent-supervised flow
- [ ] Test ride tracking (GPS + sensors)
- [ ] Complete 20+ min, 5+ km ride
- [ ] View results
- [ ] Check module unlocking

## Architecture Decisions

1. **Hybrid Pricing**: Package (discounted) vs hourly (flexible)
2. **Full Sensor Integration**: GPS + Accelerometer + Gyroscope
3. **Automated Evaluation**: Algorithm-based with instructor override
4. **Progressive Module System**: Unlock based on prerequisites

## Business Impact

### Student Value
- **Pass Diagnostic**: Save $800 by skipping Basics
- **Retry Option**: Can retry with instructor for $120 (total savings $680)
- **Flexible Enrollment**: Choose package (discounted) or hourly

### Platform Revenue
- **Instructor Diagnostic Rides**: ~$120 each
- **Module Packages**: Basics $800, Advanced $600
- **Hourly Lessons**: $75-80/hour
- Students who skip Basics still pay for Advanced ($600)

### Expected Metrics
- 60-70% adoption rate for diagnostic rides
- 30-40% pass rate (maintains quality bar)
- $680-800 average savings for passing students
- Platform maintains revenue through Advanced module

## Next Steps / Future Enhancements

1. **Real-time Feedback**: Audio cues during ride ("Harsh braking detected")
2. **Video Recording**: Optional dash cam integration
3. **Weather Adjustment**: Factor in conditions
4. **Route Difficulty**: Adjust scoring based on complexity
5. **Analytics Dashboard**: Detailed performance comparisons
6. **Insurance Integration**: Share results for discounts

## Troubleshooting

### Sensors Not Working
- **Problem**: Accelerometer/gyroscope returning zeros
- **Solution**: Must use physical device (not simulator)

### GPS Inaccurate
- **Problem**: Location jumps around
- **Solution**: Ensure Location.Accuracy.High, test outdoors

### Evaluation Fails
- **Problem**: Ride completed but evaluation errors
- **Solution**: Check sensor data format, ensure JSON is valid

### Module Not Unlocking
- **Problem**: Passed diagnostic but Advanced still locked
- **Solution**: Check `StudentModuleProgress` status in database

## Support

For issues or questions:
1. Check backend logs: Look for evaluation errors
2. Check mobile console: Look for API errors
3. Verify database: Check `diagnostic_rides`, `student_module_progress` tables
4. Test with seed data: Use `seed_modules.py` to reset

## Summary

✅ **Complete Implementation**: All 20 tasks across 3 phases completed
✅ **Backend**: Models, API endpoints, evaluation algorithm
✅ **Mobile**: Sensor hooks, 5 screens, navigation integration
✅ **Ready to Test**: Just needs `npm install` + physical device

Total Implementation:
- **Backend**: ~1,000 lines of Python code
- **Mobile**: ~1,800 lines of JavaScript/React Native code
- **Time Estimate**: Full testing and refinement ~2-3 hours

The diagnostic ride feature is now fully implemented and ready for testing on a physical device!
