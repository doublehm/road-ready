# 2026-03-25: Fix Live G-Force Gauges (Stop Force & Accel)

## Problem
In the `DiagnosticRideActiveScreen` live dashboard, the "Stop Force" and "Accel" gauges remained at 0 during the ride, even though the final report correctly identified and flagged harsh braking/acceleration events.

## Root Cause
1.  **Render-Loop Collision:** `DiagnosticRideActiveScreen.js` was updating `prevSpeedRef` and `prevAccelerationRef` on every render. Because the component re-renders much faster (~10Hz) than GPS or Accelerometer data updates, the "previous" value almost always equaled the "current" value, resulting in a zero delta.
2.  **GPS Dependency:** `TelemetryPanel.js` relied exclusively on GPS speed changes to categorize longitudinal force as either "Braking" or "Acceleration". GPS data is too slow and jittery for high-frequency UI updates, often showing 0 delta between 100ms UI frames.
3.  **Throttled State:** The `sampleIntervalMs` passed to the smoothness (jerk) calculation was hardcoded to 100ms, while the actual throttle in `useDeviceMotion` was 150ms, causing slightly inaccurate jerk readings.

## Solution
1.  **Stable State Management:** Refactored `DiagnosticRideActiveScreen.js` to update `prev` refs inside `useEffect` hooks that only fire when the underlying sensor data actually changes. This preserves a meaningful delta for the UI.
2.  **Accelerometer-First Categorization:** Updated `TelemetryPanel.js` to use the dominant longitudinal accelerometer axis (Y or Z) for real-time force magnitude.
3.  **Hybrid Logic:**
    *   If GPS shows a clear trend (±0.2 km/h), it is used to categorize the force.
    *   If GPS is static, the sign of the accelerometer axis is used as a heuristic.
    *   This ensures the gauges are responsive even when the car is moving slowly or GPS signal is weak.
4.  **Synced Timing:** Aligned `sampleIntervalMs` (150ms) between the hook and the panel for accurate jerk calculation.

## Verification
*   Backend evaluation logic remains unchanged (it already correctly used full telemetry history).
*   Live dashboard now shows real-time spikes in "Stop Force" and "Accel" matching the G-force overlay.
