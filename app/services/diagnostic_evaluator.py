import json
from typing import Dict, List, Optional, Tuple
import math
from app.services.nosql_repo import NoSQLRepository

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
    BRAKING_WEIGHT = 0.4
    SPEED_WEIGHT = 0.35
    CORNERING_WEIGHT = 0.25

    # Pass thresholds
    OVERALL_PASS_THRESHOLD = 75
    CATEGORY_PASS_THRESHOLD = 70
    MIN_DURATION_MINUTES = 20
    MIN_DISTANCE_KM = 5

    # Physical constants
    GRAVITY = 9.81  # m/s²

    # Braking thresholds
    # 0.6g = genuine hard braking; lower values are normal deceleration / road vibration
    HARSH_BRAKING_THRESHOLD = 0.6  # g
    SUDDEN_STOP_SPEED_DROP = 10  # km/h drop within the sampling window
    SMOOTH_BRAKING_MIN = 0.1  # g minimum for smooth braking bonus
    SMOOTH_BRAKING_MAX = 0.4  # g maximum for smooth braking bonus

    # Cornering thresholds
    # 0.45g = aggressive lane change / sharp turn; normal city corners are ~0.2-0.3g
    SHARP_TURN_LATERAL_THRESHOLD = 0.45  # g lateral acceleration
    SMOOTH_TURN_THRESHOLD = 0.2  # Below this is smooth

    # Minimum milliseconds between two events of the same type.
    # Prevents consecutive sensor samples from one braking/cornering action
    # being counted as dozens of separate events.
    EVENT_COOLDOWN_MS = 3000  # 3 seconds

    # Gyroscope: minimum change in rad/s between samples to count as jerky steering
    JERKY_STEERING_RATE_THRESHOLD = 1.0  # rad/s (was 0.5 — too sensitive to normal corrections)

    # Speed limits (fallback when no actual data available)
    RESIDENTIAL_LIMIT = 50  # km/h
    ARTERIAL_LIMIT = 60  # km/h
    HIGHWAY_LIMIT = 80  # km/h

    # School zone penalty multiplier
    SCHOOL_ZONE_PENALTY_MULTIPLIER = 2.0

    # Lane discipline thresholds
    HEADING_VARIANCE_THRESHOLD = 3.0  # degrees - high variance when driving straight
    WEAVING_THRESHOLD = 5.0  # degrees - sudden heading change at speed

    def __init__(self):
        self.nosql_repo = NoSQLRepository()

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
                heading_data.append({'timestamp': ts, 'heading': p['heading']})

        # Fallback to ride_data JSON blobs if NoSQL is empty (transition/hybrid support)
        if not acceleration_data:
            acceleration_data = json.loads(ride_data.get('acceleration_data', '[]'))
        if not rotation_data:
            rotation_data = json.loads(ride_data.get('rotation_data', '[]'))
        if not speed_data:
            speed_data = json.loads(ride_data.get('speed_data', '[]'))
        if not heading_data:
            heading_data = json.loads(ride_data.get('heading_data', '[]'))

        speed_limit_data = json.loads(ride_data.get('speed_limit_data', '[]'))
        human_feedback = json.loads(ride_data.get('human_feedback', '[]'))

        duration_minutes = ride_data.get('duration_minutes', 0)
        distance_km = ride_data.get('distance_km', 0)

        # Calculate individual scores
        braking_score, braking_feedback = self._evaluate_braking(acceleration_data, speed_data)
        speed_score, speed_feedback = self._evaluate_speed(
            speed_data, duration_minutes, speed_limit_data=speed_limit_data
        )
        cornering_score, cornering_feedback = self._evaluate_cornering(
            acceleration_data, rotation_data, heading_data=heading_data
        )

        # Calculate overall score
        overall_score = self._calculate_overall(braking_score, speed_score, cornering_score)

        # Determine pass/fail
        passed, pass_feedback = self._determine_pass(
            overall_score, braking_score, speed_score, cornering_score,
            duration_minutes, distance_km
        )

        # Aggregate all events
        all_events = []
        all_events.extend(braking_feedback.get('events', []))
        all_events.extend(speed_feedback.get('events', []))
        all_events.extend(cornering_feedback.get('events', []))
        
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
            'overall': pass_feedback,
            'events': all_events,
            'route_segments': route_segments,
            'human_feedback': human_feedback,
            'summary': self._generate_summary(
                passed, overall_score, braking_score, speed_score, cornering_score,
                speed_feedback
            )
        }

        return {
            'braking_score': round(braking_score, 2),
            'speed_score': round(speed_score, 2),
            'cornering_score': round(cornering_score, 2),
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

    def _evaluate_braking(self, acceleration_data: List[Dict], speed_data: List[Dict]) -> Tuple[float, Dict]:
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
        # Use a cooldown so that consecutive samples from the same braking action
        # are grouped into a single event rather than each sample being counted separately.
        last_harsh_braking_ts = None
        for i in range(len(acceleration_data)):
            point = acceleration_data[i]
            y_accel = point.get('y', 0)
            z_accel = point.get('z', 0)

            y_decel_g = abs(y_accel) / self.GRAVITY if y_accel < 0 else 0
            z_decel_g = abs(z_accel) / self.GRAVITY if z_accel < 0 else 0
            deceleration_g = max(y_decel_g, z_decel_g)

            try:
                ts = float(point.get('timestamp') or 0)
            except (ValueError, TypeError):
                ts = 0

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
                        'severity': 'high' if deceleration_g > 0.8 else 'medium',
                        'description': f'Harsh braking at {round(deceleration_g, 2)}g force'
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

            excess = speed - speed_limit

            try:
                ts = float(point.get('timestamp', 0))
            except (ValueError, TypeError):
                ts = 0

            if excess > 0:
                consecutive_over += 1
                speeding_time += time_duration
                if excess > max_excess_kmh:
                    max_excess_kmh = excess
                is_school = zone_type == "school"
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
                sustained = consecutive_over >= CONSECUTIVE_SPEEDING_REQUIRED
                if excess > 5 and (sustained or is_school) and (i % 5 == 0 or is_school):
                    events.append({
                        'type': 'speeding',
                        'timestamp': ts,
                        'lat': point.get('latitude'),
                        'lng': point.get('longitude'),
                        'value': round(speed, 1),
                        'limit': speed_limit,
                        'excess': round(excess, 1),
                        'zone_type': zone_type,
                        'severity': 'high' if excess > 15 or is_school else 'medium',
                        'description': (
                            f'{"SCHOOL ZONE: " if is_school else ""}'
                            f'{round(speed, 0)} km/h in a {speed_limit} km/h zone '
                            f'({round(excess, 0)} km/h over)'
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
            x_accel = point.get('x', 0)
            lateral_g = abs(x_accel) / self.GRAVITY
            y_accel = point.get('y', 0)
            y_lateral_g = abs(y_accel) / self.GRAVITY
            if y_lateral_g > lateral_g and lateral_g < 0.05:
                lateral_g = y_lateral_g

            try:
                ts = float(point.get('timestamp') or 0)
            except (ValueError, TypeError):
                ts = 0

            if lateral_g > self.SHARP_TURN_LATERAL_THRESHOLD:
                if last_sharp_turn_ts is None or (ts - last_sharp_turn_ts) >= self.EVENT_COOLDOWN_MS:
                    sharp_turn_count += 1
                    score -= 8
                    last_sharp_turn_ts = ts
                    events.append({
                        'type': 'sharp_turn',
                        'timestamp': point.get('timestamp'),
                        'lat': point.get('latitude'),
                        'lng': point.get('longitude'),
                        'value': round(lateral_g, 2),
                        'severity': 'high' if lateral_g > 0.65 else 'medium',
                        'description': f'Sharp turn at {round(lateral_g, 2)}g'
                    })
            elif lateral_g < self.SMOOTH_TURN_THRESHOLD and lateral_g > 0.02:
                smooth_turn_count += 1

        if rotation_data:
            for i in range(1, len(rotation_data)):
                prev_rotation = rotation_data[i-1].get('z', 0)
                curr_rotation = rotation_data[i].get('z', 0)
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
                        duration_minutes: float, distance_km: float) -> Tuple[bool, Dict]:
        feedback = {'criteria_met': [], 'criteria_failed': []}
        passed = True
        for score, threshold, label in [
            (overall_score, self.OVERALL_PASS_THRESHOLD, "Overall score"),
            (braking_score, self.CATEGORY_PASS_THRESHOLD, "Braking score"),
            (speed_score, self.CATEGORY_PASS_THRESHOLD, "Speed score"),
            (cornering_score, self.CATEGORY_PASS_THRESHOLD, "Cornering score")
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
                        speed_feedback: Optional[Dict] = None) -> str:
        if passed:
            return f"Congratulations! You passed with {overall_score:.1f}/100."
        return f"You did not pass (overall score: {overall_score:.1f}/100)."
