import json
from typing import Dict, List, Optional, Tuple
import math
import numpy as np
from app.services.nosql_repo import NoSQLRepository
try:
    from app.services.ml_evaluator import MLEvaluator
except ImportError:
    MLEvaluator = None
try:
    from app.services.force_ml_model import analyse_ride, ForceDecorrelator
    _FORCE_ML_AVAILABLE = True
except ImportError:
    _FORCE_ML_AVAILABLE = False

class DiagnosticEvaluator:
    """
    Evaluates diagnostic ride data to generate scores and pass/fail determination.
    
    Refactored to treat the phone as a high-fidelity Inertial Measurement Unit (IMU)
    using rigorous physical principles:
    - Gravity Compensation via Low-Pass Filtering
    - Reference Frame Alignment to Vehicle Axes
    - SI-unit based G-Force Evaluation
    - Jerk (da/dt) analysis for erratic control
    """

    # Scoring weights
    BRAKING_WEIGHT = 0.3
    SPEED_WEIGHT = 0.3
    CORNERING_WEIGHT = 0.2
    SMOOTHNESS_WEIGHT = 0.2

    # Pass thresholds
    OVERALL_PASS_THRESHOLD = 75
    CATEGORY_PASS_THRESHOLD = 70
    MIN_DURATION_MINUTES = 5
    MIN_DISTANCE_KM = 1

    # Physical constants
    GRAVITY = 9.81  # m/s²

    # ── Requirement 3: Strict G-Force Evaluation (SI units: m/s²) ──
    # Hard Braking: Flag if forward deceleration > 0.6G (5.88 m/s²)
    HARD_BRAKING_THRESHOLD = 0.6 * 9.81
    # Harsh Cornering: Flag if lateral acceleration magnitude > 0.45G
    HARSH_CORNERING_THRESHOLD = 0.45 * 9.81

    # ── Requirement 4: Jerk Threshold ──
    # Flag erratic control if jerk > 5.0 m/s³ (raised from 2.0 — minor road
    # vibrations and small steering corrections were triggering false positives)
    JERK_THRESHOLD = 5.0
    JERK_HARSH_THRESHOLD = 12.0
    JERK_EVENT_COOLDOWN_MS = 5000  # prevent rapid re-triggering on bumpy roads

    # ── Hard Acceleration ──
    # Raised from 0.3G to 0.4G — 0.3G was triggering on normal highway merges
    HARD_ACCEL_THRESHOLD = 0.4 * 9.81

    # ── Legacy Thresholds (maintained for comprehensive scoring) ──
    SUDDEN_STOP_SPEED_DROP = 10  # km/h — only used with 10 s cooldown now
    VERTICAL_IMPACT_THRESHOLD_G = 0.40  # g variance
    FRICTION_CIRCLE_THRESHOLD_G = 0.60  # g total
    EVENT_COOLDOWN_MS = 3000  # 3 seconds
    SPEED_TOLERANCE_KMH = 5
    CORNERING_HEADING_CHANGE_MIN = 3.0
    CORNERING_HEADING_WINDOW_MS = 3000
    CORNERING_SPEED_SENSITIVITY = 0.002 # G reduction per km/h

    # ── Traffic context ──
    # Speed below this threshold is treated as stop-and-go / urban traffic.
    # Braking threshold is relaxed and smoothness jerk is excluded from average
    # to avoid penalising drivers who are simply stuck in congestion.
    TRAFFIC_SPEED_KMH = 30
    # Fraction of ride samples that must be ≤ TRAFFIC_SPEED_KMH before the
    # evaluator switches into "traffic-aware" mode for the whole ride.
    TRAFFIC_FRACTION_THRESHOLD = 0.25
    # Elevated braking G threshold used when the car is already moving slowly.
    # At <30 km/h a firm-but-normal stop easily exceeds 0.6G; 0.85G is the
    # threshold where it becomes genuinely harsh.
    TRAFFIC_BRAKING_THRESHOLD_G = 0.85

    def __init__(self):
        self.nosql_repo = NoSQLRepository()
        self.ml_evaluator = MLEvaluator() if MLEvaluator else None

    @staticmethod
    def _median_filter(data: List[Dict], window: int = 5) -> List[Dict]:
        """Apply a sliding-window median filter to remove single-sample spikes."""
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
                vals = sorted([p.get(axis) or 0 for p in window_slice])
                filtered_point[axis] = vals[len(vals) // 2]
            filtered.append(filtered_point)
        return filtered

    def _calibrate_orientation(self, acceleration_data: List[Dict], speed_data: List[Dict]) -> Optional[np.ndarray]:
        """
        Requirement 2: Rotate the phone's local coordinate system to match the vehicle's reference frame.
        - Z_vert: Aligned with gravity (Up).
        - Y_long: Aligned with forward acceleration correlated with GPS speed changes.
        - X_lat: Orthogonal to both (Right).
        """
        if not acceleration_data or len(acceleration_data) < 50:
            return None

        # 1. Identify Gravity Vector (Z_vert)
        # Average acceleration over the window to find the stable gravity direction.
        accels = np.array([[p.get('x') or 0, p.get('y') or 0, p.get('z') or 0] for p in acceleration_data])
        avg_accel = np.mean(accels, axis=0)
        norm = np.linalg.norm(avg_accel)
        if norm < 1.0: return None
        z_basis = avg_accel / norm # Points UP

        # 2. Identify Forward Vector (Y_long)
        # Requirement 2: Correlate sustained acceleration with GPS speed changes.
        forward_samples = []
        for i in range(1, len(speed_data)):
            v1 = speed_data[i-1].get('speed', 0) / 3.6 # m/s
            v2 = speed_data[i].get('speed', 0) / 3.6 # m/s
            t1 = speed_data[i-1].get('timestamp', 0) / 1000.0
            t2 = speed_data[i].get('timestamp', 0) / 1000.0
            
            if t2 > t1 and (v2 - v1) / (t2 - t1) > 0.5: # Speed increasing > 0.5 m/s²
                matches = [p for p in acceleration_data if speed_data[i-1].get('timestamp') <= p.get('timestamp') <= speed_data[i].get('timestamp')]
                for m in matches:
                    raw_a = np.array([m.get('x') or 0, m.get('y') or 0, m.get('z') or 0])
                    # Remove gravity component
                    linear_a = raw_a - np.dot(raw_a, z_basis) * z_basis
                    if np.linalg.norm(linear_a) > 0.1:
                        forward_samples.append(linear_a)

        if len(forward_samples) > 10:
            y_basis = np.mean(forward_samples, axis=0)
            y_basis /= np.linalg.norm(y_basis)
        else:
            # Fallback: portrait phone (screen facing driver) → Z axis is forward
            temp_y = np.array([0, 0, 1]) if abs(z_basis[2]) < 0.8 else np.array([1, 0, 0])
            y_basis = temp_y - np.dot(temp_y, z_basis) * z_basis
            y_basis /= np.linalg.norm(y_basis)

        # 3. Lateral Vector (X_lat)
        x_basis = np.cross(y_basis, z_basis)
        x_basis /= np.linalg.norm(x_basis)
        
        # Ensure perfect orthogonality
        y_basis = np.cross(z_basis, x_basis)

        # Matrix: Rows are basis vectors (Sensor -> Vehicle)
        return np.array([x_basis, y_basis, z_basis])

    def _process_physics(self, acceleration_data: List[Dict], 
                         orientation_matrix: np.ndarray,
                         alpha: float = 0.98) -> List[Dict]:
        """
        Requirement 1: Gravity Compensation via Low-Pass Filter.
        Requirement 2: Reference Frame Alignment.
        Requirement 4: Jerk Calculation (da/dt).
        """
        processed = []
        if not acceleration_data: return []

        # Initialize gravity estimate
        gravity_est = np.array([
            acceleration_data[0].get('x') or 0,
            acceleration_data[0].get('y') or 0,
            acceleration_data[0].get('z') or 0
        ])
        
        prev_v_accel = None
        prev_ts_s = None

        for p in acceleration_data:
            ts_ms = p.get('timestamp', 0)
            ts_s = float(ts_ms) / 1000.0
            raw = np.array([p.get('x') or 0, p.get('y') or 0, p.get('z') or 0])
            
            # 1. Gravity Compensation (Requirement 1)
            # High alpha (0.98) ensures we track the steady gravity vector while ignoring bursts.
            gravity_est = alpha * gravity_est + (1.0 - alpha) * raw
            linear_accel_sensor = raw - gravity_est
            
            # 2. Reference Frame Alignment (Requirement 2)
            # v_accel = [Lateral, Longitudinal/Forward, Vertical]
            v_accel = np.dot(orientation_matrix, linear_accel_sensor)
            
            # 3. Jerk Calculation (Requirement 4)
            jerk = 0.0
            if prev_v_accel is not None and prev_ts_s is not None:
                dt = ts_s - prev_ts_s
                if dt > 0.001:
                    jerk = np.linalg.norm(v_accel - prev_v_accel) / dt
            
            processed.append({
                'timestamp': ts_ms,
                'latitude': p.get('latitude'),
                'longitude': p.get('longitude'),
                'lat_accel': v_accel[0],   # m/s² — X lateral (left/right)
                'long_accel': v_accel[1],  # m/s² — Z forward/back (after orientation rotation)
                'vert_accel': v_accel[2],  # m/s² — Y up/down
                'jerk': jerk,              # m/s³
                'raw': raw
            })
            
            prev_v_accel = v_accel
            prev_ts_s = ts_s
            
        return processed

    async def evaluate(self, ride_id: str, ride_data: Dict) -> Dict:
        """Main evaluation pipeline with physics refactor."""
        # 1. Fetch data
        telemetry = await self._fetch_telemetry(ride_id, ride_data)
        acc_raw = telemetry['acceleration']
        speed_data = telemetry['speed']
        heading_data = telemetry['heading']
        speed_limit_data = telemetry['speed_limit']
        
        duration_minutes = ride_data.get('duration_minutes') or 0
        distance_km = ride_data.get('distance_km') or 0

        if not acc_raw:
            return self._empty_result("Insufficient sensor data")

        # 2. Physics Processing Pipeline
        acc_filtered = self._median_filter(acc_raw)
        orientation_matrix = self._calibrate_orientation(acc_filtered[:3000], speed_data)
        if orientation_matrix is None: orientation_matrix = np.eye(3)
        
        # Apply Gravity Compensation and Rotation (Requirement 1, 2, 4, 5)
        physics_data = self._process_physics(acc_filtered, orientation_matrix)

        # 2b. Decorrelate force axes (Gram-Schmidt partial regression)
        # Removes spurious covariance between lat/lon/vert introduced by imperfect
        # orientation alignment and sensor cross-talk, then recomputes jerk.
        decorr_report: Dict = {}
        if _FORCE_ML_AVAILABLE and len(physics_data) >= 50:
            try:
                decorr = ForceDecorrelator()
                physics_data = decorr.fit_transform(physics_data)
                decorr_report = decorr.report()
            except Exception:
                pass

        # 3. High-Fidelity Modules (Requirement 3: Strict G-Force Evaluation)
        traffic_mode = self._detect_traffic_mode(speed_data)
        braking_score, braking_fb = self._evaluate_braking(physics_data, speed_data, traffic_mode=traffic_mode)
        speed_score, speed_fb = self._evaluate_speed(speed_data, speed_limit_data)
        cornering_score, cornering_fb = self._evaluate_cornering(physics_data, heading_data, speed_data=speed_data)
        erratic_penalty, erratic_fb = self._evaluate_erratic_driving(physics_data)
        combined_penalty, combined_fb = self._evaluate_combined_dynamics(physics_data)
        
        # 4. Smoothness & Vertical Impacts
        smoothness_score = self._calculate_smoothness_score(physics_data, speed_data=speed_data, traffic_mode=traffic_mode)
        impact_penalty, impact_fb = self._evaluate_impacts(physics_data)

        # Deduct penalties
        smoothness_score = max(0, smoothness_score - erratic_penalty - impact_penalty - combined_penalty)
        
        overall_score = (
            braking_score * self.BRAKING_WEIGHT +
            speed_score * self.SPEED_WEIGHT +
            cornering_score * self.CORNERING_WEIGHT +
            smoothness_score * self.SMOOTHNESS_WEIGHT
        )

        passed, pass_fb = self._determine_pass(
            overall_score, braking_score, speed_score, cornering_score,
            duration_minutes, distance_km, smoothness_score
        )

        # 5. Metadata and Events
        all_events = self._collect_events(braking_fb, speed_fb, cornering_fb, erratic_fb, combined_fb, impact_fb)
        route_segments = self._build_route_segments(speed_data, speed_limit_data)

        # 6. ML Enhancement — Ridge correlation analysis + ESN temporal detection
        ml_analysis: Dict = {}
        if _FORCE_ML_AVAILABLE and len(physics_data) >= 30:
            try:
                base_thresholds = {
                    'braking':   self.HARD_BRAKING_THRESHOLD,
                    'cornering': self.HARSH_CORNERING_THRESHOLD,
                    'jerk':      self.JERK_THRESHOLD,
                    'grip':      self.FRICTION_CIRCLE_THRESHOLD_G * self.GRAVITY,
                    'vertical':  self.VERTICAL_IMPACT_THRESHOLD_G * self.GRAVITY,
                }
                ml_result = analyse_ride(physics_data, all_events, base_thresholds,
                                        decorr_report=decorr_report)

                # Merge ESN-detected events that don't duplicate existing events
                existing_ts = {e.get('timestamp', 0) for e in all_events}
                for ev in ml_result.get('esn_events', []):
                    ts = ev.get('timestamp', 0)
                    if all(abs(ts - et) > self.EVENT_COOLDOWN_MS for et in existing_ts):
                        all_events.append(ev)
                        existing_ts.add(ts)

                ml_analysis = {
                    'top_correlations': ml_result.get('top_correlations', []),
                    'ridge_weights':    ml_result.get('ridge_weights', {}),
                    'esn_trained':      ml_result.get('esn_trained', False),
                    'esn_event_count':  len(ml_result.get('esn_events', [])),
                    'calibrated_thresholds': {
                        k: round(float(v), 4)
                        for k, v in ml_result.get('calibrated_thresholds', {}).items()
                    },
                    'decorrelation': ml_result.get('decorrelation', {}),
                }
            except Exception:
                pass

        evaluation_result = {
            'braking': braking_fb,
            'speed': speed_fb,
            'cornering': cornering_fb,
            'smoothness': {'score': smoothness_score},
            'erratic_driving': erratic_fb,
            'combined_dynamics': combined_fb,
            'overall': pass_fb,
            'events': all_events,
            'route_segments': route_segments,
            'ml_analysis': ml_analysis,
            'traffic_mode': traffic_mode,
            'summary': self._generate_summary(passed, overall_score, braking_score, speed_score, cornering_score, smoothness_score, traffic_mode=traffic_mode)
        }

        return {
            'braking_score': round(braking_score, 2),
            'speed_score': round(speed_score, 2),
            'cornering_score': round(cornering_score, 2),
            'smoothness_score': round(smoothness_score, 2),
            'overall_score': round(overall_score, 2),
            'passed': passed,
            'evaluation_result': json.dumps(evaluation_result),
            'speed_data': speed_data,
            'route_coords': [
                {'latitude': self._get_lat(p), 'longitude': self._get_lon(p)}
                for p in speed_data
                if self._get_lat(p) is not None
            ],
        }

    def _detect_traffic_mode(self, speed_data: List[Dict]) -> bool:
        """
        Returns True when a significant portion of the ride was spent in
        slow-moving / stop-and-go traffic.  The threshold is intentionally
        loose (25% of samples ≤ 30 km/h) so that a brief detour through a
        school zone does not count as a traffic ride.
        """
        if not speed_data:
            return False
        low_speed_count = sum(1 for p in speed_data if p.get('speed', 0) <= self.TRAFFIC_SPEED_KMH)
        return (low_speed_count / len(speed_data)) >= self.TRAFFIC_FRACTION_THRESHOLD

    def _evaluate_braking(self, physics_data: List[Dict], speed_data: List[Dict], *, traffic_mode: bool = False) -> Tuple[float, Dict]:
        """
        Hard braking evaluation with speed-context awareness.

        At low speeds (stop-and-go traffic) a firm stop routinely exceeds 0.6G
        without representing dangerous driving.  We apply a higher threshold
        (0.85G) and a reduced point deduction when the driver was already
        travelling below TRAFFIC_SPEED_KMH at the moment of braking.
        """
        score = 100.0
        events = []
        last_event_ts = None

        for p in physics_data:
            decel = -p['long_accel']
            ts = p['timestamp']

            # Choose threshold based on current vehicle speed at this sample
            current_speed = self._get_closest_speed(ts, speed_data)
            if current_speed < self.TRAFFIC_SPEED_KMH:
                # Low-speed / traffic stop: apply relaxed threshold
                threshold = self.TRAFFIC_BRAKING_THRESHOLD_G * self.GRAVITY
                deduction = 4  # minor deduction — driver is in traffic
            else:
                threshold = self.HARD_BRAKING_THRESHOLD
                deduction = 10

            if decel > threshold:
                if last_event_ts is None or (ts - last_event_ts) >= self.EVENT_COOLDOWN_MS:
                    score -= deduction
                    last_event_ts = ts
                    g_force = decel / self.GRAVITY
                    severity = 'high' if g_force > 0.85 else ('medium' if g_force > 0.6 else 'low')
                    events.append({
                        'type': 'harsh_braking',
                        'timestamp': ts,
                        'lat': p.get('latitude'),
                        'lng': p.get('longitude'),
                        'value': round(g_force, 2),
                        'severity': severity,
                        'traffic_context': current_speed < self.TRAFFIC_SPEED_KMH,
                        'description': f'Hard braking: {round(g_force, 2)}G ({round(decel, 1)} m/s²)'
                    })

        # Sudden stop check — only fires when speed drops to near-zero AND
        # hasn't fired within the last 10 seconds.  Suppress in traffic mode
        # because complete stops at intersections are expected behaviour.
        sudden_stops = 0
        last_sudden_stop_ts = None
        for i in range(1, len(speed_data)):
            dv = speed_data[i-1].get('speed', 0) - speed_data[i].get('speed', 0)
            end_speed = speed_data[i].get('speed', 0)
            ts = speed_data[i].get('timestamp', 0)
            if dv > self.SUDDEN_STOP_SPEED_DROP and end_speed < 5:
                if last_sudden_stop_ts is None or (ts - last_sudden_stop_ts) >= 10000:
                    sudden_stops += 1
                    # In heavy traffic, stopping at lights is expected — halve the deduction
                    score -= 2 if traffic_mode else 5
                    last_sudden_stop_ts = ts

        return max(0, score), {
            'harsh_braking_events': len(events),
            'sudden_stops': sudden_stops,
            'traffic_mode': traffic_mode,
            'events': events,
            'notes': [f'Detected {len(events)} harsh braking incidents.']
        }

    def _evaluate_cornering(self, physics_data: List[Dict], heading_data: List[Dict], speed_data: Optional[List[Dict]] = None) -> Tuple[float, Dict]:
        """Requirement 3: Harsh Cornering magnitude > speed-dependent threshold."""
        score = 100.0
        events = []
        last_event_ts = None
        
        for p in physics_data:
            ts = p['timestamp']
            lat_accel_mag = abs(p['lat_accel'])
            
            # Dynamic threshold (Requirement 3 enhancement)
            # 0.45g baseline @ 0km/h, -0.002g per km/h
            threshold = self.HARSH_CORNERING_THRESHOLD
            if speed_data:
                speed = self._get_closest_speed(ts, speed_data)
                dynamic_g = max(0.15, 0.45 - (speed * self.CORNERING_SPEED_SENSITIVITY))
                threshold = dynamic_g * self.GRAVITY

            if lat_accel_mag > threshold:
                # Cross-reference with heading change to ensure it's a real turn
                if not self._heading_changed(ts, heading_data): continue

                if last_event_ts is None or (ts - last_event_ts) >= self.EVENT_COOLDOWN_MS:
                    score -= 8
                    last_event_ts = ts
                    g_force = lat_accel_mag / self.GRAVITY
                    events.append({
                        'type': 'sharp_turn',
                        'timestamp': ts,
                        'lat': p.get('latitude'),
                        'lng': p.get('longitude'),
                        'value': round(g_force, 2),
                        'severity': 'high' if g_force > 0.55 else 'medium',
                        'description': f'Harsh cornering: {round(g_force, 2)}G ({round(lat_accel_mag, 1)} m/s²)'
                    })

        return max(0, score), {'sharp_turns': len(events), 'events': events, 'notes': [f'Detected {len(events)} sharp turns.']}

    def _evaluate_combined_dynamics(self, physics_data: List[Dict]) -> Tuple[float, Dict]:
        """Requirement 5: Friction Circle (Combined G-Force) > 0.60G."""
        penalty = 0.0
        events = []
        last_event_ts = None
        max_g = 0.0

        for p in physics_data:
            # Use pre-decorrelation long_accel for friction circle so that genuinely
            # combined braking+cornering manoeuvres are still captured at full force.
            lon_for_friction = p.get('raw_long_accel', p['long_accel'])
            total_accel = math.sqrt(p['lat_accel']**2 + lon_for_friction**2)
            total_g = total_accel / self.GRAVITY
            if total_g > max_g:
                max_g = total_g
            
            if total_g > self.FRICTION_CIRCLE_THRESHOLD_G:
                ts = p['timestamp']
                if last_event_ts is None or (ts - last_event_ts) >= self.EVENT_COOLDOWN_MS:
                    penalty += 12
                    last_event_ts = ts
                    events.append({
                        'type': 'friction_circle_violation',
                        'timestamp': ts,
                        'lat': p.get('latitude'),
                        'lng': p.get('longitude'),
                        'value': round(total_g, 2),
                        'description': f'Friction circle violation: {round(total_g, 2)}G combined force'
                    })
        return penalty, {'penalty': penalty, 'events': events, 'max_total_g': round(max_g, 2)}

    def _get_closest_speed(self, timestamp: float, speed_data: List[Dict]) -> float:
        if not speed_data: return 0.0
        closest = min(speed_data, key=lambda x: abs(x.get('timestamp', 0) - timestamp))
        return closest.get('speed', 0.0)

    def _calculate_jerk_score(self, data: List[Dict]) -> float:
        """Legacy helper for tests. Calculates average jerk for a raw data snippet."""
        # This requires processing through physics pipeline with identity matrix
        physics = self._process_physics(data, np.eye(3))
        if not physics: return 100.0
        return self._calculate_smoothness_score(physics)

    def _evaluate_erratic_driving(self, physics_data: List[Dict]) -> Tuple[float, Dict]:
        """
        Requirement 3: Hard Acceleration > 0.3G.
        Requirement 4: Erratic Control (Jerk > 1.0 m/s³).
        """
        penalty = 0.0
        events = []
        last_accel_ts = None
        last_jerk_ts = None

        for p in physics_data:
            ts = p['timestamp']
            
            # Hard Acceleration (Forward Y)
            accel = p['long_accel']
            if accel > self.HARD_ACCEL_THRESHOLD:
                if last_accel_ts is None or (ts - last_accel_ts) >= self.EVENT_COOLDOWN_MS:
                    penalty += 8
                    last_accel_ts = ts
                    g_force = accel / self.GRAVITY
                    events.append({
                        'type': 'harsh_acceleration',
                        'timestamp': ts,
                        'lat': p.get('latitude'),
                        'lng': p.get('longitude'),
                        'value': round(g_force, 2),
                        'description': f'Hard acceleration: {round(g_force, 2)}G'
                    })
            
            # Erratic Control (Jerk)
            if p['jerk'] > self.JERK_THRESHOLD:
                if last_jerk_ts is None or (ts - last_jerk_ts) >= self.JERK_EVENT_COOLDOWN_MS:
                    penalty += 4
                    last_jerk_ts = ts
                    events.append({
                        'type': 'erratic_control',
                        'timestamp': ts,
                        'lat': p.get('latitude'),
                        'lng': p.get('longitude'),
                        'value': round(p['jerk'], 2),
                        'description': f'Erratic control: Jerk of {round(p["jerk"], 2)} m/s³'
                    })

        return penalty, {'penalty': penalty, 'events': events}

    def _calculate_smoothness_score(self, physics_data: List[Dict], *, speed_data: Optional[List[Dict]] = None, traffic_mode: bool = False) -> float:
        """
        Smoothness score based on jerk.

        Stop-and-go traffic inherently generates high jerk at low speeds
        (clutch releases, gear changes, creeping forward).  We exclude samples
        where the vehicle is travelling below TRAFFIC_SPEED_KMH so that the
        average reflects the driver's actual smoothness on open road.  If no
        higher-speed samples exist (purely urban trip), we fall back to the full
        dataset but apply a milder multiplier (3 instead of 5).
        """
        if not physics_data:
            return 100.0

        if speed_data:
            highway_jerks = [
                p['jerk'] for p in physics_data
                if self._get_closest_speed(p['timestamp'], speed_data) >= self.TRAFFIC_SPEED_KMH
            ]
        else:
            highway_jerks = []

        if len(highway_jerks) >= 20:
            # Evaluate smoothness on above-traffic-speed driving only
            avg_jerk = float(np.median(highway_jerks))  # median is more robust than mean
            multiplier = 5
        else:
            # Mostly urban/traffic ride — use all data with a gentler multiplier
            avg_jerk = float(np.median([p['jerk'] for p in physics_data]))
            multiplier = 3 if traffic_mode else 5

        score = 100.0 - (avg_jerk * multiplier)
        return max(0, min(100, score))

    def _evaluate_impacts(self, physics_data: List[Dict]) -> Tuple[float, Dict]:
        """Detects vertical impacts (potholes, speed bumps)."""
        penalty = 0.0
        events = []
        last_ts = None
        for p in physics_data:
            # vert_accel is already gravity-compensated
            impact_g = abs(p['vert_accel']) / self.GRAVITY
            if impact_g > self.VERTICAL_IMPACT_THRESHOLD_G:
                ts = p['timestamp']
                if last_ts is None or (ts - last_ts) >= 1000:
                    penalty += 5
                    last_ts = ts
                    events.append({
                        'type': 'vertical_impact',
                        'timestamp': ts,
                        'value': round(impact_g, 2),
                        'description': f'Vertical impact: {round(impact_g, 2)}G'
                    })
        return penalty, {'penalty': penalty, 'events': events}

    @staticmethod
    def _get_lat(p: Dict) -> Optional[float]:
        """Read latitude from a data point that may use 'lat' or 'latitude' key."""
        v = p.get('lat') or p.get('latitude')
        return float(v) if v is not None else None

    @staticmethod
    def _get_lon(p: Dict) -> Optional[float]:
        """Read longitude from a data point that may use 'lon' or 'longitude' key."""
        v = p.get('lon') or p.get('longitude')
        return float(v) if v is not None else None

    def _closest_speed_limit(self, lat: Optional[float], lon: Optional[float],
                              speed_limit_data: List[Dict]) -> float:
        """Return nearest speed limit (km/h) by location. Falls back to 50 km/h."""
        if not speed_limit_data:
            return 50
        if lat is None or lon is None:
            return speed_limit_data[0].get('speed_limit', 50)
        best = min(
            speed_limit_data,
            key=lambda x: abs((self._get_lat(x) or 0) - lat) + abs((self._get_lon(x) or 0) - lon)
        )
        return best.get('speed_limit', 50)

    def _evaluate_speed(self, speed_data: List[Dict], speed_limit_data: List[Dict]) -> Tuple[float, Dict]:
        """Standard speed compliance check."""
        score = 100.0
        speeding_points = 0
        events = []
        for p in speed_data:
            speed = p.get('speed', 0)
            lat = self._get_lat(p)
            lon = self._get_lon(p)
            limit = self._closest_speed_limit(lat, lon, speed_limit_data)
            if speed > (limit + self.SPEED_TOLERANCE_KMH):
                speeding_points += 1
                if speeding_points % 10 == 0:
                    events.append({
                        'type': 'speeding',
                        'timestamp': p.get('timestamp'),
                        'value': speed, 'limit': limit,
                        'description': f'Speeding: {round(speed, 0)} in a {limit} zone'
                    })
        score -= (speeding_points * 0.4)
        return max(0, score), {'score': score, 'events': events}

    async def _fetch_telemetry(self, ride_id: str, ride_data: Dict) -> Dict:
        """Harmonizes data sources."""
        try:
            nosql = await self.nosql_repo.get_ride_telemetry(ride_id)
        except Exception: nosql = []

        accel, speed, heading = [], [], []
        for p in nosql:
            ts = p.get('timestamp')
            lat, lon = p.get('latitude'), p.get('longitude')
            if 'x' in p and 'y' in p and 'z' in p:
                accel.append({'timestamp': ts, 'x': p['x'], 'y': p['y'], 'z': p['z'], 'latitude': lat, 'longitude': lon})
            if 'speed' in p: speed.append({'timestamp': ts, 'speed': p['speed'], 'latitude': lat, 'longitude': lon})
            if 'heading' in p: heading.append({'timestamp': ts, 'heading': p['heading']})

        if not accel: accel = json.loads(ride_data.get('acceleration_data') or '[]')
        if not speed: speed = json.loads(ride_data.get('speed_data') or '[]')
        if not heading: heading = json.loads(ride_data.get('heading_data') or '[]')
        
        return {
            'acceleration': accel, 'speed': speed, 'heading': heading,
            'speed_limit': json.loads(ride_data.get('speed_limit_data') or '[]')
        }

    def _heading_changed(self, timestamp: float, heading_data: List[Dict]) -> bool:
        if not heading_data: return True
        nearby = [h for h in heading_data if abs(h.get('timestamp', 0) - timestamp) < self.CORNERING_HEADING_WINDOW_MS]
        if len(nearby) < 2: return True
        headings = [h.get('heading', 0) for h in nearby]
        diffs = [abs(headings[i] - headings[i-1]) for i in range(1, len(headings))]
        max_diff = max([(d if d <= 180 else 360-d) for d in diffs])
        return max_diff >= self.CORNERING_HEADING_CHANGE_MIN

    def _collect_events(self, *feedbacks) -> List[Dict]:
        all_e = []
        for fb in feedbacks: all_e.extend(fb.get('events', []))
        return sorted(all_e, key=lambda x: x.get('timestamp', 0))

    def _build_route_segments(self, speed_data: List[Dict], speed_limit_data: List[Dict]) -> List[Dict]:
        segments = []
        for i in range(1, len(speed_data)):
            p1, p2 = speed_data[i-1], speed_data[i]
            lat1, lon1 = self._get_lat(p1), self._get_lon(p1)
            lat2, lon2 = self._get_lat(p2), self._get_lon(p2)
            if lat1 and lat2:
                segments.append({
                    'start': {'lat': lat1, 'lng': lon1},
                    'end': {'lat': lat2, 'lng': lon2},
                    'speed': p2.get('speed'), 'timestamp': p2.get('timestamp')
                })
        return segments

    def _determine_pass(self, overall, b, s, c, duration, distance, smoothness) -> Tuple[bool, Dict]:
        fb = {'criteria_met': [], 'criteria_failed': []}
        passed = True
        for score, thresh, name in [(overall, self.OVERALL_PASS_THRESHOLD, "Overall"), (b, self.CATEGORY_PASS_THRESHOLD, "Braking"), (s, self.CATEGORY_PASS_THRESHOLD, "Speed"), (c, self.CATEGORY_PASS_THRESHOLD, "Cornering"), (smoothness, self.CATEGORY_PASS_THRESHOLD, "Smoothness")]:
            if score >= thresh: fb['criteria_met'].append(f"{name}: {score:.1f}")
            else:
                fb['criteria_failed'].append(f"{name} too low: {score:.1f}"); passed = False
        if duration < self.MIN_DURATION_MINUTES: fb['criteria_failed'].append(f"Duration: {duration:.1f} min"); passed = False
        if distance < self.MIN_DISTANCE_KM: fb['criteria_failed'].append(f"Distance: {distance:.1f} km"); passed = False
        return passed, fb

    def _generate_summary(self, passed, o, b, s, c, sm, *, traffic_mode: bool = False) -> str:
        res = "PASSED" if passed else "FAILED"
        traffic_tag = " [traffic-aware]" if traffic_mode else ""
        return f"{res}: Score {o:.1f} (B:{b:.0f} S:{s:.0f} C:{c:.0f} Sm:{sm:.0f}){traffic_tag}"

    def _empty_result(self, msg: str) -> Dict:
        return {
            'overall_score': 0,
            'braking_score': 0,
            'speed_score': 0,
            'cornering_score': 0,
            'smoothness_score': 0,
            'passed': False,
            'evaluation_result': json.dumps({'summary': msg}),
            'speed_data': [],
            'route_segments': [],
        }
