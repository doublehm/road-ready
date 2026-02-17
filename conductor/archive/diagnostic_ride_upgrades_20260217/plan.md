# Implementation Plan: Diagnostic Ride Enhancements

## Phase 1: Backend Foundation & Evaluation Logic
- [x] **Task 1: Update `DiagnosticEvaluator`**
    - Modify detection methods to return lists of events with metadata (timestamp, coords).
    - Ensure `evaluate` method populates the new `events` structure in `evaluation_result`.
- [x] **Task 2: Implement Live Evaluation Endpoint**
    - Add `POST /api/diagnostic-rides/live-evaluate` to `app/api/diagnostic_rides.py`.
    - Logic should process the last few seconds of data and return immediate feedback.

## Phase 2: Frontend Detail View
- [x] **Task 3: Create Diagnostic Ride Detail Page**
    - Add route `/diagnostic-ride/{id}` in `app/api/diagnostic_rides.py` (or a web router if preferred).
    - Create `app/templates/diagnostic_ride_detail.html`.
    - Integrate Leaflet.js for route and event markers.
    - Integrate Chart.js for speed and acceleration graphs.
- [x] **Task 4: Create Diagnostic Ride History Page**
    - Create `app/templates/diagnostic_ride_history.html`. (Implemented via enhancement to `student_progress.html` and a new detail page)
    - Update student dashboard/progress to link to history.

## Phase 3: Real-time UI & Simulation
- [x] **Task 5: Update Mobile Simulator**
    - Enhance the mobile app/simulator to call the live evaluation endpoint.
    - Implement a UI notification system for alerts.
- [x] **Task 6: Verification & Testing**
    - Add tests for the new evaluation logic.
    - Manual walkthrough of the ride and report.
