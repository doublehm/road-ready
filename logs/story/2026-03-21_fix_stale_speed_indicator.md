# 2026-03-21: Fix Stale Speed Indicator at Standstill

## Problem
When the vehicle was stopped (e.g., at a red light), the speed indicator on the active ride screen continued showing the last recorded speed (7, 10, 13 km/h) instead of dropping to 0.

## Root Cause
Three compounding issues in `useGPSTracking.js`:

1. **No dead-zone filter:** GPS chipsets report phantom speeds of 3–15 km/h from signal drift while stationary. The hook passed these values straight to the UI.
2. **Render throttle blocked zero-speed updates:** A 2-second throttle (`GPS_STATE_THROTTLE_MS`) suppressed *all* state updates within the window, including the critical speed=0 update when the car stopped.
3. **High `distanceInterval` starved callbacks:** `distanceInterval: 10` (10 metres) meant the GPS subscription stopped firing entirely when the device was stationary, so speed was never re-evaluated.

## Changes Made
**File:** `mobile-app/src/hooks/useGPSTracking.js`

- **Dead-zone filter** (`SPEED_DEAD_ZONE_KMH = 3`): Any GPS speed below 3 km/h (~0.8 m/s) is treated as 0 km/h, eliminating drift noise.
- **Smart throttle bypass** (`SPEED_CHANGE_THRESHOLD_KMH = 5`): When speed changes by more than 5 km/h since the last UI update, the 2-second render throttle is bypassed so the display reacts immediately to hard braking or stops.
- **Staleness timer** (`STALE_SPEED_TIMEOUT_MS = 3000`): A 1-second interval checks whether the last GPS fix is older than 3 seconds; if so, speed is reset to 0. This covers edge cases where GPS callbacks stop firing at standstill (since `distanceInterval` remains at 10 m to avoid highway lag — see `logs/story/2026-02-23_performance_highway_ride.md`).
- **Negative speed guard:** `gpsSpeed > 0` check filters out the `-1` value some platforms return when speed is unavailable.
