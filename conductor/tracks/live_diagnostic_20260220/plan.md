# Implementation Plan: Live Diagnostic Ride Enhancements

## Phase 1: Backend WebSocket Foundation [checkpoint: f5984ee]
- [x] Task: Implement WebSocket endpoint in FastAPI for live ride streaming. e1694e1
- [x] Task: Create a Connection Manager to handle broadcasting data from mobile to web clients based on `ride_id`. 83f4c2e
- [x] Task: Define JSON schemas for WebSocket messages (telemetry, event, system). 83f4c2e
- [x] Task: Write tests for WebSocket connection and message broadcasting. 83f4c2e
- [x] Task: Conductor - User Manual Verification 'Phase 1: Backend WebSocket Foundation' (Protocol in workflow.md) 83f4c2e

## Phase 2: Mobile Live Streaming & Feedback [checkpoint: d676d1a] [checkpoint: ca2db42]
- [x] Task: Integrate WebSocket client into the mobile app using `socket.io-client` or native WebSockets. ca2db42
- [x] Task: Update sensor logic to detect events (braking/cornering) in real-time on-device. ca2db42
- [x] Task: Add live visual/audio feedback components for students in the mobile UI. ca2db42
- [x] Task: Implement data buffering to handle temporary network disconnections. ca2db42
- [x] Task: Write unit tests for on-device event detection logic. ca2db42
- [x] Task: Conductor - User Manual Verification 'Phase 2: Mobile Live Streaming & Feedback' (Protocol in workflow.md) ca2db42

## Phase 3: Web Dashboard Live Visualization
- [ ] Task: Update Diagnostic Ride detail page to support WebSocket connections.
- [ ] Task: Implement Live Event Feed component.
- [ ] Task: Integrate real-time charts (e.g., Chart.js or Recharts) for acceleration data.
- [ ] Task: Update the Map component to handle live position updates and dynamic markers.
- [ ] Task: Add a "Live" status indicator and transition logic between live/completed states.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Web Dashboard Live Visualization' (Protocol in workflow.md)

## Phase 4: Integration & Optimization
- [ ] Task: Perform end-to-end testing between mobile app and web dashboard.
- [ ] Task: Optimize WebSocket message frequency to balance latency and battery/data usage.
- [ ] Task: Final code review and documentation updates.
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Integration & Optimization' (Protocol in workflow.md)
