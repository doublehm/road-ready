import json
from typing import Dict, List, Optional, Tuple
import math

from app.services.nosql_repo import NoSQLRepository

class DiagnosticEvaluator:
    """
    Evaluates diagnostic ride data to generate scores and pass/fail determination.
    """
    # ... (rest of the class constants remain same)

    def __init__(self):
        self.nosql_repo = NoSQLRepository()

    async def evaluate(self, ride_id: str, ride_data: Dict) -> Dict:
        """
        Main evaluation method that processes all sensor data and generates scores.
        Fetches telemetry from NoSQL if not provided in ride_data.
        """
        # Fetch telemetry from NoSQL
        nosql_telemetry = await self.nosql_repo.get_ride_telemetry(ride_id)
        nosql_events = await self.nosql_repo.get_ride_events(ride_id)

        # Parse sensor data from ride_data (legacy/metadata) or NoSQL
        # We prioritize NoSQL for granular telemetry
        
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

        # Fallback to ride_data JSON blobs if NoSQL is empty (transition period)
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
            'evaluation_result': json.dumps(evaluation_result)
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

        # Analyze acceleration data for harsh braking
        # Phone orientation in car varies, so check both Y and Z axes for deceleration.
        # In portrait mount: Y = forward/backward, Z = perpendicular to screen
        # In landscape mount: Z = forward/backward, Y = up/down
        for i in range(len(acceleration_data)):
            point = acceleration_data[i]
            y_accel = point.get('y', 0)
            z_accel = point.get('z', 0)

            # Use the strongest deceleration from either axis
            y_decel_g = abs(y_accel) / self.GRAVITY if y_accel < 0 else 0
            z_decel_g = abs(z_accel) / self.GRAVITY if z_accel < 0 else 0
            deceleration_g = max(y_decel_g, z_decel_g)

            if deceleration_g > self.HARSH_BRAKING_THRESHOLD:
                harsh_braking_count += 1
                score -= 10
                events.append({
                    'type': 'harsh_braking',
                    'timestamp': point.get('timestamp'),
                    'lat': point.get('latitude'),
                    'lng': point.get('longitude'),
                    'value': round(deceleration_g, 2),
                    'severity': 'high' if deceleration_g > 0.6 else 'medium',
                    'description': f'Harsh braking at {round(deceleration_g, 2)}g force'
                })
            elif deceleration_g >= self.SMOOTH_BRAKING_MIN and deceleration_g <= self.SMOOTH_BRAKING_MAX:
                smooth_braking_count += 1

        # Analyze speed data for sudden stops
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
                    sudden_stop_count += 1
                    score -= 15
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

        # Only use if within 60 seconds (60000ms or 60s depending on unit)
        # Mobile app seems to use ms for some and s for others.
        # If best_diff > 100000, it's likely a ms vs s mismatch.
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
        has_actual_limits = speed_limit_data and len(speed_limit_data) > 0

        # Fallback: infer from average speed
        speeds = [point.get('speed', 0) for point in speed_data]
        avg_speed = sum(speeds) / len(speeds) if speeds else 0
        fallback_limit, fallback_road_type = self._infer_speed_limit(avg_speed)

        for i in range(len(speed_data)):
            point = speed_data[i]
            speed = point.get('speed', 0)
            time_duration = 1
            total_time += time_duration

            # Determine speed limit for this point
            speed_limit = fallback_limit
            zone_type = "regular"
            road_type = fallback_road_type

            if has_actual_limits:
                sl_entry = self._get_limit_for_point(point, speed_limit_data)
                if sl_entry:
                    speed_limit = sl_entry.get('speed_limit', fallback_limit) or fallback_limit
                    zone_type = sl_entry.get('zone_type', 'regular')
                    road_type = sl_entry.get('road_type', fallback_road_type)

            # Also check inline speed_limit field (from live-evaluate requests)
            inline_limit = point.get('speed_limit')
            if inline_limit and inline_limit > 0:
                speed_limit = inline_limit

            excess = speed - speed_limit

            try:
                ts = float(point.get('timestamp', 0))
            except (ValueError, TypeError):
                ts = 0

            # Check for speeding
            if excess > 0:
                speeding_time += time_duration

                # Track max excess
                if excess > max_excess_kmh:
                    max_excess_kmh = excess

                # School zone violations
                is_school = zone_type == "school"
                if is_school:
                    school_zone_violations += 1

                # Penalty multiplier for school zones
                penalty_mult = self.SCHOOL_ZONE_PENALTY_MULTIPLIER if is_school else 1.0

                # Track violation segments
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

                # Record significant speeding events (>5 km/h over, sampled to avoid flooding)
                if excess > 5 and (i % 5 == 0 or is_school):
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
                # End current violation segment
                if current_violation:
                    speed_violations.append(current_violation)
                    current_violation = None

            # Check for under-speed (excluding stops)
            if speed > 5 and speed < (speed_limit - 10):
                under_speed_time += time_duration

            # Track speed changes
            if i > 0:
                prev_speed = speed_data[i-1].get('speed', 0)
                speed_changes.append(abs(speed - prev_speed))

        # Close any open violation segment
        if current_violation:
            speed_violations.append(current_violation)

        # Calculate percentages
        speeding_pct = (speeding_time / total_time * 100) if total_time > 0 else 0
        under_speed_pct = (under_speed_time / total_time * 100) if total_time > 0 else 0

        # Penalties
        base_speeding_penalty = speeding_pct * 2  # -2 points per 1% time speeding
        school_zone_penalty = school_zone_violations * 0.5  # Extra penalty per school zone second
        score -= base_speeding_penalty + school_zone_penalty
        score -= under_speed_pct * 1

        # Speed variance penalty
        avg_change = 0
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

        # Generate notes
        if school_zone_violations > 0:
            feedback['notes'].append(
                f'School zone speed violation detected ({school_zone_violations} seconds over limit). '
                f'School zones require extra caution.'
            )
            feedback['tips'].append(
                'Always slow down to 30 km/h in school zones (Mon-Fri 8am-5pm). '
                'Watch for school zone signs and flashing lights.'
            )

        if speeding_pct > 10:
            feedback['notes'].append(
                f'Significant speeding detected ({speeding_pct:.1f}% of time over limit). '
                f'Maximum excess: {max_excess_kmh:.0f} km/h over the limit.'
            )
            feedback['tips'].append(
                'Check your speedometer frequently. Use cruise control on highways when safe.'
            )
        elif speeding_pct > 5:
            feedback['notes'].append(
                f'Some speeding detected ({speeding_pct:.1f}% of time). '
                f'Be more mindful of posted speed limits.'
            )
            feedback['tips'].append(
                'Glance at your speedometer every 10-15 seconds to maintain awareness.'
            )
        elif speeding_pct > 0:
            feedback['notes'].append(
                f'Minor speeding detected ({speeding_pct:.1f}% of time). Almost perfect compliance!'
            )
        else:
            feedback['notes'].append('Excellent speed limit compliance throughout the ride.')

        if under_speed_pct > 10:
            feedback['notes'].append(
                'Driving too slowly can impede traffic flow and be dangerous.'
            )
            feedback['tips'].append(
                'Maintain a speed close to the posted limit when conditions allow.'
            )

        if avg_change > 5:
            feedback['notes'].append('Speed was inconsistent. Work on maintaining a steady pace.')
            feedback['tips'].append(
                'Use gentle throttle adjustments to maintain constant speed.'
            )

        if has_actual_limits:
            feedback['notes'].append(
                'Speed evaluation used real-time speed limit data from road maps.'
            )

        return score, feedback

    def _evaluate_cornering(self, acceleration_data: List[Dict], rotation_data: List[Dict],
                            heading_data: Optional[List[Dict]] = None) -> Tuple[float, Dict]:
        """Evaluate cornering quality based on lateral acceleration, rotation, and heading stability."""
        score = 100.0
        sharp_turn_count = 0
        smooth_turn_count = 0
        jerky_steering_count = 0
        events = []

        feedback = {
            'sharp_turns': 0,
            'smooth_turns': 0,
            'jerky_steering': 0,
            'lane_discipline': 'good',
            'notes': [],
            'events': [],
            'tips': []
        }

        if not acceleration_data:
            return 50.0, {'notes': ['Insufficient acceleration data for evaluation'], 'events': [], 'tips': []}

        # Analyze lateral acceleration
        # In portrait mount: X = lateral (turns). In landscape: Y could be lateral.
        # Use X axis as primary lateral indicator (most common phone orientation).
        for i in range(len(acceleration_data)):
            point = acceleration_data[i]
            x_accel = point.get('x', 0)
            lateral_g = abs(x_accel) / self.GRAVITY
            # Also check if Y axis shows stronger lateral force (landscape mount)
            y_accel = point.get('y', 0)
            y_lateral_g = abs(y_accel) / self.GRAVITY
            # Use the larger value but only if X is low (suggests different orientation)
            if y_lateral_g > lateral_g and lateral_g < 0.05:
                lateral_g = y_lateral_g

            if lateral_g > self.SHARP_TURN_LATERAL_THRESHOLD:
                sharp_turn_count += 1
                score -= 8
                events.append({
                    'type': 'sharp_turn',
                    'timestamp': point.get('timestamp'),
                    'lat': point.get('latitude'),
                    'lng': point.get('longitude'),
                    'value': round(lateral_g, 2),
                    'severity': 'high' if lateral_g > 0.5 else 'medium',
                    'description': f'Sharp turn with {round(lateral_g, 2)}g lateral force'
                })
            elif lateral_g < self.SMOOTH_TURN_THRESHOLD and lateral_g > 0.02:
                smooth_turn_count += 1

        # Analyze rotation data for jerky steering
        if rotation_data:
            for i in range(1, len(rotation_data)):
                prev_rotation = rotation_data[i-1].get('z', 0)
                curr_rotation = rotation_data[i].get('z', 0)
                rotation_change = abs(curr_rotation - prev_rotation)

                if rotation_change > 0.5:
                    jerky_steering_count += 1
                    score -= 5

        # Lane discipline analysis from heading data
        weaving_count = 0
        if heading_data and len(heading_data) > 10:
            # Analyze heading stability in windows of 10 samples
            window_size = 10
            for start in range(0, len(heading_data) - window_size, window_size):
                window = heading_data[start:start + window_size]
                headings = [h.get('heading', 0) for h in window if h.get('heading') is not None]

                if len(headings) >= 5:
                    # Calculate heading variance
                    avg_heading = sum(headings) / len(headings)
                    variance = sum((h - avg_heading) ** 2 for h in headings) / len(headings)
                    std_dev = math.sqrt(variance)

                    if std_dev > self.HEADING_VARIANCE_THRESHOLD:
                        weaving_count += 1

            if weaving_count > 5:
                score -= min(15, weaving_count * 2)
                feedback['lane_discipline'] = 'poor'
                feedback['notes'].append(
                    f'Excessive lane weaving detected ({weaving_count} instances). '
                    f'Keep the vehicle centered in your lane.'
                )
                feedback['tips'].append(
                    'Look further ahead down the road rather than directly in front of the car. '
                    'This naturally improves lane discipline.'
                )
            elif weaving_count > 2:
                score -= weaving_count
                feedback['lane_discipline'] = 'fair'
                feedback['notes'].append(
                    'Some lane wandering detected. Focus on smooth, steady steering.'
                )
            else:
                feedback['lane_discipline'] = 'good'

        # Bonus for smooth turns
        smooth_bonus = min(20, smooth_turn_count * 2)
        score += smooth_bonus

        score = max(0, min(100, score))

        feedback['sharp_turns'] = sharp_turn_count
        feedback['smooth_turns'] = smooth_turn_count
        feedback['jerky_steering'] = jerky_steering_count
        feedback['events'] = events

        if sharp_turn_count > 5:
            feedback['notes'].append(f'Too many sharp turns ({sharp_turn_count}). Slow down before corners.')
            feedback['tips'].append(
                'Brake BEFORE the turn, not during it. Enter turns at a controlled speed and '
                'accelerate gently after the apex.'
            )
        elif sharp_turn_count > 0:
            feedback['notes'].append(f'Some sharp turns detected ({sharp_turn_count}). Practice gradual steering.')
            feedback['tips'].append(
                'Use the "slow in, smooth out" technique for turns.'
            )
        else:
            feedback['notes'].append('Excellent cornering technique!')

        if jerky_steering_count > 10:
            feedback['notes'].append('Steering inputs are too abrupt. Make smoother, more gradual adjustments.')
            feedback['tips'].append(
                'Hold the steering wheel at 9 and 3 positions. Make smooth, flowing movements.'
            )

        if smooth_turn_count > 10:
            feedback['notes'].append('Great smooth turning technique.')

        return score, feedback

    def _build_route_segments(self, speed_data: List[Dict],
                              speed_limit_data: Optional[List[Dict]],
                              events: List[Dict]) -> List[Dict]:
        """
        Build color-coded route segments for map replay.
        Each segment has start/end coords, color (green/yellow/red), and metadata.
        """
        segments = []
        if not speed_data or len(speed_data) < 2:
            return segments

        has_limits = speed_limit_data and len(speed_limit_data) > 0
        speeds = [p.get('speed', 0) for p in speed_data]
        avg_speed = sum(speeds) / len(speeds) if speeds else 0
        fallback_limit, _ = self._infer_speed_limit(avg_speed)

        for i in range(1, len(speed_data)):
            prev = speed_data[i-1]
            curr = speed_data[i]

            prev_lat = prev.get('latitude')
            prev_lng = prev.get('longitude')
            curr_lat = curr.get('latitude')
            curr_lng = curr.get('longitude')

            if not all([prev_lat, prev_lng, curr_lat, curr_lng]):
                continue

            speed = curr.get('speed', 0)

            # Get speed limit for this segment
            limit = fallback_limit
            zone_type = 'regular'
            if has_limits:
                sl_entry = self._get_limit_for_point(curr, speed_limit_data)
                if sl_entry:
                    limit = sl_entry.get('speed_limit', fallback_limit) or fallback_limit
                    zone_type = sl_entry.get('zone_type', 'regular')

            # Determine segment color
            excess = speed - limit
            if excess > 10:
                color = 'red'
                status = 'over_limit'
            elif excess > 0:
                color = 'yellow'
                status = 'near_limit'
            else:
                color = 'green'
                status = 'compliant'

            segments.append({
                'start': {'lat': prev_lat, 'lng': prev_lng},
                'end': {'lat': curr_lat, 'lng': curr_lng},
                'color': color,
                'status': status,
                'speed': round(speed, 1),
                'limit': limit,
                'zone_type': zone_type,
                'timestamp': curr.get('timestamp'),
            })

        return segments

    def _calculate_overall(self, braking_score: float, speed_score: float, cornering_score: float) -> float:
        """Calculate weighted overall score."""
        overall = (
            braking_score * self.BRAKING_WEIGHT +
            speed_score * self.SPEED_WEIGHT +
            cornering_score * self.CORNERING_WEIGHT
        )
        return overall

    def _determine_pass(
        self,
        overall_score: float,
        braking_score: float,
        speed_score: float,
        cornering_score: float,
        duration_minutes: float,
        distance_km: float
    ) -> Tuple[bool, Dict]:
        """Determine if the ride passes all criteria."""
        feedback = {'criteria_met': [], 'criteria_failed': []}
        passed = True

        if overall_score >= self.OVERALL_PASS_THRESHOLD:
            feedback['criteria_met'].append(f'Overall score: {overall_score:.1f}/100')
        else:
            feedback['criteria_failed'].append(f'Overall score too low: {overall_score:.1f}/100 (need {self.OVERALL_PASS_THRESHOLD})')
            passed = False

        if braking_score >= self.CATEGORY_PASS_THRESHOLD:
            feedback['criteria_met'].append(f'Braking score: {braking_score:.1f}/100')
        else:
            feedback['criteria_failed'].append(f'Braking score too low: {braking_score:.1f}/100 (need {self.CATEGORY_PASS_THRESHOLD})')
            passed = False

        if speed_score >= self.CATEGORY_PASS_THRESHOLD:
            feedback['criteria_met'].append(f'Speed score: {speed_score:.1f}/100')
        else:
            feedback['criteria_failed'].append(f'Speed score too low: {speed_score:.1f}/100 (need {self.CATEGORY_PASS_THRESHOLD})')
            passed = False

        if cornering_score >= self.CATEGORY_PASS_THRESHOLD:
            feedback['criteria_met'].append(f'Cornering score: {cornering_score:.1f}/100')
        else:
            feedback['criteria_failed'].append(f'Cornering score too low: {cornering_score:.1f}/100 (need {self.CATEGORY_PASS_THRESHOLD})')
            passed = False

        if duration_minutes >= self.MIN_DURATION_MINUTES:
            feedback['criteria_met'].append(f'Duration: {duration_minutes:.1f} min')
        else:
            feedback['criteria_failed'].append(f'Duration too short: {duration_minutes:.1f} min (need {self.MIN_DURATION_MINUTES})')
            passed = False

        if distance_km >= self.MIN_DISTANCE_KM:
            feedback['criteria_met'].append(f'Distance: {distance_km:.1f} km')
        else:
            feedback['criteria_failed'].append(f'Distance too short: {distance_km:.1f} km (need {self.MIN_DISTANCE_KM})')
            passed = False

        return passed, feedback

    def _generate_summary(
        self,
        passed: bool,
        overall_score: float,
        braking_score: float,
        speed_score: float,
        cornering_score: float,
        speed_feedback: Optional[Dict] = None
    ) -> str:
        """Generate a human-readable summary of the evaluation."""
        if passed:
            summary = f"Congratulations! You passed the diagnostic ride with an overall score of {overall_score:.1f}/100. "
            summary += "You've demonstrated sufficient driving skills to skip the Basics module and proceed directly to Advanced training. "
            summary += "You've potentially saved $800+ in training costs!"
        else:
            summary = f"You did not pass the diagnostic ride (overall score: {overall_score:.1f}/100). "

            scores = [
                ('braking', braking_score),
                ('speed control', speed_score),
                ('cornering', cornering_score)
            ]
            scores.sort(key=lambda x: x[1])
            weakest_area = scores[0][0]
            strongest_area = scores[-1][0]

            summary += f"Your weakest area is {weakest_area} ({scores[0][1]:.0f}/100). "
            summary += f"Your strongest area is {strongest_area} ({scores[-1][1]:.0f}/100). "

            # Add specific improvement guidance
            if weakest_area == 'braking':
                summary += "Focus on smooth, gradual braking. Start braking earlier and apply pressure progressively. "
            elif weakest_area == 'speed control':
                summary += "Pay closer attention to posted speed limits and maintain a consistent speed. "
                if speed_feedback and speed_feedback.get('school_zone_violations', 0) > 0:
                    summary += "Special attention needed in school zones! "
            elif weakest_area == 'cornering':
                summary += "Slow down before turns and steer smoothly through them. "

            summary += "Consider retrying with an instructor for personalized coaching, "
            summary += "or enroll in the Basics module to build fundamental skills."

        return summary
