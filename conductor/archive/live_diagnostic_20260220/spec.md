# Specification: Live Diagnostic Ride Enhancements

## Overview
This track introduces real-time capabilities to the Diagnostic Ride feature. Currently, ride data (braking, cornering, acceleration) is processed and viewed after the ride concludes. This enhancement will allow instructors to monitor a student's performance live via a web dashboard while the ride is in progress. The mobile app will detect driving events on-device and stream them, along with raw telemetry, to the backend via WebSockets for immediate visualization.

## Functional Requirements
- **Mobile (On-Device Detection):**
    - Implement real-time analysis of accelerometer and gyroscope data to detect "Harsh Braking" and "Sharp Cornering" events.
    - Provide immediate visual/audio feedback to the student within the mobile app upon event detection.
    - Stream detected events and periodic telemetry (acceleration, location) to the backend using WebSockets.
- **Backend (Real-time Hub):**
    - Implement a WebSocket server (FastAPI/WebSockets) to handle live connections from mobile devices and web clients.
    - Broadcast incoming ride data from a specific mobile device to all authorized web clients (instructors) watching that ride.
    - Persist live data to the database to ensure no data is lost if the connection drops.
- **Web Dashboard (Live View):**
    - Enhance the Diagnostic Ride detail page with a "Live Mode" that activates when a ride is in progress.
    - **Live Event Feed:** A scrolling list of detected driving events with timestamps.
    - **Live Gauges/Charts:** Real-time visualization of G-forces and acceleration.
    - **Live Map:** Real-time tracking of the vehicle's position with markers appearing instantly for detected events.

## Non-Functional Requirements
- **Latency:** Event detection to web dashboard visualization should be < 1 second.
- **Robustness:** Gracefully handle WebSocket disconnections and reconnections (auto-reconnect with data buffering on mobile).
- **Battery Impact:** Optimize mobile sensor polling and network usage to minimize battery drain during long rides.

## Acceptance Criteria
- [ ] Instructor can open a "Live View" for an active diagnostic ride.
- [ ] Harsh braking detected on mobile appears on the web dashboard within 1 second.
- [ ] Live map correctly follows the mobile device's GPS position.
- [ ] Student receives a notification/alert on the phone when they brake too hard.
- [ ] All live-streamed data is saved and available for the final ride report.

## Out of Scope
- Historical playback of the live stream (only the final aggregated report is stored for history).
- Multi-camera live video streaming.
- Real-time voice communication over WebSockets.
