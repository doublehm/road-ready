# Road Ready Diagnostic Ride Sensor Evaluation System - Complete Analysis

## Executive Summary

The Road Ready diagnostic ride system is a comprehensive real-time and post-ride evaluation framework that uses smartphone sensors (accelerometer, gyroscope, GPS) combined with OSM speed limit data to assess driving proficiency. The system allows students to demonstrate advanced driving skills and potentially skip the Basics module, saving $800.

---

## 1. BACKEND EVALUATOR - DiagnosticEvaluator Service

**Location:** `/home/Hoss/Documents/road-ready/app/services/diagnostic_evaluator.py` (1,028 lines)

### 1.1 Scoring Architecture

The overall score is a weighted combination of four categories:

| Category | Weight | Threshold (Pass) | Description |
|----------|--------|------------------|-------------|
| Braking | 30% | ≥70/100 | Smooth stops, avoiding harsh braking |
| Speed | 30% | ≥70/100 | Speed limit compliance and consistency |
| Cornering | 20% | ≥70/100 | Smooth turns with controlled acceleration |
| Smoothness (Jerk) | 20% | ≥70/100 | Overall smoothness of movements |

**Pass Criteria:**
- Overall score ≥ 75/100
- Each individual category ≥ 70/100
- Ride duration ≥ 20 minutes
- Distance traveled ≥ 5 km

### 1.2 Physical Constants

```python
GRAVITY = 9.81  # m/s² - used to normalize accelerometer readings
```

### 1.3 HARSH BRAKING DETECTION

**Thresholds:**
```python
HARSH_BRAKING_THRESHOLD = 0.6  # g (gravitational force)
SMOOTH_BRAKING_MIN = 0.1  # g minimum for smooth braking bonus
SMOOTH_BRAKING_MAX = 0.4  # g maximum for smooth braking bonus
SUDDEN_STOP_SPEED_DROP = 10  # km/h drop within sampling window
EVENT_COOLDOWN_MS = 3000  # 3 seconds between events of same type
```

**Algorithm (in `_evaluate_braking()`):**

1. **Gravity Compensation**: Accounts for incline using GPS altitude
   - True forward acceleration = Measured Y-axis + g×sin(incline angle)
   - Prevents false positives on uphills

2. **Deceleration Detection**: Uses calibrated vehicle frame
   - Y-axis (forward) acceleration is extracted via orientation matrix
   - Negative Y-accel indicates braking
   - Magnitude relative to gravity: `deceleration_g = |true_y_accel| / 9.81`

3. **Event Flagging**:
   - If `deceleration_g > 0.6g`: **Harsh Braking** event (penalty: -10 points, max -15 for sudden stops)
   - If `deceleration_g ∈ [0.1g, 0.4g]`: **Smooth Braking** event (bonus: +2 points per event, max +20)
   - Cooldown: Events are throttled to prevent one 2-second braking action from generating 20 events

4. **Sudden Stop Detection**: Speed-based fallback
   - If speed drops > 10 km/h within ≤1.5 seconds: Sudden stop flagged
   - Penalty: -15 points per event
   - Same 3-second cooldown applied

**Feedback Output:**
- `harsh_braking_events`: Count of events
- `smooth_braking_events`: Count of events
- `sudden_stops`: Count of events
- Events list with timestamp, location, g-force value, severity (high/medium)

---

### 1.4 SPEEDING DETECTION

**Thresholds:**
```python
RESIDENTIAL_LIMIT = 50  # km/h (fallback)
ARTERIAL_LIMIT = 60    # km/h (fallback)
HIGHWAY_LIMIT = 80     # km/h (fallback)
CONSECUTIVE_SPEEDING_REQUIRED = 3  # data points to confirm speeding
SCHOOL_ZONE_PENALTY_MULTIPLIER = 2.0
```

**Algorithm (in `_evaluate_speed()`):**

1. **Speed Limit Resolution** (priority order):
   - Inline speed_limit in speed data point
   - Nearest speed_limit_data entry (matched by timestamp within 60ms or within 60s)
   - Fallback: Inferred from average speed (avg < 40 km/h → 50 km/h residential, etc.)

2. **Speed Limit Sources** (see SpeedLimitService section):
   - OSM Overpass API maxspeed tag (cached, 24h TTL)
   - BC school zone detection (30 km/h Mon-Fri 8am-5pm)
   - BC provincial defaults by road type

3. **Speeding Validation**:
   - Requires **3 consecutive data points** over limit before flagging (filters OSM glitches)
   - **Exception**: School zones flag single-point violations immediately
   - Excess = `speed - speed_limit`

4. **Event Flagging**:
   - If `excess > 0`: Record as speeding
   - Emit event only if:
     - `excess > 5 km/h` AND
     - (3+ consecutive points OR in school zone) AND
     - (every 5th point OR school zone)
   - School zone violations: **2x penalty multiplier** on score reduction

5. **Scoring**:
   - Base penalty: `speeding_percentage × 2` points deducted
   - School zone penalty: `school_zone_violations × 0.5` points deducted
   - Under-speed penalty: `under_speed_percentage × 1` point deducted (speed > 5 and < limit - 10)
   - Variance penalty: Average speed change × 2 (min 20 points)

**Feedback Output:**
- `speeding_percentage`: % of ride time spent speeding
- `under_speed_percentage`: % of ride time spent too slow
- `school_zone_violations`: Count of violations
- `max_excess_kmh`: Maximum km/h over limit
- `speed_violations`: List of violations with start timestamp, zone type, duration, max speed

---

### 1.5 CORNERING (SHARP TURN) DETECTION

**Thresholds:**
```python
SHARP_TURN_LATERAL_THRESHOLD = 0.45  # g lateral acceleration
SMOOTH_TURN_THRESHOLD = 0.2  # g (bonus threshold)
CORNERING_SPEED_SENSITIVITY = 0.002  # g reduction per km/h
EVENT_COOLDOWN_MS = 3000  # 3 seconds
JERKY_STEERING_RATE_THRESHOLD = 1.0  # rad/s
```

**Algorithm (in `_evaluate_cornering()`):**

1. **Lateral Acceleration Extraction**:
   - X-axis (lateral) from accelerometer: `lateral_g = |x_accel| / 9.81`
   - Extracted from calibrated vehicle frame

2. **Speed-Dependent Threshold**:
   ```
   dynamic_threshold = 0.45 - (speed_kmh × 0.002)
   dynamic_threshold = max(0.15, dynamic_threshold)  # floor at 0.15g
   ```
   - At 20 km/h: 0.45g threshold (normal city corner)
   - At 100 km/h: 0.25g threshold (higher speeds require gentler turns)
   - Prevents false positives at low speeds

3. **Event Flagging**:
   - If `lateral_g > dynamic_threshold`: **Sharp Turn** event
   - Penalty: `-8 × (1 + speed_kmh/50)` points (speed-scaled)
   - If `lateral_g ∈ [0.02g, 0.2g]`: **Smooth Turn** (bonus: +2 points, max +20)
   - Cooldown: 3-second throttle per turn

4. **Jerky Steering Detection**:
   - Z-axis (yaw) from gyroscope
   - If `|Δyaw| > 1.0 rad/s` between samples: **Jerky Steering**
   - Penalty: -5 points per occurrence

**Feedback Output:**
- `sharp_turns`: Count of events
- `smooth_turns`: Count of events
- `jerky_steering`: Count of events
- Events list with timestamp, location, g-force, speed, severity

---

### 1.6 ADDITIONAL FAULT DETECTIONS

#### 1.6.1 Friction Circle Violation (Combined Maneuvers)

**Threshold:**
```python
FRICTION_CIRCLE_THRESHOLD = 0.6  # g total vector magnitude
```

**Algorithm (in `_evaluate_combined_dynamics()`):**

- Calculates magnitude: `total_g = √(x² + y²) / 9.81`
- Detects trail-braking into turns or other dangerous combined forces
- Event: If `total_g > 0.6g`
- Penalty: -12 points per violation
- Warns: "Avoid heavy braking while turning. Complete braking in straight line before corner."

#### 1.6.2 Vertical Impact Detection (Potholes, Speed Bumps)

**Threshold:**
```python
VERTICAL_IMPACT_THRESHOLD = 0.4  # g variance from gravity
```

**Algorithm (in `_evaluate_vertical_impacts()`):**

- Z-axis variance: `z_variance_g = |z_accel - GRAVITY| / GRAVITY`
- Relative to calibrated gravity (usually ~9.81 m/s²)
- Event: If `z_variance_g > 0.4g`
- Penalty: -5 points per impact
- Cooldown: 1 second (separate from other events)

#### 1.6.3 Smoothness Score (Jerk Analysis)

**Thresholds:**
```python
JERK_SMOOTH_THRESHOLD = 2.0  # m/s³
JERK_HARSH_THRESHOLD = 6.0   # m/s³
```

**Algorithm (in `_calculate_jerk_score()`):**

- Jerk = rate of change of acceleration: `|Δaccel| / Δt`
- For each consecutive acceleration sample pair
- Scoring: `100 - (avg_jerk × 2) - (high_jerk_ratio × 50)`
  - `high_jerk_ratio` = % of samples where jerk > 6.0 m/s³
- Result: 0-100 score

---

### 1.7 Orientation Calibration (Vehicle Frame Alignment)

**Purpose**: Aligns smartphone accelerometer to vehicle coordinate system

**Method (in `_calibrate_orientation()`):**

1. **Gravity Vector** (Z-axis, Up):
   - Average acceleration over first ~3000 samples (30 seconds at 10 Hz)
   - Normalized to unit vector (points "up")

2. **Forward Vector** (Y-axis):
   - Finds window where GPS speed increasing (accel > 0.5 m/s²)
   - Uses accelerometer samples at that timestamp
   - Removes gravity component
   - Normalized to unit vector

3. **Right Vector** (X-axis):
   - Cross product: `x_basis = cross(y_basis, z_basis)`
   - Ensures orthogonal frame

4. **Output**: 3×3 rotation matrix
   - If calibration fails (insufficient data): Falls back to heuristic (assumes phone is upright/facing forward)

---

### 1.8 Data Processing Pipeline

```
Raw acceleration data (3 axes)
    ↓
[Remove gravity via low-pass filter: α=0.8]
    ↓
[Apply orientation matrix to get vehicle frame]
    ↓
[Extract Y-axis (braking), X-axis (cornering), Z-axis (impacts)]
    ↓
[Compare to thresholds with cooldown throttle]
    ↓
Events + penalties + bonuses
    ↓
Scores (braking, speed, cornering, smoothness)
    ↓
Weighted overall score
    ↓
Pass/fail determination
```

---

## 2. MOBILE SENSOR HOOKS

### 2.1 `useDeviceMotion` Hook

**Location:** `/home/Hoss/Documents/road-ready/mobile-app/src/hooks/useDeviceMotion.js` (169 lines)

**Purpose**: Collects accelerometer and gyroscope data using expo-sensors

**Configuration:**
```javascript
const DATA_WINDOW = 600;        // ~60 seconds at 10 Hz (rolling buffer)
const STATE_THROTTLE_MS = 500;  // Limits React state updates (but full data to refs)
const sampleRate = 10;          // Hz (100 ms intervals)
```

**Data Collection:**

1. **Accelerometer** (via `Accelerometer` from expo-sensors):
   - Updates every 100 ms (10 Hz)
   - Raw values in Gs (1g = 9.81 m/s²)
   - **Gravity Filtering**: Low-pass filter with α=0.8
     ```javascript
     gravityRef = α * gravity + (1-α) * accelData  // α=0.8
     userAccel = (accelData - gravity) * 9.81 // Convert back to m/s²
     ```

2. **Gyroscope** (via `Gyroscope` from expo-sensors):
   - Updates every 100 ms (10 Hz)
   - Values in rad/s (rotation rates around x, y, z axes)

3. **Data Accumulation**:
   - Full-rate data stored in `dataRef.current` (rolling buffer of 600 samples)
   - State update throttled to every 500 ms to prevent excessive re-renders
   - State stores only last 100 samples for UI display

**Output:**
```javascript
{
  isTracking: bool,
  acceleration: { x, y, z } // m/s² (gravity-corrected)
  rotation: { x, y, z }     // rad/s
  data: [                   // Array of recent points
    { timestamp, acceleration, rotation },
    ...
  ],
  startTracking, stopTracking, clearData
}
```

---

### 2.2 `useGPSTracking` Hook

**Location:** `/home/Hoss/Documents/road-ready/mobile-app/src/hooks/useGPSTracking.js` (197 lines)

**Purpose**: Continuous GPS location tracking with speed and distance calculation

**Configuration:**
```javascript
const GPS_STATE_THROTTLE_MS = 2000;  // Update UI every 2 seconds
// Position watching:
accuracy: Location.Accuracy.High,   // Best accuracy
timeInterval: 1000,                  // Update every 1 second
distanceInterval: 10,                // Update every 10 meters (was 5 — halves highway rate)
```

**Speed Calculation:**
```javascript
// GPS provides speed in m/s; convert to km/h
speedKmh = gpsSpeed * 3.6

// If GPS speed unavailable: 0 (fallback)
```

**Distance Calculation** (using Haversine formula):
```javascript
function calculateDistance(coord1, coord2) {
  const R = 6371;  // Earth radius in km
  const Δlat = (lat2 - lat1) × π/180
  const Δlon = (lon2 - lon1) × π/180
  const a = sin²(Δlat/2) + cos(lat1) × cos(lat2) × sin²(Δlon/2)
  const c = 2 × atan2(√a, √(1-a))
  return R × c
}
```

**Data Accumulation:**
- Full-rate tracking in `routeRef.current` (all coordinates)
- Full-rate in `speedDataRef.current` (all GPS fixes)
- State update throttled to 2 seconds (only last 200 coordinates for map)
- Stores: timestamp, speed, latitude, longitude, heading

**Output:**
```javascript
{
  isTracking: bool,
  location: { latitude, longitude, altitude, heading },
  speed: 0,                // km/h (current)
  routeCoordinates: [],    // [{ latitude, longitude }, ...]
  distance: 0,             // km (cumulative)
  speedData: [],           // [{ timestamp, speed, latitude, longitude, heading }, ...]
  startTracking, stopTracking, clearData
}
```

---

### 2.3 `useSpeedLimit` Hook

**Location:** `/home/Hoss/Documents/road-ready/mobile-app/src/hooks/useSpeedLimit.js` (185 lines)

**Purpose**: Queries and caches speed limits from backend API, applies smoothing to prevent glitches

**Configuration:**
```javascript
const QUERY_DISTANCE_THRESHOLD = 100;        // meters moved
const QUERY_TIME_THRESHOLD = 30000;          // 30 seconds
const DRAMATIC_CHANGE_THRESHOLD = 20;        // km/h - changes larger trigger validation
const SMOOTHING_HISTORY_SIZE = 5;            // Recent readings to keep
const DRAMATIC_CHANGE_MIN_AGREE = 3;         // How many of the 5 must agree
```

**Query Throttling**:
- Only queries when: moved ≥100m OR 30 seconds elapsed
- Stores last query location + timestamp

**Client-Side Smoothing** (filters OSM glitches):

1. **Problem**: On-ramp nearby highway may briefly show 50 km/h limit tag next to 100 km/h highway
2. **Solution**: Smoothing with validation
   ```
   If |newLimit - confirmedLimit| > 20 km/h (dramatic change):
     Count recent readings within 15 km/h of new limit
     If < 3 of last 5 readings agree:
       REJECT change, keep confirmed limit
     Else:
       ACCEPT change, update confirmed limit
   ```

3. **Applied Result**: Smoothed limit stored in state and speed_limit_data array

**Data Accumulation**:
- `speedLimitDataRef.current`: Array of all queried limits
  ```javascript
  {
    timestamp,
    latitude, longitude,
    speed_limit: (smoothed value),
    road_type, road_name,
    zone_type: 'regular' or 'school',
    source: 'osm', 'osm_conditional', 'bc_default', 'school_proximity', 'default'
  }
  ```

**Output:**
```javascript
{
  currentSpeedLimit: 50,      // km/h (smoothed)
  roadName: "Main Street",
  roadType: "secondary",
  zoneType: 'regular' or 'school',
  isLoading: bool,
  getSpeedLimitData: () => [...],  // Get all historical records
  clearData: () => {}
}
```

---

## 3. MOBILE DIAGNOSTIC RIDE ACTIVE SCREEN

**Location:** `/home/Hoss/Documents/road-ready/mobile-app/src/screens/DiagnosticRideActiveScreen.js` (1,338 lines)

### 3.1 Real-Time Event Detection Loop

**Feedback Interval**: Every 2 seconds while ride is active

```javascript
const feedbackInterval = setInterval(async () => {
  const motionData = latestMotionDataRef.current.slice(-30);     // 10Hz × 3s
  const speedData = latestSpeedDataRef.current.slice(-3);        // 1Hz × 3s
  const rotationWindow = motionData.map(p => ({ timestamp, x, y, z }));
  
  // POST to /diagnostic-rides/live-evaluate
  const response = await client.post('/diagnostic-rides/live-evaluate', {
    acceleration_window: accelerationWindow,
    speed_window: speedWindow,
    rotation_window: rotationWindow,
  });
  
  // Process events returned from backend
  if (response.data.events && response.data.events.length > 0) {
    // Update UI feedback counts
    events.forEach(event => {
      const mapping = DEVICE_EVENT_TO_CODE[event.type];
      handleFeedbackUpdate(mapping.code, 1, metadata);
    });
    
    // Rate-limit alerts to once per 15 seconds per event type
    triggerAlert(mostSevereEvent);
    
    // Stream to backend WebSocket for live dashboard
    sendMessage({ type: 'event', data: event });
  }
}, 2000);
```

**Event Processing Chain**:
1. Mobile app accumulates sensor data in rolling buffers
2. Every 2 seconds, small window (3 seconds motion, 3 seconds speed) sent to backend
3. Backend runs `live-evaluate` endpoint (smaller thresholds for snappy feedback)
4. Events returned and:
   - Auto-mapped to feedback codes (F1-F4)
   - Streamed via WebSocket to live dashboard
   - Alerted to user (rate-limited: once per 15s per event type)
   - Accumulated in `allEvents` array (capped at 300 to prevent memory leak)

**Alert Conditions**:
```javascript
const getSpeedColor = () => {
  if (!speedLimit.currentSpeedLimit || !gpsTracking.speed) return '#1a1a1a';
  const excess = gpsTracking.speed - speedLimit.currentSpeedLimit;
  if (excess > 10) return '#dc3545';   // red
  if (excess > 0) return '#ffc107';    // yellow
  return '#28a745';                    // green
};

// Alert triggers
if (event.type === 'speeding') {
  if (zoneType === 'school') {
    msg = 'SCHOOL ZONE - Slow Down!';
    detail = `Speed limit is ${limit} km/h`;
  } else {
    msg = 'Reduce Speed';
    detail = `${speed} km/h in a ${limit} km/h zone (+${excess} km/h over)`;
  }
} else if (event.type === 'harsh_braking') {
  msg = 'Braking Too Hard';
  detail = `${value}g force detected. Apply brake gradually.`;
} else if (event.type === 'sharp_turn') {
  msg = 'Turn More Smoothly';
  detail = `${value}g lateral force. Reduce speed before turns.`;
}
```

### 3.2 Data Flow to Backend

**During Ride:**
1. Sensors stream data via ref updates (full rate, no throttle)
2. Every 2 seconds: Snapshot sent to `/diagnostic-rides/live-evaluate`
3. Events streamed to WebSocket in real-time
4. Telemetry persisted via `/diagnostic-rides/{ride_id}/telemetry` (HTTP) or WebSocket fallback

**Ride Completion:**
- All data (acceleration, rotation, speed, speedLimit data) packaged
- POST to `/diagnostic-rides/{ride_id}/evaluate`
- Backend runs full evaluation with all thresholds

---

## 4. WEBSOCKET AND API ENDPOINTS

**Location:** `/home/Hoss/Documents/road-ready/app/api/diagnostic_rides.py` (940 lines)

### 4.1 Speed Limit Endpoint

**Route:** `GET /diagnostic-rides/speed-limit`

**Parameters:**
```
lat: float
lon: float
```

**Returns:**
```json
{
  "speed_limit_kmh": 50,
  "source": "osm" | "osm_conditional" | "bc_default" | "school_proximity" | "default",
  "road_name": "Main Street",
  "road_type": "secondary",
  "zone_type": "regular" | "school"
}
```

**Implementation:** Delegates to `SpeedLimitService.get_speed_limit(lat, lon)`

---

### 4.2 Live Telemetry WebSocket

**Route:** `@router.websocket("/{ride_id}/stream/{client_type}")`

**Purpose**: Real-time bidirectional data streaming between mobile app and backend

**Message Types**:

1. **Telemetry**: Raw sensor data
   ```json
   {
     "type": "telemetry",
     "data": {
       "timestamp": 1234567890,
       "acceleration": { "x": 0.1, "y": -0.5, "z": 9.8 },
       "rotation": { "x": 0, "y": 0, "z": 0.01 },
       "speed": 45.2,
       "latitude": 49.2827,
       "longitude": -123.1207
     }
   }
   ```

2. **Event**: Detected fault/issue
   ```json
   {
     "type": "event",
     "data": {
       "type": "harsh_braking",
       "timestamp": 1234567890,
       "value": 0.75,
       "severity": "high",
       "description": "Harsh braking at 0.75g force",
       "location": { "latitude": 49.2827, "longitude": -123.1207 }
     }
   }
   ```

**Persistence**: Messages persisted to NoSQL (MongoDB) via `manager.persist_data()`
- Telemetry chunks stored for later evaluation
- Events stored for event timeline

---

### 4.3 Live Evaluate Endpoint

**Route:** `POST /diagnostic-rides/live-evaluate`

**Purpose**: Evaluate a small window of sensor data for real-time feedback

**Request:**
```json
{
  "acceleration_window": [
    { "timestamp": 1234567890, "x": 0.1, "y": -0.5, "z": 9.8, "latitude": 49.28, "longitude": -123.12 },
    ...
  ],
  "speed_window": [
    { "timestamp": 1234567890, "speed": 45.2, "latitude": 49.28, "longitude": -123.12, "speed_limit": 50 },
    ...
  ],
  "rotation_window": [
    { "timestamp": 1234567890, "x": 0, "y": 0, "z": 0.01 },
    ...
  ]
}
```

**Response:**
```json
{
  "events": [
    {
      "type": "harsh_braking",
      "timestamp": 1234567890,
      "lat": 49.2827,
      "lng": -123.1207,
      "value": 0.75,
      "severity": "high",
      "description": "..."
    }
  ],
  "timestamp": "2026-02-25T12:34:56"
}
```

**Algorithm**:
- Calls `evaluator._evaluate_braking()`, `_evaluate_speed()`, `_evaluate_cornering()`
- **Does NOT call full `evaluate()` method** (no scoring, just event detection)
- Collects events from all three sub-evaluations
- Returns sorted by timestamp

---

### 4.4 Evaluate (Complete) Endpoint

**Route:** `POST /diagnostic-rides/{ride_id}/evaluate`

**Purpose**: Perform full evaluation of completed ride

**Process**:

1. **Fetch Telemetry**: From NoSQL (MongoDB) via `nosql_repo.get_ride_telemetry(ride_id)`

2. **Run Full Evaluator**:
   ```python
   evaluator = DiagnosticEvaluator()
   evaluation_result = await evaluator.evaluate(ride_id, ride_data)
   ```

3. **Extract Scores**:
   ```python
   ride.braking_score = evaluation_result['braking_score']
   ride.speed_score = evaluation_result['speed_score']
   ride.cornering_score = evaluation_result['cornering_score']
   ride.smoothness_score = evaluation_result['smoothness_score']
   ride.overall_score = evaluation_result['overall_score']
   ride.passed = evaluation_result['passed']
   ```

4. **Module Unlocking**:
   - If `passed == True`:
     - Set `student.diagnostic_completed = True`
     - Set `student.basics_skipped = True`
     - Unlock Advanced module, keep Basics locked
   - If `passed == False`:
     - Unlock Basics module

5. **Return**: Evaluation result with scores and pass/fail status

---

### 4.5 Telemetry HTTP Endpoint (Fallback)

**Route:** `POST /diagnostic-rides/{ride_id}/telemetry`

**Purpose**: Reliable HTTP fallback when WebSocket unavailable

**Request:**
```json
[
  { "timestamp": ..., "acceleration": {...}, "rotation": {...}, "speed": ..., "latitude": ..., "longitude": ... },
  ...
]
```

**Response:**
```json
{
  "status": "persisted",
  "count": 150
}
```

---

## 5. SPEED LIMIT SERVICE (OSM/Overpass Integration)

**Location:** `/home/Hoss/Documents/road-ready/app/services/speed_limit_service.py` (265 lines)

### 5.1 Speed Limit Resolution Hierarchy

**Process in `get_speed_limit(lat, lon)`**:

1. **Check Cache**: 
   - Key: `(round(lat, 3), round(lon, 3))`  (~100m precision)
   - TTL: 24 hours

2. **Query Overpass API**:
   - Query radius: 50 meters
   - Looks for ways with `highway` tag and optionally `maxspeed` tag
   - Timeout: 5 seconds

3. **Best Road Selection** (`_best_road()`):
   - **Priority**: Higher-class roads win
   - **Strategy**: Among roads with maxspeed tags, pick highest-class road type
   - **Fallback**: If no maxspeed, use highest-class road overall
   - **Rationale**: Prevents a 50 km/h on-ramp nearby a 100 km/h highway from "winning"

**Road Type Priority** (lower to higher):
```
0: service
1: living_street
2: residential
3: unclassified
4: tertiary_link
5: tertiary
6: secondary_link
7: secondary
8: primary_link
9: primary
10: trunk_link
11: trunk
12: motorway_link
13: motorway (highest)
```

### 5.2 Speed Limit Defaults (BC Provincial)

```python
BC_DEFAULTS = {
    "motorway": 120,
    "motorway_link": 60,
    "trunk": 80,
    "trunk_link": 60,
    "primary": 80,
    "primary_link": 60,
    "secondary": 60,
    "secondary_link": 50,
    "tertiary": 50,
    "tertiary_link": 50,
    "residential": 50,
    "living_street": 30,
    "unclassified": 50,
    "service": 20,
}
```

### 5.3 School Zone Detection

**Conditions**:
- Time: Monday-Friday, 8am-5pm
- Location: Within 200m of school (via OSM amenity=school)

**Applied Limit**: 30 km/h (BC_SCHOOL_ZONE_LIMIT)

**Priority**: 
- If OSM maxspeed:conditional tag found with "school": Use that value
- Else if near school during school hours: Apply 30 km/h

### 5.4 Maxspeed Parsing

Supports formats:
- Plain integer: `"100"` → 100 km/h
- Unit suffix: `"50 mph"` → 80.5 km/h (×1.60934 conversion)
- Fallback: Return None if unparseable

---

## 6. FAULT CODE SYSTEM (A-F Categories)

**Location:** `/home/Hoss/Documents/road-ready/mobile-app/src/data/faults.js`

### Categories and Codes

| Code | Category | Label |
|------|----------|-------|
| **A Series** | Observation | Shoulder Check, Scan, Mirror Check, 360° Check, Direction of Travel, Backing, Hazard Perception, Other |
| **B Series** | Space Margins | Lane Position, Follow Distance, Stops Too Close/Far, Gap, Blocks Crosswalk, Turn Position, Occupied Crosswalk, Manoeuvre Location, Other, Stop Position, Road Position, 3-Point/U-Turn, Parking Margins, Railroad Crossing |
| **C Series** | Speed | Speed Maintenance, Rolling Stop, Amber Light, Accel/Decel, Shifting, Rolling Back, Other, Covers Brakes, Parking Brake |
| **D Series** | Steering | General Steering, Other, Steering Wheel Position, Weight Transfer |
| **E Series** | Communication | Signal, Timing, Cancel, Other |
| **F Series** | Device Detected | Harsh Braking (F1), Speeding (F2), Sharp Turn (F3), Sudden Stop (F4) |

### Device Event to Code Mapping

```javascript
DEVICE_EVENT_TO_CODE = {
  harsh_braking: { code: 'F1', label: 'Harsh Braking', category: 'F' },
  speeding: { code: 'F2', label: 'Speeding', category: 'F' },
  sharp_turn: { code: 'F3', label: 'Sharp Turn', category: 'F' },
  sudden_stop: { code: 'F4', label: 'Sudden Stop', category: 'F' },
};
```

**Auto-mapping**: When sensor detects event → automatically logged as corresponding F-code

---

## 7. DATA FORMAT SPECIFICATIONS

### 7.1 Acceleration Data Point

```json
{
  "timestamp": 1708867200000,        // milliseconds since epoch
  "x": 0.05,                          // m/s² (lateral, after gravity removal)
  "y": -0.6,                          // m/s² (forward, negative = braking)
  "z": 9.81,                          // m/s² (vertical, ~9.81 at rest)
  "latitude": 49.2827,                // optional
  "longitude": -123.1207              // optional
}
```

### 7.2 Speed Data Point

```json
{
  "timestamp": 1708867200000,        // milliseconds since epoch
  "speed": 45.2,                      // km/h
  "latitude": 49.2827,
  "longitude": -123.1207,
  "heading": 180.5,                   // degrees (0-360)
  "speed_limit": 50                   // optional, from speed limit hook
}
```

### 7.3 Speed Limit Data Point

```json
{
  "timestamp": 1708867200000,
  "latitude": 49.2827,
  "longitude": -123.1207,
  "speed_limit": 50,
  "road_type": "secondary",
  "road_name": "Main Street",
  "zone_type": "regular",             // or "school"
  "source": "osm"                     // or "osm_conditional", "bc_default", "school_proximity", "default"
}
```

### 7.4 Event Output

```json
{
  "type": "harsh_braking",           // Event type identifier
  "timestamp": 1708867200000,
  "lat": 49.2827,
  "lng": -123.1207,
  "value": 0.75,                      // Event-specific value (g-force, km/h, etc.)
  "severity": "high",                 // "high" or "medium"
  "description": "Harsh braking at 0.75g force",
  "limit": 50,                        // For speeding events
  "excess": 5,                        // For speeding events
  "zone_type": "regular",             // For speeding events
  "speed": 45.2                       // For turn/corner events
}
```

---

## 8. COMPLETE SENSOR-TO-EVALUATION PIPELINE

```
┌─────────────────────────────────────────────────────────────────┐
│                      MOBILE DEVICE                               │
├─────────────────────────────────────────────────────────────────┤
│
│  1. SENSOR DATA COLLECTION (Continuous, Full Rate)
│  ├─ Accelerometer: 10 Hz → gravityRef + userAccel (m/s²)
│  ├─ Gyroscope: 10 Hz → rotation (rad/s)
│  ├─ GPS: ~1 Hz → speed (m/s→km/h), location, distance (Haversine)
│  └─ Speed Limit: On movement (≥100m or 30s) → hook queries backend
│
│  2. REAL-TIME FEEDBACK LOOP (Every 2 Seconds)
│  ├─ Snapshot: Last 30 motion points (3s @ 10Hz)
│  ├─ Snapshot: Last 3 speed points (3s @ 1Hz)
│  └─ POST to /diagnostic-rides/live-evaluate
│
│  3. UI ALERTS (Rate-limited: 1 per 15s per event type)
│  ├─ Map event to F-code
│  ├─ Increment feedback badge
│  └─ Show alert (Reduce Speed, Braking Too Hard, etc.)
│
│  4. DATA ACCUMULATION
│  ├─ Full sensor data in rolling buffers (600 samples motion, unlimited speed)
│  ├─ Speed limit data in hook (all historical entries)
│  └─ Coach notes (human feedback)
│
│  5. RIDE COMPLETION
│  └─ Package all data + metadata and POST to /diagnostic-rides/{id}/evaluate
│
└─────────────────────────────────────────────────────────────────┘
                                │
                                ↓
┌─────────────────────────────────────────────────────────────────┐
│                      BACKEND (API)                               │
├─────────────────────────────────────────────────────────────────┤
│
│  1. SPEED LIMIT LOOKUP (Via SpeedLimitService)
│  ├─ Query Overpass API (radius 50m) with caching (24h TTL)
│  ├─ Parse maxspeed tag, handle mph/km/h
│  ├─ Select best road (highest priority to avoid on-ramps)
│  ├─ Check for school zones (200m radius, Mon-Fri 8am-5pm)
│  └─ Return BC defaults if no OSM data
│
│  2. LIVE EVALUATION (Every 2 seconds from mobile)
│  ├─ Call evaluator._evaluate_braking(accel_window, speed_window)
│  │  ├─ Harsh braking: > 0.6g
│  │  ├─ Sudden stops: > 10 km/h drop
│  │  └─ Smooth braking: 0.1-0.4g
│  │
│  ├─ Call evaluator._evaluate_speed(speed_window, duration_min)
│  │  ├─ Get speed limit via _get_limit_for_point()
│  │  ├─ Require 3 consecutive points over limit (except school zones)
│  │  └─ Flag events if excess > 5 km/h
│  │
│  ├─ Call evaluator._evaluate_cornering(accel_window, rotation_window)
│  │  ├─ Sharp turns: > speed-dependent threshold (0.45g baseline @ 0km/h, -0.002g per km/h)
│  │  └─ Jerky steering: > 1.0 rad/s rotation rate
│  │
│  └─ Return events + stream via WebSocket
│
│  3. FULL EVALUATION (On ride completion)
│  ├─ Fetch all telemetry from NoSQL
│  ├─ Calibrate orientation (first 3000 samples)
│  │  ├─ Gravity vector (Z-up)
│  │  ├─ Forward vector (Y, from acceleration phase)
│  │  └─ Right vector (X, cross product)
│  │
│  ├─ Run all sub-evaluators with full data:
│  │  ├─ _evaluate_braking() → score
│  │  ├─ _evaluate_speed() → score
│  │  ├─ _evaluate_cornering() → score
│  │  ├─ _calculate_jerk_score() → score
│  │  ├─ _evaluate_combined_dynamics() (friction circle)
│  │  └─ _evaluate_vertical_impacts() (z-axis bumps)
│  │
│  ├─ Calculate weighted overall score:
│  │  overall = braking×0.3 + speed×0.3 + cornering×0.2 + smoothness×0.2
│  │
│  ├─ Determine pass/fail:
│  │  ├─ overall_score >= 75
│  │  ├─ braking_score >= 70
│  │  ├─ speed_score >= 70
│  │  ├─ cornering_score >= 70
│  │  ├─ smoothness_score >= 70
│  │  ├─ duration >= 20 minutes
│  │  └─ distance >= 5 km
│  │
│  ├─ Generate event timeline + route segments
│  └─ Save all results to database
│
│  4. MODULE UNLOCKING
│  ├─ If passed:
│  │  ├─ Mark diagnostic_completed = True
│  │  ├─ Skip Basics (lock)
│  │  └─ Unlock Advanced module
│  │
│  └─ If failed:
│     └─ Unlock Basics module for further learning
│
└─────────────────────────────────────────────────────────────────┘
```

---

## 9. KEY DETECTION LOGIC SUMMARY

| Fault | Threshold | Logic | Penalty | Notes |
|-------|-----------|-------|---------|-------|
| **Harsh Braking** | > 0.6g deceleration | Measures forward-axis acceleration after gravity compensation and orientation calibration. Requires 3s cooldown. | -10 points per event | Sudden stops (>10 km/h drop) also trigger, -15 each. Smooth braking (0.1-0.4g) gives +2 bonus per event. |
| **Sharp Turn** | > 0.45g - (speed × 0.002) g lateral acceleration | Speed-scaled threshold (lower at high speeds). Extracted from calibrated X-axis. | -8 × (1 + speed/50) | Scaled by speed. 3s cooldown. Smooth turns (0.02-0.2g) give +2 bonus. |
| **Speeding** | excess > 0 km/h | Requires 3 consecutive points over limit (except school zones). Matched via timestamp to speed_limit_data. | -2 × % time speeding | School zones: 2x multiplier. Single-point glitches filtered. |
| **Friction Circle** | > 0.6g combined (√(x² + y²)) | Vector magnitude of lateral + forward acceleration (combined maneuver risk). | -12 points | Detects trail-braking into turns. |
| **Vertical Impact** | > 0.4g z-axis variance | |z_accel - 9.81| / 9.81 relative to gravity. | -5 points | 1s cooldown (separate). Detects potholes/bumps. |
| **Jerky Steering** | > 1.0 rad/s yaw rate change | Frame-to-frame gyroscope Z-axis delta. | -5 points | Detects abrupt steering corrections. |

---

## 10. CRITICAL IMPLEMENTATION NOTES

1. **Gravity Compensation**: The system uses a calibration phase to align smartphone to vehicle frame, then removes gravity from acceleration readings. This is essential to avoid false positives on inclines.

2. **Orientation Calibration**: If calibration fails (insufficient acceleration window), falls back to heuristic (phone upright/forward). This is why initial drive-off matters.

3. **Cooldown Throttling**: 3-second cooldown between events of same type prevents one 2-second braking action from generating 20 events. Critical for accurate event counts.

4. **Speed Limit Glitch Filtering**:
   - Backend: Requires 3 consecutive points over limit (filters OSM glitches like on-ramp tags)
   - Mobile: Smoothing with validation (rejects 20+ km/h changes unless 3+ recent readings agree)

5. **School Zone Detection**: Automatic via OSM proximity + time-of-day. Handled separately from general speeding.

6. **Pass Criteria**: ALL conditions must be met:
   - Overall >= 75
   - All categories >= 70 (not average—each must pass individually)
   - Duration >= 20 min
   - Distance >= 5 km

7. **Data Persistence**: 
   - Live data: WebSocket + HTTP fallback to NoSQL (MongoDB)
   - Evaluation result: Full telemetry fetched from NoSQL on evaluation
   - Speed limits: Cached for 24 hours (rounded to ~100m precision)

---

