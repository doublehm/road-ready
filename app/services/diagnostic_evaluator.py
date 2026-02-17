import json
from typing import Dict, List, Tuple
import math

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
    HARSH_BRAKING_THRESHOLD = 0.4  # 0.4g deceleration
    SUDDEN_STOP_SPEED_DROP = 5  # km/h drop in 1 second
    SMOOTH_BRAKING_MIN = 0.1  # 0.1g minimum for smooth braking
    SMOOTH_BRAKING_MAX = 0.3  # 0.3g maximum for smooth braking

    # Cornering thresholds
    SHARP_TURN_LATERAL_THRESHOLD = 0.3  # 0.3g lateral acceleration
    SMOOTH_TURN_THRESHOLD = 0.15  # Below this is smooth

    # Speed limits (assumed based on patterns)
    RESIDENTIAL_LIMIT = 50  # km/h
    ARTERIAL_LIMIT = 60  # km/h
    HIGHWAY_LIMIT = 80  # km/h

    def __init__(self):
        pass

    def evaluate(self, ride_data: Dict) -> Dict:
        """
        Main evaluation method that processes all sensor data and generates scores.

        Args:
            ride_data: Dictionary containing:
                - acceleration_data: JSON string of acceleration readings
                - rotation_data: JSON string of gyroscope readings
                - speed_data: JSON string of GPS speed readings
                - duration_minutes: Float
                - distance_km: Float

        Returns:
            Dictionary with scores, pass/fail status, and detailed feedback
        """
        # Parse sensor data
        acceleration_data = json.loads(ride_data.get('acceleration_data', '[]'))
        rotation_data = json.loads(ride_data.get('rotation_data', '[]'))
        speed_data = json.loads(ride_data.get('speed_data', '[]'))

        duration_minutes = ride_data.get('duration_minutes', 0)
        distance_km = ride_data.get('distance_km', 0)

        # Calculate individual scores
        braking_score, braking_feedback = self._evaluate_braking(acceleration_data, speed_data)
        speed_score, speed_feedback = self._evaluate_speed(speed_data, duration_minutes)
        cornering_score, cornering_feedback = self._evaluate_cornering(acceleration_data, rotation_data)

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
        # Sort events by timestamp
        all_events.sort(key=lambda x: x.get('timestamp') or 0)

        # Generate comprehensive feedback
        evaluation_result = {
            'braking': braking_feedback,
            'speed': speed_feedback,
            'cornering': cornering_feedback,
            'overall': pass_feedback,
            'events': all_events,
            'summary': self._generate_summary(passed, overall_score, braking_score, speed_score, cornering_score)
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
        """
        Evaluate braking performance based on acceleration and speed data.

        Returns:
            Tuple of (score, feedback_dict)
        """
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
            'events': []
        }

        if not acceleration_data:
            return 50.0, {'notes': ['Insufficient acceleration data for evaluation'], 'events': []}

        # Analyze acceleration data for harsh braking
        for i in range(len(acceleration_data)):
            point = acceleration_data[i]
            z_accel = point.get('z', 0)

            # Negative Z acceleration indicates braking (forward deceleration)
            deceleration_g = abs(z_accel) / self.GRAVITY if z_accel < 0 else 0

            if deceleration_g > self.HARSH_BRAKING_THRESHOLD:
                harsh_braking_count += 1
                score -= 10
                events.append({
                    'type': 'harsh_braking',
                    'timestamp': point.get('timestamp'),
                    'lat': point.get('latitude'),
                    'lng': point.get('longitude'),
                    'value': round(deceleration_g, 2),
                    'severity': 'high' if deceleration_g > 0.6 else 'medium'
                })
            elif self.SMOOTH_BRAKING_MIN <= deceleration_g <= self.SMOOTH_BRAKING_MAX:
                smooth_braking_count += 1

        # Analyze speed data for sudden stops
        for i in range(1, len(speed_data)):
            point = speed_data[i]
            prev_point = speed_data[i-1]
            prev_speed = prev_point.get('speed', 0)
            curr_speed = point.get('speed', 0)
            time_diff = point.get('timestamp', 0) - prev_point.get('timestamp', 0)

            if time_diff > 0 and time_diff <= 1.5:  # Within 1.5 seconds
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
                        'severity': 'high'
                    })

        # Bonus for smooth braking
        smooth_bonus = min(20, smooth_braking_count * 2)
        score += smooth_bonus

        # Clamp score
        score = max(0, min(100, score))

        feedback['harsh_braking_events'] = harsh_braking_count
        feedback['sudden_stops'] = sudden_stop_count
        feedback['smooth_braking_events'] = smooth_braking_count
        feedback['events'] = events

        if harsh_braking_count > 5:
            feedback['notes'].append(f'Too many harsh braking events ({harsh_braking_count}). Practice gradual deceleration.')
        elif harsh_braking_count > 0:
            feedback['notes'].append(f'Some harsh braking detected ({harsh_braking_count} events). Work on smoother stops.')
        else:
            feedback['notes'].append('Excellent braking control!')

        if smooth_braking_count > 10:
            feedback['notes'].append('Great smooth braking technique.')

        return score, feedback

    def _evaluate_speed(self, speed_data: List[Dict], duration_minutes: float) -> Tuple[float, Dict]:
        """
        Evaluate speed compliance and consistency.

        Returns:
            Tuple of (score, feedback_dict)
        """
        score = 100.0
        speeding_time = 0
        under_speed_time = 0
        total_time = 0
        speed_changes = []

        feedback = {
            'speeding_percentage': 0,
            'under_speed_percentage': 0,
            'speed_variance': 0,
            'notes': [],
            'events': []
        }

        if not speed_data or len(speed_data) < 2:
            return 50.0, {'notes': ['Insufficient speed data for evaluation'], 'events': []}
        
        events = []

        # Detect road type based on average speed
        speeds = [point.get('speed', 0) for point in speed_data]
        avg_speed = sum(speeds) / len(speeds) if speeds else 0

        # Determine likely speed limit
        if avg_speed < 40:
            speed_limit = self.RESIDENTIAL_LIMIT
            road_type = "residential"
        elif avg_speed < 65:
            speed_limit = self.ARTERIAL_LIMIT
            road_type = "arterial"
        else:
            speed_limit = self.HIGHWAY_LIMIT
            road_type = "highway"

        # Analyze speed compliance
        for i in range(len(speed_data)):
            point = speed_data[i]
            speed = point.get('speed', 0)

            # Calculate time duration for this point (assume 1 second intervals)
            time_duration = 1
            total_time += time_duration

            # Check for speeding
            if speed > speed_limit:
                speeding_time += time_duration
                # Only record event if speeding significantly or at intervals
                if speed > (speed_limit + 5):
                    events.append({
                        'type': 'speeding',
                        'timestamp': point.get('timestamp'),
                        'lat': point.get('latitude'),
                        'lng': point.get('longitude'),
                        'value': round(speed, 2),
                        'limit': speed_limit,
                        'severity': 'high' if speed > (speed_limit + 15) else 'medium'
                    })

            # Check for under-speed (excluding stops)
            if speed > 5 and speed < (speed_limit - 10):
                under_speed_time += time_duration

            # Track speed changes for variance calculation
            if i > 0:
                prev_speed = speed_data[i-1].get('speed', 0)
                speed_changes.append(abs(speed - prev_speed))

        # Calculate percentages
        speeding_pct = (speeding_time / total_time * 100) if total_time > 0 else 0
        under_speed_pct = (under_speed_time / total_time * 100) if total_time > 0 else 0

        # Penalties
        score -= speeding_pct * 2  # -2 points per 1% time speeding
        score -= under_speed_pct * 1  # -1 point per 1% time under speed

        # Calculate speed variance
        if speed_changes:
            avg_change = sum(speed_changes) / len(speed_changes)
            variance_penalty = min(20, avg_change * 2)
            score -= variance_penalty
            feedback['speed_variance'] = round(avg_change, 2)

        # Clamp score
        score = max(0, min(100, score))

        feedback['speeding_percentage'] = round(speeding_pct, 2)
        feedback['under_speed_percentage'] = round(under_speed_pct, 2)
        feedback['detected_road_type'] = road_type
        feedback['assumed_limit'] = speed_limit
        feedback['events'] = events

        if speeding_pct > 10:
            feedback['notes'].append(f'Significant speeding detected ({speeding_pct:.1f}% of time). Always observe posted limits.')
        elif speeding_pct > 5:
            feedback['notes'].append(f'Some speeding detected ({speeding_pct:.1f}% of time). Be more mindful of speed limits.')
        else:
            feedback['notes'].append('Good speed limit compliance.')

        if under_speed_pct > 10:
            feedback['notes'].append('Maintaining appropriate speed is important for traffic flow.')

        if avg_change > 5:
            feedback['notes'].append('Work on maintaining more consistent speed.')

        return score, feedback

    def _evaluate_cornering(self, acceleration_data: List[Dict], rotation_data: List[Dict]) -> Tuple[float, Dict]:
        """
        Evaluate cornering quality based on lateral acceleration and rotation.

        Returns:
            Tuple of (score, feedback_dict)
        """
        score = 100.0
        sharp_turn_count = 0
        smooth_turn_count = 0
        jerky_steering_count = 0
        events = []

        feedback = {
            'sharp_turns': 0,
            'smooth_turns': 0,
            'jerky_steering': 0,
            'notes': [],
            'events': []
        }

        if not acceleration_data:
            return 50.0, {'notes': ['Insufficient acceleration data for evaluation'], 'events': []}

        # Analyze lateral acceleration (X-axis)
        for i in range(len(acceleration_data)):
            point = acceleration_data[i]
            x_accel = point.get('x', 0)

            lateral_g = abs(x_accel) / self.GRAVITY

            if lateral_g > self.SHARP_TURN_LATERAL_THRESHOLD:
                sharp_turn_count += 1
                score -= 8
                events.append({
                    'type': 'sharp_turn',
                    'timestamp': point.get('timestamp'),
                    'lat': point.get('latitude'),
                    'lng': point.get('longitude'),
                    'value': round(lateral_g, 2),
                    'severity': 'high' if lateral_g > 0.5 else 'medium'
                })
            elif lateral_g < self.SMOOTH_TURN_THRESHOLD and lateral_g > 0.02:
                smooth_turn_count += 1

        # Analyze rotation data for jerky steering
        if rotation_data:
            for i in range(1, len(rotation_data)):
                prev_rotation = rotation_data[i-1].get('z', 0)  # Z-axis for yaw (turning)
                curr_rotation = rotation_data[i].get('z', 0)

                rotation_change = abs(curr_rotation - prev_rotation)

                # Sudden rotation changes indicate jerky steering
                if rotation_change > 0.5:  # threshold in rad/s change
                    jerky_steering_count += 1
                    score -= 5

        # Bonus for smooth turns
        smooth_bonus = min(20, smooth_turn_count * 2)
        score += smooth_bonus

        # Clamp score
        score = max(0, min(100, score))

        feedback['sharp_turns'] = sharp_turn_count
        feedback['smooth_turns'] = smooth_turn_count
        feedback['jerky_steering'] = jerky_steering_count
        feedback['events'] = events

        if sharp_turn_count > 5:
            feedback['notes'].append(f'Too many sharp turns ({sharp_turn_count}). Slow down before corners.')
        elif sharp_turn_count > 0:
            feedback['notes'].append(f'Some sharp turns detected ({sharp_turn_count}). Practice gradual steering.')
        else:
            feedback['notes'].append('Excellent cornering technique!')

        if jerky_steering_count > 10:
            feedback['notes'].append('Steering inputs are too abrupt. Make smoother, more gradual adjustments.')

        if smooth_turn_count > 10:
            feedback['notes'].append('Great smooth turning technique.')

        return score, feedback

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
        """
        Determine if the ride passes all criteria.

        Returns:
            Tuple of (passed, feedback_dict)
        """
        feedback = {'criteria_met': [], 'criteria_failed': []}

        passed = True

        # Check overall score
        if overall_score >= self.OVERALL_PASS_THRESHOLD:
            feedback['criteria_met'].append(f'Overall score: {overall_score:.1f}/100 ✓')
        else:
            feedback['criteria_failed'].append(f'Overall score too low: {overall_score:.1f}/100 (need {self.OVERALL_PASS_THRESHOLD})')
            passed = False

        # Check individual scores
        if braking_score >= self.CATEGORY_PASS_THRESHOLD:
            feedback['criteria_met'].append(f'Braking score: {braking_score:.1f}/100 ✓')
        else:
            feedback['criteria_failed'].append(f'Braking score too low: {braking_score:.1f}/100 (need {self.CATEGORY_PASS_THRESHOLD})')
            passed = False

        if speed_score >= self.CATEGORY_PASS_THRESHOLD:
            feedback['criteria_met'].append(f'Speed score: {speed_score:.1f}/100 ✓')
        else:
            feedback['criteria_failed'].append(f'Speed score too low: {speed_score:.1f}/100 (need {self.CATEGORY_PASS_THRESHOLD})')
            passed = False

        if cornering_score >= self.CATEGORY_PASS_THRESHOLD:
            feedback['criteria_met'].append(f'Cornering score: {cornering_score:.1f}/100 ✓')
        else:
            feedback['criteria_failed'].append(f'Cornering score too low: {cornering_score:.1f}/100 (need {self.CATEGORY_PASS_THRESHOLD})')
            passed = False

        # Check duration
        if duration_minutes >= self.MIN_DURATION_MINUTES:
            feedback['criteria_met'].append(f'Duration: {duration_minutes:.1f} min ✓')
        else:
            feedback['criteria_failed'].append(f'Duration too short: {duration_minutes:.1f} min (need {self.MIN_DURATION_MINUTES})')
            passed = False

        # Check distance
        if distance_km >= self.MIN_DISTANCE_KM:
            feedback['criteria_met'].append(f'Distance: {distance_km:.1f} km ✓')
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
        cornering_score: float
    ) -> str:
        """Generate a human-readable summary of the evaluation."""
        if passed:
            summary = f"Congratulations! You passed the diagnostic ride with an overall score of {overall_score:.1f}/100. "
            summary += "You've demonstrated sufficient driving skills to skip the Basics module and proceed directly to Advanced training. "
            summary += "You've potentially saved $800+ in training costs!"
        else:
            summary = f"You did not pass the diagnostic ride (overall score: {overall_score:.1f}/100). "

            # Identify weakest area
            scores = [
                ('braking', braking_score),
                ('speed control', speed_score),
                ('cornering', cornering_score)
            ]
            scores.sort(key=lambda x: x[1])
            weakest_area = scores[0][0]

            summary += f"Your weakest area is {weakest_area}. "
            summary += "Consider retrying the diagnostic ride with an instructor for personalized feedback, "
            summary += "or enroll in the Basics module to build fundamental skills."

        return summary
