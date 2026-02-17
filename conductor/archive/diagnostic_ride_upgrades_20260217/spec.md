# Specification: Diagnostic Ride Enhancements

## 1. Problem Statement
Students currently receive minimal feedback during and after their diagnostic rides. They only see a pass/fail result and a high-level summary of strengths and weaknesses. There is no real-time guidance to correct mistakes as they happen, nor an interactive way to review their performance (e.g., where specifically they braked too hard).

## 2. Goals
- **Real-time Feedback:** Provide instant alerts to the student when a mistake is detected (e.g., speeding, harsh braking).
- **Interactive Reports:** Allow students to review their ride on a map, seeing exactly where mistakes occurred.
- **Visual Analytics:** Provide charts showing performance metrics over the duration of the ride.
- **Historical Record:** Enable students to browse their history of diagnostic rides.

## 3. Functional Requirements

### 3.1 Real-time Mistake Detection
- The system shall expose an endpoint `/api/diagnostic-rides/live-evaluate`.
- This endpoint will accept a small window of sensor data (acceleration, speed, location).
- The system will return detected "events" (mistakes) with type and severity.
- The mobile app (or simulator) will display these alerts to the user.

### 3.2 Enhanced Post-Ride Evaluation
- The `DiagnosticEvaluator` shall be updated to record the timestamp and GPS coordinates of every detected mistake.
- These events will be stored in the `evaluation_result` JSON field in the `DiagnosticRide` model.

### 3.3 Diagnostic Ride Detail View
- A new template `diagnostic_ride_detail.html` shall be created.
- **Map Integration:** Show the ride route with markers for each detected mistake.
- **Performance Charts:** Using Chart.js, display graphs for:
    - Speed vs. Time (with speed limit overlay)
    - Acceleration (G-force) vs. Time
- **Score Breakdown:** Detailed scores for Braking, Speed, and Cornering.
- **Instructor Notes:** Display any notes if the ride was instructor-supervised.

### 3.4 Diagnostic Ride History
- A new template `diagnostic_ride_history.html` (or an enhancement to `student_progress.html`) to list all past diagnostic rides with summaries.

## 4. Technical Requirements
- **Frontend:** Leaflet.js for maps, Chart.js for visual analytics.
- **Backend:** FastAPI, SQLAlchemy, existing `DiagnosticEvaluator` logic.
- **Data Format:** Update `evaluation_result` JSON schema to include an `events` list:
  ```json
  {
    "events": [
      {"type": "harsh_braking", "timestamp": "...", "lat": "...", "lng": "...", "severity": "high"},
      ...
    ]
  }
  ```
