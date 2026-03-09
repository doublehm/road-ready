# 2026-03-09: Sensor Flag Recalibration

## Problem
The diagnostic ride evaluation system produced excessive false positive flags during normal driving:
1. **False cornering flags** — Road vibration and phone micro-movements on the X-axis registered as lateral g-force, triggering sharp turn events while driving straight.
2. **False harsh braking from potholes** — Vertical impacts (potholes, speed bumps) produced Z-axis spikes that the uncalibrated braking detector misinterpreted as deceleration.
3. **False speeding alerts** — GPS speed jitter of ±3 km/h combined with zero-tolerance scoring caused penalties even at legal speeds. OSM speed limit data occasionally returned incorrect limits from nearby side roads.

## Root Cause
- Cornering detection used raw, unfiltered X-axis acceleration with no cross-check against GPS heading.
- The uncalibrated braking fallback took `max(Y, Z)` deceleration, meaning pothole Z-axis spikes were counted as braking.
- Speeding penalties started at any amount over the posted limit, with no buffer for GPS or speedometer variance.

## Changes Made

### Backend — `app/services/diagnostic_evaluator.py`
- **5-sample sliding median filter** applied to all acceleration data before evaluation, removing single-sample noise spikes.
- **GPS heading cross-check** in cornering detection: sharp turns are only flagged when heading changes ≥5° within a ±2s window.
- **Cornering threshold floor raised** from 0.15g to 0.25g; speed sensitivity reduced from 0.002 to 0.0015 g/km/h.
- **Vertical impact exclusion** in braking: samples where Z-axis deviates from gravity by >0.4g are skipped (pothole/bump, not braking).
- **Uncalibrated fallback** now uses Y-axis only for braking (Z-axis is unreliable without calibration).
- **Speed tolerance** of 5 km/h (2 km/h in school zones) applied before penalizing.
- **Backend speed limit smoothing**: dramatic limit changes (>20 km/h) require 3 consecutive readings before acceptance.

### Backend — `app/api/diagnostic_rides.py`
- Live-evaluate endpoint now applies the median filter to incoming acceleration data.

### Mobile — `mobile-app/src/hooks/useDeviceMotion.js`
- Added 3-sample moving average on accelerometer readings to pre-filter noise before data reaches the backend.

### Tests
- Added `test_straight_line_driving_no_cornering_flag` — verifies no sharp turn events on straight road with vibration noise.
- Added `test_pothole_does_not_flag_harsh_braking` — verifies Z-axis pothole spike doesn't trigger braking event.
- Added `test_gps_jitter_does_not_flag_speeding` — verifies ±3 km/h GPS jitter produces no speeding events.
- Updated existing tests to use Y-axis for braking data (matching new behavior).
- Updated friction circle integration test to use sustained data (matching median filter behavior).

### Documentation
- Updated `DIAGNOSTIC_RIDE_IMPLEMENTATION.md` with new thresholds and filter descriptions.
