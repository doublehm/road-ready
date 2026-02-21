# Implementation Plan: OpenStreetMap Smoothness & Performance Optimization

## Phase 1: Diagnostics & Base Improvements
Focus on fixing the foundational "blinking" and "gray area" issues.

- [ ] Task: Audit `app/static/maps.js` for inefficient re-renders or multiple `setView` calls.
- [ ] Task: Implement global `resizeObserver` or improved `invalidateSize` logic for all map containers.
- [ ] Task: Optimize Tile Layer settings (fadeAnimation, zoomAnimation, updateWhenIdle, updateWhenZooming).
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Diagnostics & Base Improvements' (Protocol in workflow.md)

## Phase 2: Smooth Marker Movement (Interpolation)
Introduce fluid animations for moving markers.

- [ ] Task: Research and select a Leaflet plugin or custom logic for marker interpolation (e.g., `Leaflet.MovingMarker` or custom `requestAnimationFrame`).
- [ ] Task: Implement `SmoothMarker` component in `app/static/maps.js` to handle position updates with transitions.
- [ ] Task: Integrate `SmoothMarker` into the Live Ride tracking logic (`app/templates/instructor_dashboard.html` and others).
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Smooth Marker Movement' (Protocol in workflow.md)

## Phase 3: Live Ride & History Optimization
Refine the auto-centering and tracking behavior for high-stress scenarios.

- [ ] Task: Implement "Smooth Pan" logic to follow markers without aggressive `panTo` calls.
- [ ] Task: Throttle coordinate updates if they arrive faster than the animation frames.
- [ ] Task: Verify smoothness across Mobile WebView (diagnostic ride view).
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Live Ride & History Optimization' (Protocol in workflow.md)

## Phase 4: Final Polishing & Performance Audit
Final checks and cleanup.

- [ ] Task: Remove any remaining legacy `setTimeout` hacks used for fixing map sizing.
- [ ] Task: Conduct a performance audit using browser dev tools (FPS and memory usage).
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Final Polishing' (Protocol in workflow.md)
