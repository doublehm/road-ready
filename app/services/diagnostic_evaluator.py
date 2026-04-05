import json
from typing import Dict, List, Optional, Tuple
import math
import numpy as np
from app.services.nosql_repo import NoSQLRepository
from app.services.ml_evaluator import MLEvaluator

class DiagnosticEvaluator:
    """
    Evaluates diagnostic ride data to generate scores and pass/fail determination.

    Scoring breakdown:
    - Braking Score (40% weight): Based on harsh braking events and smoothness
    - Speed Score (35% weight): Based on speed compliance and consistency
    - Cornering Score (25% weight): Based on turning smoothness and stability

    Pass criteria:
    - Overall score >= 75
    - Each individual score >= 70
    - Duration >= 20 minutes
    - Distance >= 5 km
    """

    # Scoring weights
    BRAKING_WEIGHT = 0.3
    SPEED_WEIGHT = 0.3
    CORNERING_WEIGHT = 0.2
    SMOOTHNESS_WEIGHT = 0.2 # Jerk analysis

    # Pass thresholds
    OVERALL_PASS_THRESHOLD = 75
    CATEGORY_PASS_THRESHOLD = 70
    MIN_DURATION_MINUTES = 20
    MIN_DISTANCE_KM = 5

    # Physical constants
    GRAVITY = 9.81  # m/s²

    # ── Braking thresholds ──
    # Comfortable braking ~0.15-0.25g, moderate ~0.25-0.35g.
    # Passenger discomfort starts at ~0.35g; 0.40g is clearly firm braking.
    HARSH_BRAKING_THRESHOLD = 0.40  # g
    # Speed drop in one sampling window that indicates surprise braking (~0.17g decel)
    SUDDEN_STOP_SPEED_DROP = 6  # km/h
    # Smooth braking range: engine-braking level up to comfortable deceleration
    SMOOTH_BRAKING_MIN = 0.08  # g
    SMOOTH_BRAKING_MAX = 0.30  # g

    # ── Jerk thresholds (m/s³) — per ISO 2631 human vibration comfort ──
    # Below 1.5 m/s³ is imperceptible; above 5.0 m/s³ is a noticeable jolt.
    JERK_SMOOTH_THRESHOLD = 1.5
    JERK_HARSH_THRESHOLD = 5.0

    # ── Cornering thresholds ──
    # City turn at 30 km/h ≈ 0.18g; at 0.35g coffee spills and passengers lean.
    SHARP_TURN_LATERAL_THRESHOLD = 0.35  # g lateral acceleration
    SMOOTH_TURN_THRESHOLD = 0.15  # g — boundary between straight-line and gentle curve

    # Speed-dependent cornering scaling
    # At 100 km/h: 0.35 − (100 × 0.002) = 0.15g — catches highway weaving
    CORNERING_SPEED_SENSITIVITY = 0.002  # g reduction per km/h

    # Minimum floor for dynamic cornering threshold regardless of speed
    CORNERING_THRESHOLD_FLOOR = 0.15  # g

    # ── Friction Circle (combined lateral + longitudinal grip) ──
    # Dry tire grip ≈ 0.80-1.00g total. 0.50g = 50% grip budget — safe margin for students.
    FRICTION_CIRCLE_THRESHOLD = 0.50  # g total vector magnitude

    # ── Vertical Impact (Z-axis variance from gravity) ──
    # Normal road ~0.05-0.15g, speed bump ~0.20-0.35g, pothole ~0.30-0.60g.
    VERTICAL_IMPACT_THRESHOLD = 0.30  # g

    # Minimum milliseconds between two events of the same type.
    # Prevents consecutive sensor samples from one braking/cornering action
    # being counted as dozens of separate events.
    EVENT_COOLDOWN_MS = 3000  # 3 seconds

    # ── Gyroscope ──
    # Normal correction ~0.1-0.3 rad/s, quick lane change ~0.3-0.7, panic swerve > 1.0
    JERKY_STEERING_RATE_THRESHOLD = 0.8  # rad/s

    # Speed limits (fallback when no actual data available)
    RESIDENTIAL_LIMIT = 50  # km/h
    ARTERIAL_LIMIT = 60  # km/h
    HIGHWAY_LIMIT = 80  # km/h

    # School zone penalty multiplier
    SCHOOL_ZONE_PENALTY_MULTIPLIER = 2.0

    # Lane discipline thresholds
    HEADING_VARIANCE_THRESHOLD = 3.0  # degrees - high variance when driving straight
    WEAVING_THRESHOLD = 5.0  # degrees - sudden heading change at speed

    # Speed tolerance — accounts for GPS jitter and speedometer variance
    SPEED_TOLERANCE_KMH = 5  # km/h buffer before penalizing
    SCHOOL_ZONE_SPEED_TOLERANCE_KMH = 2  # stricter in school zones

    # Heading change required to confirm a cornering event (degrees)
    CORNERING_HEADING_CHANGE_MIN = 3.0
    # Time window (ms) around an acceleration event to check for heading change
    CORNERING_HEADING_WINDOW_MS = 3000

    # Backend speed limit smoothing — consecutive readings needed to accept a dramatic change
    SPEED_LIMIT_SMOOTH_CONSECUTIVE = 3
    SPEED_LIMIT_SMOOTH_CHANGE_THRESHOLD = 20  # km/h

    # ── Erratic driving thresholds ──
    # At 0.30g (2.94 m/s²) passengers are noticeably pushed into seats.
    HARSH_ACCELERATION_THRESHOLD = 0.30  # g
    SPEED_OSCILLATION_WINDOW = 15  # data points (15s at 1Hz) to analyze for patterns
    SPEED_OSCILLATION_MIN_CHANGES = 3  # significant direction changes for erratic flag
    SPEED_OSCILLATION_AMPLITUDE = 8  # km/h minimum speed range in window
    SPEED_OSCILLATION_MIN_DELTA = 2.0  # km/h min change to count as a direction shift
    SPEED_OSCILLATION_MIN_SPEED = 20.0 # km/h floor; ignore oscillations in stop-and-go traffic

    def __init__(self):
        self.nosql_repo = NoSQLRepository()
        self.ml_evaluator = MLEvaluator()

    @staticmethod
    def _median_filter(data: List[Dict], window: int = 5) -> List[Dict]:
        """
        Apply a sliding-window median filter to acceleration data.
        Filters X, Y, Z independently to remove single-sample spikes
        (potholes, vibration) while preserving sustained forces.
        """
        if len(data) <= window:
            return data

        half = window // 2
        filtered = []
        for i in range(len(data)):
            lo = max(0, i - half)
            hi = min(len(data), i + half + 1)
            window_slice = data[lo:hi]

            filtered_point = dict(data[i])
            for axis in ('x', 'y', 'z'):
                vals = [p.get(axis) or 0 for p in window_slice]
                vals.sort()
                filtered_point[axis] = vals[len(vals) // 2]
            filtered.append(filtered_point)

        return filtered

    def _heading_changed(self, timestamp: float, heading_data: List[Dict],
                         speed_data: Optional[List[Dict]] = None) -> bool:
        """
        Check whether GPS heading changed significantly around the given timestamp,
        confirming an actual turn is in progress (not just vibration).
        """
        if not heading_data or len(heading_data) < 2:
            return True  # no heading data — can't disprove, assume real

        window_ms = self.CORNERING_HEADING_WINDOW_MS
        nearby = [h for h in heading_data
                  if abs((h.get('timestamp') or 0) - timestamp) <= window_ms]

        if len(nearby) < 2:
            # If no heading data near this timestamp, fall back to checking
            # speed — at very low speeds even small steering produces lateral g.
            if speed_data:
                closest = min(speed_data, key=lambda p: abs((p.get('timestamp') or 0) - timestamp))
                if (closest.get('speed') or 0) < 15:
                    return True  # low-speed manoeuvres are always plausible
            return True  # can't disprove

        headings = [h.get('heading') or 0 for h in nearby]
        # Handle wrap-around at 360°
        max_change = 0
        for i in range(1, len(headings)):
            diff = abs(headings[i] - headings[i - 1])
            if diff > 180:
                diff = 360 - diff
            max_change = max(max_change, diff)

        return max_change >= self.CORNERING_HEADING_CHANGE_MIN

    def _calibrate_orientation(self, acceleration_data: List[Dict], speed_data: List[Dict]) -> Optional[np.ndarray]:
        """
        Calculates a 3x3 rotation matrix to align sensor data with the vehicle frame.
        Vehicle Frame: Y = Forward, Z = Up (Gravity), X = Right.
        """
        if not acceleration_data or len(acceleration_data) < 20:
            return None

        # 1. Find Gravity Vector (average acceleration over calibration window)
        accels = np.array([[p.get('x') or 0, p.get('y') or 0, p.get('z') or 0] for p in acceleration_data])
        avg_accel = np.mean(accels, axis=0)
        
        # Unit vector for UP (against gravity)
        # Note: Sensor sees +9.81 m/s^2 on the axis pointing UP when at rest.
        z_basis = avg_accel / np.linalg.norm(avg_accel)

        # 2. Find Forward Vector
        # Look for a window where GPS speed is increasing (acceleration > 0.5 m/s^2)
        forward_samples = []
        for i in range(1, len(speed_data)):
            dv = speed_data[i].get('speed', 0) - speed_data[i-1].get('speed', 0)
            dt = (speed_data[i].get('timestamp', 0) - speed_data[i-1].get('timestamp', 0)) / 1000.0
            if dt > 0 and (dv / dt) / 3.6 > 0.5: # > 0.5 m/s^2
                # Find corresponding accel samples
                ts = speed_data[i].get('timestamp', 0)
                matching = [p for p in acceleration_data if abs(p.get('timestamp', 0) - ts) < 200]
                for m in matching:
                    forward_samples.append([m.get('x') or 0, m.get('y') or 0, m.get('z') or 0])

        if len(forward_samples) > 5:
            f_raw = np.mean(forward_samples, axis=0)
            # Remove gravity component from forward vector
            f_raw = f_raw - np.dot(f_raw, z_basis) * z_basis
            y_basis = f_raw / np.linalg.norm(f_raw)
        else:
            # Fallback: assume phone is mounted roughly upright/facing forward
            # if no acceleration window found.
            temp_x = np.array([1, 0, 0])
            y_basis = np.cross(z_basis, temp_x)
            y_basis /= np.linalg.norm(y_basis)

        # 3. Calculate Right Vector
        x_basis = np.cross(y_basis, z_basis)
        x_basis /= np.linalg.norm(x_basis)

        # 4. Refine Forward to ensure perfect orthogonality
        y_basis = np.cross(z_basis, x_basis)

        # Rotation Matrix (columns are the sensor axes in vehicle frame)
        # We want Sensor -> Vehicle.
        # R * v_sensor = v_vehicle
        # The rows of R should be the basis vectors of the vehicle frame in sensor coords.
        return np.array([x_basis, y_basis, z_basis])

    def _calculate_jerk_score(self, acceleration_data: List[Dict]) -> float:
        """
        Calculates a smoothness score based on Jerk (da/dt).
        """
        if len(acceleration_data) < 2:
            return 100.0

        jerk_values = []
        for i in range(1, len(acceleration_data)):
            p1 = acceleration_data[i-1]
            p2 = acceleration_data[i]
            
            dt = (p2.get('timestamp', 0) - p1.get('timestamp', 0)) / 1000.0
            if dt <= 0: continue
            
            a1 = np.array([p1.get('x') or 0, p1.get('y') or 0, p1.get('z') or 0])
            a2 = np.array([p2.get('x') or 0, p2.get('y') or 0, p2.get('z') or 0])
            
            jerk = np.linalg.norm(a2 - a1) / dt
            jerk_values.append(jerk)

        if not jerk_values:
            return 100.0

        # Penalize values above smooth threshold
        avg_jerk = np.mean(jerk_values)
        high_jerk_ratio = len([j for j in jerk_values if j > self.JERK_HARSH_THRESHOLD]) / len(jerk_values)
        
        # Scoring: 100 - (avg_jerk * 5) - (high_jerk_ratio * 100)
        score = 100.0 - (avg_jerk * 2) - (high_jerk_ratio * 50)
        return max(0, min(100, score))

    async def evaluate(self, ride_id: str, ride_data: Dict) -> Dict:
        """
        Main evaluation method that processes all sensor data and generates scores.
        Fetches telemetry from NoSQL and aggregates with metadata.
        """
        # Fetch telemetry from NoSQL (falls back to SQL blobs if MongoDB is unavailable)
        try:
            nosql_telemetry = await self.nosql_repo.get_ride_telemetry(ride_id)
            nosql_events = await self.nosql_repo.get_ride_events(ride_id)
        except Exception:
            nosql_telemetry = []
            nosql_events = []

        # Mapping NoSQL points to legacy evaluator format
        acceleration_data = []
        speed_data = []
        rotation_data = []
        heading_data = []

        for p in nosql_telemetry:
            ts = p.get('timestamp')
            lat = p.get('location', {}).get('latitude') or p.get('latitude')
            lon = p.get('location', {}).get('longitude') or p.get('longitude')
            
            if 'acceleration' in p:
                acc = p['acceleration']
                acceleration_data.append({
                    'timestamp': ts, 'x': acc.get('x'), 'y': acc.get('y'), 'z': acc.get('z'),
                    'latitude': lat, 'longitude': lon
                })
            elif 'x' in p and 'y' in p and 'z' in p:
                # Flat format support
                acceleration_data.append({
                    'timestamp': ts, 'x': p.get('x'), 'y': p.get('y'), 'z': p.get('z'),
                    'latitude': lat, 'longitude': lon
                })
            
            if 'rotation' in p:
                rot = p['rotation']
                rotation_data.append({
                    'timestamp': ts, 'x': rot.get('x'), 'y': rot.get('y'), 'z': rot.get('z')
                })
            
            if 'speed' in p:
                speed_data.append({
                    'timestamp': ts, 'speed': p['speed'], 'latitude': lat, 'longitude': lon
                })
            
            if 'heading' in p:
                heading_data.append({'timestamp': ts, 'heading': p['heading'],
                                     'latitude': lat, 'longitude': lon})

        # Fallback to ride_data JSON blobs if NoSQL is empty (transition/hybrid support)
        if not acceleration_data:
            acceleration_data = json.loads(ride_data.get('acceleration_data', '[]'))
        if not rotation_data:
            rotation_data = json.loads(ride_data.get('rotation_data', '[]'))
        if not speed_data:
            speed_data = json.loads(ride_data.get('speed_data', '[]'))
        if not heading_data:
            heading_data = json.loads(ride_data.get('heading_data', '[]'))

        # Extract heading from speed_data when heading_data is empty
        # (mobile sends heading inside each speed data point)
        if not heading_data and speed_data:
            heading_data = [
                {'timestamp': p.get('timestamp'), 'heading': p['heading'],
                 'latitude': p.get('latitude'), 'longitude': p.get('longitude')}
                for p in speed_data
                if p.get('heading') is not None
            ]

        speed_limit_data = json.loads(ride_data.get('speed_limit_data', '[]'))
        human_feedback = json.loads(ride_data.get('human_feedback', '[]'))

        duration_minutes = ride_data.get('duration_minutes') or 0
        distance_km = ride_data.get('distance_km') or 0

        # Enrich acceleration data with GPS coordinates from speed_data.
        # The mobile app sends acceleration without lat/lng, so we interpolate
        # from the nearest speed point (which always has GPS coordinates).
        if speed_data and acceleration_data:
            gps_points = [p for p in speed_data if p.get('latitude') and p.get('longitude')]
            if gps_points:
                needs_coords = any(not p.get('latitude') for p in acceleration_data[:10])
                if needs_coords:
                    sorted_gps = sorted(gps_points, key=lambda p: float(p.get('timestamp') or 0))
                    for point in acceleration_data:
                        if point.get('latitude') and point.get('longitude'):
                            continue
                        ts = float(point.get('timestamp') or 0)
                        closest = min(sorted_gps, key=lambda p: abs(float(p.get('timestamp') or 0) - ts))
                        point['latitude'] = closest.get('latitude')
                        point['longitude'] = closest.get('longitude')

        # 0. Pre-filter: median filter removes single-sample spikes (potholes, vibration)
        unfiltered_accel = [dict(p) for p in acceleration_data] # Deep copy
        acceleration_data = self._median_filter(acceleration_data)

        # 1. Calibrate Orientation (first 5 minutes / 3000 samples approx)
        calibration_samples = acceleration_data[:3000]
        orientation_matrix = self._calibrate_orientation(calibration_samples, speed_data)

        # 2. Calculate individual scores with high-fidelity physics
        braking_score, braking_feedback = self._evaluate_braking(
            acceleration_data, speed_data, gps_data=speed_data, orientation_matrix=orientation_matrix
        )
        speed_score, speed_feedback = self._evaluate_speed(
            speed_data, duration_minutes, speed_limit_data=speed_limit_data
        )
        cornering_score, cornering_feedback = self._evaluate_cornering(
            acceleration_data, rotation_data, speed_data=speed_data, heading_data=heading_data
        )
        
        # 3. New Advanced Metrics
        smoothness_score = self._calculate_jerk_score(acceleration_data)
        combined_score, combined_feedback = self._evaluate_combined_dynamics(acceleration_data)
        impact_score, impact_feedback = self._evaluate_vertical_impacts(
            acceleration_data, orientation_matrix=orientation_matrix
        )

        # 4. New: Erratic driving & lane discipline
        erratic_penalty, erratic_feedback = self._evaluate_erratic_driving(
            acceleration_data, speed_data, gps_data=speed_data,
            orientation_matrix=orientation_matrix
        )
        lane_penalty, lane_feedback = self._evaluate_lane_discipline(
            heading_data, speed_data
        )

        # 5. ML-based Evaluation
        ml_events = self.ml_evaluator.evaluate_ride(unfiltered_accel)

        # Incorporate Friction Circle and Vertical Impacts as penalties into scores
        braking_score = max(0, braking_score - combined_feedback['violations'] * 5)
        cornering_score = max(0, cornering_score - combined_feedback['violations'] * 5)
        smoothness_score = max(0, smoothness_score - impact_feedback['impact_count'] * 10)
        # Erratic driving penalties reduce smoothness score
        smoothness_score = max(0, smoothness_score - erratic_penalty)
        # Lane discipline penalties reduce cornering score
        cornering_score = max(0, cornering_score - lane_penalty)

        # Calculate overall score with new weights
        overall_score = (
            braking_score * self.BRAKING_WEIGHT +
            speed_score * self.SPEED_WEIGHT +
            cornering_score * self.CORNERING_WEIGHT +
            smoothness_score * self.SMOOTHNESS_WEIGHT
        )

        # Determine pass/fail
        passed, pass_feedback = self._determine_pass(
            overall_score, braking_score, speed_score, cornering_score,
            duration_minutes, distance_km, smoothness_score=smoothness_score
        )

        # Aggregate all events
        all_events = []
        all_events.extend(braking_feedback.get('events', []))
        all_events.extend(speed_feedback.get('events', []))
        all_events.extend(cornering_feedback.get('events', []))
        all_events.extend(combined_feedback.get('events', []))
        all_events.extend(impact_feedback.get('events', []))
        all_events.extend(erratic_feedback.get('events', []))
        all_events.extend(lane_feedback.get('events', []))
        all_events.extend(ml_events)
        
        # Add NoSQL system events
        for e in nosql_events:
            all_events.append({
                'type': e.get('type'),
                'timestamp': e.get('timestamp'),
                'severity': e.get('severity'),
                'description': e.get('description', f"System event: {e.get('type')}")
            })

        # Integrate human feedback into events list
        for flag in human_feedback:
            if 'timestamps' in flag:
                for t in flag['timestamps']:
                    all_events.append({
                        'type': 'human_flag',
                        'timestamp': t.get('ts'),
                        'label': flag.get('label'),
                        'code': flag.get('code'),
                        'severity': 'human',
                        'description': f"Supervisor flagged: {flag.get('label')} ({flag.get('code')})"
                    })

        all_events.sort(key=lambda x: x.get('timestamp') or 0)

        # Build route segments for map replay
        route_segments = self._build_route_segments(speed_data, speed_limit_data, all_events)

        # Generate comprehensive feedback
        evaluation_result = {
            'braking': braking_feedback,
            'speed': speed_feedback,
            'cornering': cornering_feedback,
            'smoothness': {'score': smoothness_score},
            'erratic_driving': erratic_feedback,
            'lane_discipline': lane_feedback,
            'overall': pass_feedback,
            'events': all_events,
            'route_segments': route_segments,
            'human_feedback': human_feedback,
            'summary': self._generate_summary(
                passed, overall_score, braking_score, speed_score, cornering_score,
                speed_feedback, smoothness_score=smoothness_score
            )
        }

        return {
            'braking_score': round(braking_score, 2),
            'speed_score': round(speed_score, 2),
            'cornering_score': round(cornering_score, 2),
            'smoothness_score': round(smoothness_score, 2),
            'overall_score': round(overall_score, 2),
            'passed': passed,
            'evaluation_result': json.dumps(evaluation_result),
            # Expose processed sensor data so the evaluate endpoint can persist it
            # back to the SQL record for the results screen.
            'speed_data': speed_data,
            'route_coords': [
                {'latitude': p['latitude'], 'longitude': p['longitude']}
                for p in speed_data
                if p.get('latitude') and p.get('longitude')
            ],
        }

    def _get_incline_at(self, timestamp: float, gps_data: List[Dict]) -> float:
        """
        Estimates the incline (sin of the angle) at a given timestamp using GPS altitude.
        Returns sin(theta) = rise / run.
        """
        if not gps_data or len(gps_data) < 2:
            return 0.0
            
        # Only use points with altitude
        valid_gps = [p for p in gps_data if p.get('altitude') is not None]
        if len(valid_gps) < 2:
            return 0.0

        # Find the two GPS points that bracket this timestamp
        sorted_gps = sorted(valid_gps, key=lambda x: float(x.get('timestamp', 0)))
        
        idx = 0
        for i in range(len(sorted_gps)):
            if float(sorted_gps[i].get('timestamp', 0)) > timestamp:
                idx = i
                break
        else:
            idx = len(sorted_gps) - 1
            
        p1 = sorted_gps[max(0, idx - 1)]
        p2 = sorted_gps[idx]
        
        if p1 == p2:
            return 0.0
            
        alt1 = p1.get('altitude')
        alt2 = p2.get('altitude')
        
        if alt1 is None or alt2 is None:
            return 0.0
            
        # Calculate horizontal distance (run)
        lat1, lon1 = p1.get('latitude', 0), p1.get('longitude', 0)
        lat2, lon2 = p2.get('latitude', 0), p2.get('longitude', 0)
        
        # Approx distance in meters (using 111.1km per degree)
        d_lat = (lat2 - lat1) * 111139
        d_lon = (lon2 - lon1) * 111139 * math.cos(math.radians(lat1))
        run = math.sqrt(d_lat**2 + d_lon**2)
        
        if run < 2.0: # Avoid noise/division by zero
            return 0.0
            
        rise = alt2 - alt1
        # sin(theta) = rise / slope_distance, but for small angles run ≈ slope_distance
        return rise / run

    def _evaluate_braking(self, acceleration_data: List[Dict], speed_data: List[Dict],
                          gps_data: Optional[List[Dict]] = None,
                          orientation_matrix: Optional[np.ndarray] = None) -> Tuple[float, Dict]:
        """Evaluate braking performance based on acceleration and speed data."""
        score = 100.0
        harsh_braking_count = 0
        sudden_stop_count = 0
        smooth_braking_count = 0
        events = []

        feedback = {
            'harsh_braking_events': 0,
            'sudden_stops': 0,
            'smooth_braking_events': 0,
            'notes': [],
            'events': [],
            'tips': []
        }

        if not acceleration_data:
            return 50.0, {'notes': ['Insufficient acceleration data for evaluation'], 'events': [], 'tips': []}

        # Analyze acceleration data for harsh braking.
        last_harsh_braking_ts = None
        for i in range(len(acceleration_data)):
            point = acceleration_data[i]
            accel_vector = np.array([point.get('x') or 0, point.get('y') or 0, point.get('z') or 0])

            # Skip samples that look like vertical impacts (potholes, speed bumps).
            # High Z-axis deviation from gravity means the phone is bouncing, not braking.
            raw_z = point.get('z') or 0
            z_variance_from_gravity = abs(abs(raw_z) - self.GRAVITY) / self.GRAVITY
            if z_variance_from_gravity > self.VERTICAL_IMPACT_THRESHOLD:
                continue

            if orientation_matrix is not None:
                # Transform to Vehicle Frame: Y=Forward, Z=Up
                vehicle_accel = np.dot(orientation_matrix, accel_vector)
                y_accel = vehicle_accel[1]
            else:
                # Uncalibrated fallback: use only Y-axis for braking.
                # Z-axis is gravity-aligned and catches potholes — unreliable for braking.
                y_accel = point.get('y') or 0

            try:
                ts = float(point.get('timestamp') or 0)
            except (ValueError, TypeError):
                ts = 0

            # Gravity Compensation
            incline_sin = self._get_incline_at(ts, gps_data) if gps_data else 0.0
            # True Forward Accel = Measured Y + g*sin(theta)
            # Uphill (theta > 0): Gravity pulls backward (-g*sin), so Measured Y = True Y - g*sin
            # -> True Y = Measured Y + g*sin
            true_y_accel = y_accel + (self.GRAVITY * incline_sin)

            y_decel_g = abs(true_y_accel) / self.GRAVITY if true_y_accel < 0 else 0
            deceleration_g = y_decel_g

            if deceleration_g > self.HARSH_BRAKING_THRESHOLD:
                # Skip if within cooldown window of the last event (same braking action)
                if last_harsh_braking_ts is None or (ts - last_harsh_braking_ts) >= self.EVENT_COOLDOWN_MS:
                    harsh_braking_count += 1
                    score -= 10
                    last_harsh_braking_ts = ts
                    events.append({
                        'type': 'harsh_braking',
                        'timestamp': point.get('timestamp'),
                        'lat': point.get('latitude'),
                        'lng': point.get('longitude'),
                        'value': round(deceleration_g, 2),
                        'incline': round(incline_sin * 100, 1), # as percentage
                        'severity': 'high' if deceleration_g > 0.8 else 'medium',
                        'description': f'Harsh braking at {round(deceleration_g, 2)}g force' + (f' ({round(incline_sin*100,0)}% grade)' if abs(incline_sin) > 0.02 else '')
                    })
            elif deceleration_g >= self.SMOOTH_BRAKING_MIN and deceleration_g <= self.SMOOTH_BRAKING_MAX:
                smooth_braking_count += 1

        # Analyze speed data for sudden stops (same cooldown applied).
        last_sudden_stop_ts = None
        for i in range(1, len(speed_data)):
            point = speed_data[i]
            prev_point = speed_data[i-1]
            prev_speed = prev_point.get('speed', 0)
            curr_speed = point.get('speed', 0)
            
            try:
                prev_ts = float(prev_point.get('timestamp', 0))
                curr_ts = float(point.get('timestamp', 0))
                time_diff = curr_ts - prev_ts
            except (ValueError, TypeError):
                time_diff = 0

            if time_diff > 0 and (time_diff <= 1.5 or (time_diff <= 1500 and time_diff > 1.5)):
                speed_drop = prev_speed - curr_speed
                if speed_drop > self.SUDDEN_STOP_SPEED_DROP:
                    try:
                        ts = float(point.get('timestamp') or 0)
                    except (ValueError, TypeError):
                        ts = 0
                    if last_sudden_stop_ts is None or (ts - last_sudden_stop_ts) >= self.EVENT_COOLDOWN_MS:
                        sudden_stop_count += 1
                        score -= 15
                        last_sudden_stop_ts = ts
                        events.append({
                            'type': 'sudden_stop',
                            'timestamp': point.get('timestamp'),
                            'lat': point.get('latitude'),
                            'lng': point.get('longitude'),
                            'value': round(speed_drop, 2),
                            'severity': 'high',
                            'description': f'Sudden stop: {round(speed_drop, 1)} km/h drop in {round(time_diff, 1)}s'
                        })

        # Bonus for smooth braking
        smooth_bonus = min(20, smooth_braking_count * 2)
        score += smooth_bonus

        score = max(0, min(100, score))

        feedback['harsh_braking_events'] = harsh_braking_count
        feedback['sudden_stops'] = sudden_stop_count
        feedback['smooth_braking_events'] = smooth_braking_count
        feedback['events'] = events

        if harsh_braking_count > 5:
            feedback['notes'].append(f'Too many harsh braking events ({harsh_braking_count}). Practice gradual deceleration.')
            feedback['tips'].append('Start braking earlier and apply pressure gradually. Look further ahead to anticipate stops.')
        elif harsh_braking_count > 0:
            feedback['notes'].append(f'Some harsh braking detected ({harsh_braking_count} events). Work on smoother stops.')
            feedback['tips'].append('Try the "progressive braking" technique: light pressure first, then gradually increase.')
        else:
            feedback['notes'].append('Excellent braking control!')

        if sudden_stop_count > 0:
            feedback['tips'].append('Maintain a safe following distance to avoid emergency stops.')

        if smooth_braking_count > 10:
            feedback['notes'].append('Great smooth braking technique.')

        return score, feedback

    def _get_limit_for_point(self, speed_point: Dict, speed_limit_data: List[Dict]) -> Optional[Dict]:
        """Find the nearest speed limit entry for a given speed data point."""
        if not speed_limit_data:
            return None

        try:
            point_ts = float(speed_point.get('timestamp', 0))
        except (ValueError, TypeError):
            return None
            
        best = None
        best_diff = float('inf')

        for sl in speed_limit_data:
            try:
                sl_ts = float(sl.get('timestamp', 0))
                diff = abs(sl_ts - point_ts)
                if diff < best_diff:
                    best_diff = diff
                    best = sl
            except (ValueError, TypeError):
                continue

        if best and (best_diff < 60 or (best_diff < 60000 and best_diff > 1000)):
            return best
        return None

    def _infer_speed_limit(self, avg_speed: float) -> Tuple[int, str]:
        """Fallback: infer speed limit from average speed."""
        if avg_speed < 40:
            return self.RESIDENTIAL_LIMIT, "residential"
        elif avg_speed < 65:
            return self.ARTERIAL_LIMIT, "arterial"
        else:
            return self.HIGHWAY_LIMIT, "highway"

    def _evaluate_speed(self, speed_data: List[Dict], duration_minutes: float,
                        speed_limit_data: Optional[List[Dict]] = None) -> Tuple[float, Dict]:
        """
        Evaluate speed compliance and consistency using actual speed limits when available.
        """
        score = 100.0
        speeding_time = 0
        under_speed_time = 0
        total_time = 0
        speed_changes = []
        school_zone_violations = 0
        max_excess_kmh = 0
        speed_violations = []
        current_violation = None

        feedback = {
            'speeding_percentage': 0,
            'under_speed_percentage': 0,
            'speed_variance': 0,
            'school_zone_violations': 0,
            'max_excess_kmh': 0,
            'speed_violations': [],
            'notes': [],
            'events': [],
            'tips': [],
            'used_actual_limits': bool(speed_limit_data),
        }

        if not speed_data or len(speed_data) < 2:
            return 50.0, {'notes': ['Insufficient speed data for evaluation'], 'events': [], 'tips': []}
    
        events = []
        speeds = [point.get('speed', 0) for point in speed_data]
        
        avg_speed = sum(speeds) / len(speeds) if speeds else 0
        fallback_limit, fallback_road_type = self._infer_speed_limit(avg_speed)

        # Require this many consecutive data points over the limit before
        # flagging a speeding event. One or two points at a glitched lower
        # limit (e.g. an on-ramp tag briefly appearing near a 100 km/h highway)
        # will be ignored. School zones are exempt — single-point violations
        # still count there.
        CONSECUTIVE_SPEEDING_REQUIRED = 3
        consecutive_over = 0  # how many consecutive points have been over the limit

        # Backend speed limit smoothing: carry-forward until confirmed
        smoothed_limit = None
        pending_new_limit = None
        pending_count = 0

        for i in range(len(speed_data)):
            point = speed_data[i]
            speed = point.get('speed', 0)
            time_duration = 1
            total_time += time_duration

            speed_limit = fallback_limit
            zone_type = "regular"
            road_type = fallback_road_type

            if speed_limit_data:
                sl_entry = self._get_limit_for_point(point, speed_limit_data)
                if sl_entry:
                    speed_limit = sl_entry.get('speed_limit', fallback_limit) or fallback_limit
                    zone_type = sl_entry.get('zone_type', 'regular')
                    road_type = sl_entry.get('road_type', fallback_road_type)

            inline_limit = point.get('speed_limit')
            if inline_limit and inline_limit > 0:
                speed_limit = inline_limit

            # Apply backend speed limit smoothing: require consecutive readings
            # to confirm a dramatic limit change (filters OSM glitches).
            if smoothed_limit is None:
                smoothed_limit = speed_limit
            elif abs(speed_limit - smoothed_limit) > self.SPEED_LIMIT_SMOOTH_CHANGE_THRESHOLD:
                if pending_new_limit is not None and abs(speed_limit - pending_new_limit) <= 10:
                    pending_count += 1
                    if pending_count >= self.SPEED_LIMIT_SMOOTH_CONSECUTIVE:
                        smoothed_limit = speed_limit
                        pending_new_limit = None
                        pending_count = 0
                else:
                    pending_new_limit = speed_limit
                    pending_count = 1
                speed_limit = smoothed_limit
            else:
                smoothed_limit = speed_limit
                pending_new_limit = None
                pending_count = 0

            # Apply speed tolerance — accounts for GPS jitter and speedometer variance
            is_school = zone_type == "school"
            tolerance = self.SCHOOL_ZONE_SPEED_TOLERANCE_KMH if is_school else self.SPEED_TOLERANCE_KMH
            excess = speed - (speed_limit + tolerance)

            try:
                ts = float(point.get('timestamp', 0))
            except (ValueError, TypeError):
                ts = 0

            if excess > 0:
                consecutive_over += 1
                speeding_time += time_duration
                if excess > max_excess_kmh:
                    max_excess_kmh = excess
                if is_school:
                    school_zone_violations += 1

                if current_violation is None:
                    current_violation = {
                        'start_timestamp': ts,
                        'zone_type': zone_type,
                        'limit': speed_limit,
                        'max_speed': speed,
                        'duration_seconds': 0,
                        'road_type': road_type,
                    }
                current_violation['duration_seconds'] += time_duration
                current_violation['max_speed'] = max(current_violation['max_speed'], speed)

                # Only emit an event after sustained speeding (or immediately in school zones).
                # This filters out single-point OSM glitches where a nearby road
                # tag briefly lowers the detected limit.
                # Note: excess already includes the tolerance buffer, so > 0 means
                # the driver is meaningfully over the limit.
                sustained = consecutive_over >= CONSECUTIVE_SPEEDING_REQUIRED
                if excess > 0 and (sustained or is_school) and (i % 5 == 0 or is_school):
                    # Show actual excess over posted limit (not tolerance-adjusted) in the description
                    actual_excess = speed - speed_limit
                    events.append({
                        'type': 'speeding',
                        'timestamp': ts,
                        'lat': point.get('latitude'),
                        'lng': point.get('longitude'),
                        'value': round(speed, 1),
                        'limit': speed_limit,
                        'excess': round(actual_excess, 1),
                        'zone_type': zone_type,
                        'severity': 'high' if actual_excess > 15 or is_school else 'medium',
                        'description': (
                            f'{"SCHOOL ZONE: " if is_school else ""}'
                            f'{round(speed, 0)} km/h in a {speed_limit} km/h zone '
                            f'({round(actual_excess, 0)} km/h over)'
                        ),
                    })
            else:
                consecutive_over = 0  # reset streak when within the limit
                if current_violation:
                    speed_violations.append(current_violation)
                    current_violation = None

            if speed > 5 and speed < (speed_limit - 10):
                under_speed_time += time_duration

            if i > 0:
                prev_speed = speed_data[i-1].get('speed', 0)
                speed_changes.append(abs(speed - prev_speed))

        if current_violation:
            speed_violations.append(current_violation)

        speeding_pct = (speeding_time / total_time * 100) if total_time > 0 else 0
        under_speed_pct = (under_speed_time / total_time * 100) if total_time > 0 else 0

        base_speeding_penalty = speeding_pct * 2
        school_zone_penalty = school_zone_violations * 0.5
        score -= base_speeding_penalty + school_zone_penalty
        score -= under_speed_pct * 1

        if speed_changes:
            avg_change = sum(speed_changes) / len(speed_changes)
            variance_penalty = min(20, avg_change * 2)
            score -= variance_penalty
            feedback['speed_variance'] = round(avg_change, 2)

        score = max(0, min(100, score))

        feedback['speeding_percentage'] = round(speeding_pct, 2)
        feedback['under_speed_percentage'] = round(under_speed_pct, 2)
        feedback['school_zone_violations'] = school_zone_violations
        feedback['max_excess_kmh'] = round(max_excess_kmh, 1)
        feedback['speed_violations'] = speed_violations
        feedback['events'] = events

        if school_zone_violations > 0:
            feedback['notes'].append(f'School zone speed violation detected ({school_zone_violations} seconds).')
            feedback['tips'].append('Always slow down to 30 km/h in school zones.')

        if speeding_pct > 10:
            feedback['notes'].append(f'Significant speeding detected ({speeding_pct:.1f}%).')
            feedback['tips'].append('Check your speedometer frequently.')
        elif speeding_pct > 0:
            feedback['notes'].append(f'Minor speeding detected ({speeding_pct:.1f}%).')
        else:
            feedback['notes'].append('Excellent speed compliance.')

        return score, feedback

    def _evaluate_cornering(self, acceleration_data: List[Dict], rotation_data: List[Dict],
                            speed_data: Optional[List[Dict]] = None,
                            heading_data: Optional[List[Dict]] = None) -> Tuple[float, Dict]:
        """Evaluate cornering quality."""
        score = 100.0
        sharp_turn_count = 0
        smooth_turn_count = 0
        jerky_steering_count = 0
        events = []

        feedback = {
            'sharp_turns': 0, 'smooth_turns': 0, 'jerky_steering': 0,
            'lane_discipline': 'good', 'notes': [], 'events': [], 'tips': []
        }

        if not acceleration_data:
            return 50.0, {'notes': ['Insufficient data'], 'events': [], 'tips': []}

        # Apply cooldown so that sustained lateral force during one turn
        # is counted as a single event, not one per sensor sample.
        last_sharp_turn_ts = None
        for i in range(len(acceleration_data)):
            point = acceleration_data[i]
            x_accel = point.get('x') or 0
            lateral_g = abs(x_accel) / self.GRAVITY
            
            # Dynamic threshold based on speed if available
            current_threshold = self.SHARP_TURN_LATERAL_THRESHOLD
            speed_kmh = 0
            if speed_data:
                # Find closest speed point
                ts = point.get('timestamp', 0)
                closest_speed = min(speed_data, key=lambda p: abs(p.get('timestamp', 0) - ts))
                speed_kmh = closest_speed.get('speed', 0)
                # Lower threshold as speed increases
                # e.g. at 100km/h, threshold = 0.45 - (100 * 0.0015) = 0.30g
                current_threshold -= (speed_kmh * self.CORNERING_SPEED_SENSITIVITY)
                current_threshold = max(self.CORNERING_THRESHOLD_FLOOR, current_threshold)

            try:
                ts = float(point.get('timestamp') or 0)
            except (ValueError, TypeError):
                ts = 0

            if lateral_g > current_threshold:
                # Cross-check with GPS heading: only flag if the vehicle is actually turning.
                # Vibration and road roughness can produce lateral g-force without a real turn.
                if not self._heading_changed(ts, heading_data, speed_data):
                    continue

                if last_sharp_turn_ts is None or (ts - last_sharp_turn_ts) >= self.EVENT_COOLDOWN_MS:
                    sharp_turn_count += 1
                    # Penalty is higher at high speeds
                    severity_multiplier = 1.0 + (speed_kmh / 50.0)
                    score -= 8 * severity_multiplier
                    last_sharp_turn_ts = ts
                    events.append({
                        'type': 'sharp_turn',
                        'timestamp': point.get('timestamp'),
                        'lat': point.get('latitude'),
                        'lng': point.get('longitude'),
                        'value': round(lateral_g, 2),
                        'limit': round(current_threshold, 2),
                        'speed': round(speed_kmh, 1),
                        'severity': 'high' if lateral_g > (current_threshold + 0.2) or speed_kmh > 80 else 'medium',
                        'description': f'Sharp turn at {round(lateral_g, 2)}g (Speed: {round(speed_kmh, 0)} km/h)'
                    })
            elif lateral_g < self.SMOOTH_TURN_THRESHOLD and lateral_g > 0.02:
                smooth_turn_count += 1

        if rotation_data:
            for i in range(1, len(rotation_data)):
                prev_rotation = rotation_data[i-1].get('z') or 0
                curr_rotation = rotation_data[i].get('z') or 0
                if abs(curr_rotation - prev_rotation) > self.JERKY_STEERING_RATE_THRESHOLD:
                    jerky_steering_count += 1
                    score -= 5

        smooth_bonus = min(20, smooth_turn_count * 2)
        score += smooth_bonus
        score = max(0, min(100, score))

        feedback['sharp_turns'] = sharp_turn_count
        feedback['smooth_turns'] = smooth_turn_count
        feedback['jerky_steering'] = jerky_steering_count
        feedback['events'] = events

        if sharp_turn_count > 0:
            feedback['notes'].append(f'Some sharp turns detected ({sharp_turn_count}).')
            feedback['tips'].append('Brake BEFORE the turn.')
        else:
            feedback['notes'].append('Excellent cornering!')

        return score, feedback

    def _evaluate_combined_dynamics(self, acceleration_data: List[Dict]) -> Tuple[float, Dict]:
        """
        Evaluates the Friction Circle (combined longitudinal and lateral forces).
        Detects dangerous maneuvers where a student is using too much of the 
        available grip for combined actions (e.g. trail braking too deep into a corner).
        """
        score = 100.0
        violations = 0
        max_total_g = 0
        events = []

        last_event_ts = None
        for p in acceleration_data:
            x_accel = p.get('x') or 0
            y_accel = p.get('y') or 0
            
            # Vector magnitude in g-force
            total_g = math.sqrt(x_accel**2 + y_accel**2) / self.GRAVITY
            
            if total_g > max_total_g:
                max_total_g = total_g

            try:
                ts = float(p.get('timestamp') or 0)
            except (ValueError, TypeError):
                ts = 0

            if total_g > self.FRICTION_CIRCLE_THRESHOLD:
                if last_event_ts is None or (ts - last_event_ts) >= self.EVENT_COOLDOWN_MS:
                    violations += 1
                    score -= 12
                    last_event_ts = ts
                    events.append({
                        'type': 'friction_circle_violation',
                        'timestamp': p.get('timestamp'),
                        'lat': p.get('latitude'),
                        'lng': p.get('longitude'),
                        'value': round(total_g, 2),
                        'severity': 'high' if total_g > 0.8 else 'medium',
                        'description': f'Combined forces exceeded grip limit: {round(total_g, 2)}g'
                    })

        feedback = {
            'violations': violations,
            'max_total_g': round(max_total_g, 2),
            'events': events,
            'notes': [],
            'tips': []
        }

        if violations > 0:
            feedback['notes'].append(f'Dangerous combined maneuvers detected ({violations}).')
            feedback['tips'].append('Avoid heavy braking while turning. Complete your braking in a straight line before entering a corner.')

        return score, feedback

    def _evaluate_vertical_impacts(self, acceleration_data: List[Dict],
                                  orientation_matrix: Optional[np.ndarray] = None) -> Tuple[float, Dict]:
        """
        Detects vertical impacts (potholes, speed bumps) on the Z-axis.
        """
        score = 100.0
        impact_count = 0
        max_z_g = 0
        events = []
        
        last_event_ts = None
        for p in acceleration_data:
            accel_vector = np.array([p.get('x') or 0, p.get('y') or 0, p.get('z') or 0])
            
            if orientation_matrix is not None:
                # In calibrated frame, Z is always vertical (Up)
                vehicle_accel = np.dot(orientation_matrix, accel_vector)
                z_accel = vehicle_accel[2]
            else:
                z_accel = p.get('z') or 0
                
            # Relative to 1g gravity (9.81 m/s^2)
            # Normal driving sees ~1.0g on Z.
            z_variance_g = abs(z_accel - self.GRAVITY) / self.GRAVITY
            
            if z_variance_g > max_z_g:
                max_z_g = z_variance_g
                
            try:
                ts = float(p.get('timestamp') or 0)
            except (ValueError, TypeError):
                ts = 0

            if z_variance_g > self.VERTICAL_IMPACT_THRESHOLD:
                if last_event_ts is None or (ts - last_event_ts) >= 1000: # 1s cooldown
                    impact_count += 1
                    score -= 5
                    last_event_ts = ts
                    events.append({
                        'type': 'vertical_impact',
                        'timestamp': p.get('timestamp'),
                        'lat': p.get('latitude'),
                        'lng': p.get('longitude'),
                        'value': round(z_variance_g, 2),
                        'severity': 'high' if z_variance_g > 0.8 else 'medium',
                        'description': f'Vertical impact detected: {round(z_variance_g, 2)}g variance'
                    })

        feedback = {
            'impact_count': impact_count,
            'max_z_g': round(max_z_g, 2),
            'events': events,
            'notes': [],
            'tips': []
        }

        if impact_count > 3:
            feedback['notes'].append(f'Multiple vertical impacts detected ({impact_count}).')
            feedback['tips'].append('Slow down for speed bumps and keep a better lookout for potholes to avoid vehicle damage.')

        return score, feedback

    def _evaluate_erratic_driving(self, acceleration_data: List[Dict],
                                   speed_data: List[Dict],
                                   gps_data: Optional[List[Dict]] = None,
                                   orientation_matrix: Optional[np.ndarray] = None) -> Tuple[float, Dict]:
        """
        Detects erratic driving patterns:
        1. Harsh acceleration — aggressive forward acceleration bursts
        2. Speed oscillation — repeated acceleration/deceleration cycles (hunting for speed)
        """
        events = []
        harsh_accel_count = 0
        erratic_count = 0
        penalty = 0.0

        # --- Harsh Acceleration Detection (mirror of braking logic) ---
        last_accel_ts = None
        for point in acceleration_data:
            accel_vector = np.array([point.get('x') or 0, point.get('y') or 0, point.get('z') or 0])

            raw_z = point.get('z') or 0
            z_variance_from_gravity = abs(abs(raw_z) - self.GRAVITY) / self.GRAVITY
            if z_variance_from_gravity > self.VERTICAL_IMPACT_THRESHOLD:
                continue

            if orientation_matrix is not None:
                vehicle_accel = np.dot(orientation_matrix, accel_vector)
                y_accel = vehicle_accel[1]
            else:
                y_accel = point.get('y') or 0

            try:
                ts = float(point.get('timestamp') or 0)
            except (ValueError, TypeError):
                ts = 0

            incline_sin = self._get_incline_at(ts, gps_data) if gps_data else 0.0
            true_y_accel = y_accel + (self.GRAVITY * incline_sin)

            # Positive Y = forward acceleration
            accel_g = true_y_accel / self.GRAVITY if true_y_accel > 0 else 0

            if accel_g > self.HARSH_ACCELERATION_THRESHOLD:
                if last_accel_ts is None or (ts - last_accel_ts) >= self.EVENT_COOLDOWN_MS:
                    harsh_accel_count += 1
                    penalty += 8
                    last_accel_ts = ts
                    events.append({
                        'type': 'harsh_acceleration',
                        'timestamp': point.get('timestamp'),
                        'lat': point.get('latitude'),
                        'lng': point.get('longitude'),
                        'value': round(accel_g, 2),
                        'severity': 'high' if accel_g > 0.6 else 'medium',
                        'description': f'Harsh acceleration at {round(accel_g, 2)}g'
                    })

        # --- Speed Oscillation Detection ---
        if len(speed_data) >= self.SPEED_OSCILLATION_WINDOW:
            window = self.SPEED_OSCILLATION_WINDOW
            last_erratic_ts = None

            for start in range(0, len(speed_data) - window + 1, window // 2):
                window_data = speed_data[start:start + window]
                speeds = [p.get('speed', 0) for p in window_data]

                # If driving slow in traffic (stop-and-go), oscillations are normal.
                # Only flag if mean speed is high enough to warrant steady throttle.
                if sum(speeds)/len(speeds) < self.SPEED_OSCILLATION_MIN_SPEED:
                    continue

                direction_changes = 0
                for j in range(2, len(speeds)):
                    prev_delta = speeds[j - 1] - speeds[j - 2]
                    curr_delta = speeds[j] - speeds[j - 1]
                    
                    # Physicist: Only count if the change is above the noise floor (e.g. 2 km/h)
                    if abs(prev_delta) >= self.SPEED_OSCILLATION_MIN_DELTA and \
                       abs(curr_delta) >= self.SPEED_OSCILLATION_MIN_DELTA:
                        if (prev_delta > 0 and curr_delta < 0) or (prev_delta < 0 and curr_delta > 0):
                            direction_changes += 1

                speed_range = max(speeds) - min(speeds)

                try:
                    ts = float(window_data[0].get('timestamp') or 0)
                except (ValueError, TypeError):
                    ts = 0

                if (direction_changes >= self.SPEED_OSCILLATION_MIN_CHANGES and
                        speed_range >= self.SPEED_OSCILLATION_AMPLITUDE):
                    # Instructor: Erratic driving is a sustained pattern, not a one-off.
                    # Use a longer cooldown (10s) between erratic flags.
                    if last_erratic_ts is None or (ts - last_erratic_ts) >= 10000:
                        erratic_count += 1
                        penalty += 5
                        last_erratic_ts = ts
                        events.append({
                            'type': 'erratic_speed',
                            'timestamp': ts,
                            'lat': window_data[0].get('latitude'),
                            'lng': window_data[0].get('longitude'),
                            'value': round(speed_range, 1),
                            'direction_changes': direction_changes,
                            'severity': 'medium' if direction_changes < 5 else 'high',
                            'description': (
                                f'Erratic speed: {direction_changes} significant surges, '
                                f'{round(speed_range, 1)} km/h range'
                            )
                        })

        feedback = {
            'harsh_acceleration_count': harsh_accel_count,
            'erratic_count': erratic_count,
            'penalty': penalty,
            'events': events,
            'notes': [],
            'tips': []
        }

        if harsh_accel_count > 0:
            feedback['notes'].append(f'Harsh acceleration detected ({harsh_accel_count} events).')
            feedback['tips'].append('Apply throttle gradually. Smooth acceleration improves safety and fuel economy.')

        if erratic_count > 0:
            feedback['notes'].append(f'Erratic speed pattern detected ({erratic_count} instances).')
            feedback['tips'].append('Maintain a steady speed. Frequent speed changes indicate poor throttle control.')

        return penalty, feedback

    def _evaluate_lane_discipline(self, heading_data: List[Dict],
                                   speed_data: List[Dict]) -> Tuple[float, Dict]:
        """
        Detects lane discipline issues using heading data:
        1. Weaving — sudden heading changes at speed indicating unsafe lane behaviour
        Uses HEADING_VARIANCE_THRESHOLD and WEAVING_THRESHOLD constants.
        """
        events = []
        weaving_count = 0
        penalty = 0.0

        if not heading_data or len(heading_data) < 5:
            return 0, {'weaving_count': 0, 'penalty': 0, 'events': [], 'notes': [], 'tips': []}

        WINDOW_SIZE = 10
        last_event_ts = None

        for start in range(0, len(heading_data) - WINDOW_SIZE + 1, WINDOW_SIZE // 2):
            window = heading_data[start:start + WINDOW_SIZE]
            headings = [h.get('heading', 0) for h in window]

            avg_ts = sum(float(h.get('timestamp') or 0) for h in window) / len(window)
            speed_at = 0
            if speed_data:
                closest = min(speed_data, key=lambda p: abs((p.get('timestamp') or 0) - avg_ts))
                speed_at = closest.get('speed', 0)

            if speed_at < 30:
                continue

            changes = []
            for j in range(1, len(headings)):
                diff = abs(headings[j] - headings[j - 1])
                if diff > 180:
                    diff = 360 - diff
                changes.append(diff)

            if not changes:
                continue

            max_change = max(changes)

            try:
                ts = float(window[0].get('timestamp') or 0)
            except (ValueError, TypeError):
                ts = 0

            if max_change > self.WEAVING_THRESHOLD and speed_at > 40:
                if last_event_ts is None or (ts - last_event_ts) >= self.EVENT_COOLDOWN_MS:
                    weaving_count += 1
                    penalty += 5
                    last_event_ts = ts

                    # Get lat/lng from the closest speed or heading data point
                    lat, lng = None, None
                    if speed_data:
                        closest_pt = min(speed_data, key=lambda p: abs((p.get('timestamp') or 0) - avg_ts))
                        lat = closest_pt.get('latitude')
                        lng = closest_pt.get('longitude')
                    if (lat is None or lng is None) and window:
                        lat = lat or window[0].get('latitude')
                        lng = lng or window[0].get('longitude')

                    events.append({
                        'type': 'lane_weaving',
                        'timestamp': ts,
                        'lat': lat,
                        'lng': lng,
                        'value': round(max_change, 1),
                        'speed': round(speed_at, 1),
                        'severity': 'high' if max_change > 10 else 'medium',
                        'description': f'Lane weaving: {round(max_change, 1)}° heading change at {round(speed_at, 0)} km/h'
                    })

        feedback = {
            'weaving_count': weaving_count,
            'penalty': penalty,
            'events': events,
            'notes': [],
            'tips': []
        }

        if weaving_count > 0:
            feedback['notes'].append(f'Lane weaving detected ({weaving_count} instances).')
            feedback['tips'].append('Keep your eyes focused further ahead and make small, smooth steering adjustments.')

        return penalty, feedback

    def _build_route_segments(self, speed_data: List[Dict],
                              speed_limit_data: Optional[List[Dict]],
                              events: List[Dict]) -> List[Dict]:
        """Build route segments for map replay."""
        segments = []
        if not speed_data or len(speed_data) < 2:
            return segments

        speeds = [p.get('speed', 0) for p in speed_data]
        avg_speed = sum(speeds) / len(speeds) if speeds else 0
        fallback_limit, _ = self._infer_speed_limit(avg_speed)

        for i in range(1, len(speed_data)):
            curr = speed_data[i]
            prev = speed_data[i-1]
            if not all([prev.get('latitude'), prev.get('longitude'), curr.get('latitude'), curr.get('longitude')]):
                continue

            limit = fallback_limit
            if speed_limit_data:
                sl_entry = self._get_limit_for_point(curr, speed_limit_data)
                if sl_entry:
                    limit = sl_entry.get('speed_limit', fallback_limit) or fallback_limit

            speed = curr.get('speed', 0)
            excess = speed - limit
            color = 'red' if excess > 10 else 'yellow' if excess > 0 else 'green'

            segments.append({
                'start': {'lat': prev.get('latitude'), 'lng': prev.get('longitude')},
                'end': {'lat': curr.get('latitude'), 'lng': curr.get('longitude')},
                'color': color, 'speed': round(speed, 1), 'limit': limit,
                'timestamp': curr.get('timestamp'),
            })
        return segments

    def _calculate_overall(self, braking_score: float, speed_score: float, cornering_score: float) -> float:
        return (braking_score * self.BRAKING_WEIGHT + speed_score * self.SPEED_WEIGHT + cornering_score * self.CORNERING_WEIGHT)

    def _determine_pass(self, overall_score: float, braking_score: float, speed_score: float, cornering_score: float,
                        duration_minutes: float, distance_km: float, 
                        smoothness_score: float = 100.0) -> Tuple[bool, Dict]:
        feedback = {'criteria_met': [], 'criteria_failed': []}
        passed = True
        for score, threshold, label in [
            (overall_score, self.OVERALL_PASS_THRESHOLD, "Overall score"),
            (braking_score, self.CATEGORY_PASS_THRESHOLD, "Braking score"),
            (speed_score, self.CATEGORY_PASS_THRESHOLD, "Speed score"),
            (cornering_score, self.CATEGORY_PASS_THRESHOLD, "Cornering score"),
            (smoothness_score, self.CATEGORY_PASS_THRESHOLD, "Smoothness score")
        ]:
            if score >= threshold: feedback['criteria_met'].append(f'{label}: {score:.1f}/100')
            else:
                feedback['criteria_failed'].append(f'{label} too low: {score:.1f}/100 (need {threshold})')
                passed = False
        
        if duration_minutes >= self.MIN_DURATION_MINUTES: feedback['criteria_met'].append(f'Duration: {duration_minutes:.1f} min')
        else:
            feedback['criteria_failed'].append(f'Duration too short: {duration_minutes:.1f} min')
            passed = False
            
        if distance_km >= self.MIN_DISTANCE_KM: feedback['criteria_met'].append(f'Distance: {distance_km:.1f} km')
        else:
            feedback['criteria_failed'].append(f'Distance too short: {distance_km:.1f} km')
            passed = False
            
        return passed, feedback

    def _generate_summary(self, passed: bool, overall_score: float, braking_score: float, speed_score: float, cornering_score: float,
                        speed_feedback: Optional[Dict] = None, smoothness_score: float = 100.0) -> str:
        if passed:
            return f"Congratulations! You passed with {overall_score:.1f}/100. (Braking: {braking_score:.0f}, Speed: {speed_score:.0f}, Cornering: {cornering_score:.0f}, Smoothness: {smoothness_score:.0f})"
        return f"You did not pass (overall score: {overall_score:.1f}/100). Review your category scores: Braking: {braking_score:.0f}, Speed: {speed_score:.0f}, Cornering: {cornering_score:.0f}, Smoothness: {smoothness_score:.0f}."
