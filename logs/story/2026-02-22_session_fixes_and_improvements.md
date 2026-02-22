# Log: Session Fixes & Improvements — Feb 22, 2026
**Date:** 2026-02-22  
**Sessions:** Multiple Copilot CLI sessions (IDs: 60eff873, df48c627)

---

## Overview

A focused set of bug fixes and stability improvements across the mobile app and backend, addressing real-world problems discovered during diagnostic ride testing. All issues were reproduced, root-caused, and resolved.

---

## Fixes Implemented

### 1. Fix Live Feedback Loop Never Firing
**File:** `mobile-app/src/screens/DiagnosticRideActiveScreen.js`

**Root Cause:** The `feedbackInterval` effect listed `deviceMotion.data` and `gpsTracking.speedData` in its dependency array. These values update every ~1 second, so the 2-second interval was cleared and restarted before it ever fired — no events were ever evaluated.

**Fix:** Moved sensor values to refs. Removed them from the effect dependency array. The interval now fires correctly every 2 seconds.

---

### 2. Fix Telemetry Streaming Intervals Breaking
**File:** `mobile-app/src/screens/DiagnosticRideActiveScreen.js`

**Root Cause:** The telemetry streaming effect depended on `deviceMotion.acceleration`, `rotation`, `speed`, and `location` — values that update every 100ms–1s. This restarted both the 1-second and 10-second intervals constantly, effectively preventing any telemetry from being sent.

**Fix:** Moved sensor reads to refs. Dependency array now only contains `rideId` and `sendMessage`.

---

### 3. Fix `triggerAlert` Stale Closure
**File:** `mobile-app/src/screens/DiagnosticRideActiveScreen.js`

**Root Cause:** `triggerAlert` was captured inside an old interval closure and read stale `speedLimit` / `speed` values from when the interval was first created.

**Fix:** Routed `triggerAlert` calls through a ref (`triggerAlertRef`) that is updated on each render.

---

### 4. Replace react-native-maps with Leaflet WebView
**Files:** `mobile-app/src/components/OSMMap.js`, multiple screens

**Root Cause:** `react-native-maps` on Android requires the Google Maps SDK and produces a blank view without a paid API key. This affected all map screens.

**Fix:** Replaced with a `WebView` + Leaflet.js solution using the already-installed `react-native-webview` package. The new `OSMMap` component supports:
- Live tracking with polyline updates
- Current position marker
- Route replay with color-coded segments (green/yellow/red by score)
- Event markers with popups

---

### 5. Fix Live Events Gated Behind `speedData`
**File:** `mobile-app/src/screens/DiagnosticRideActiveScreen.js`

**Root Cause:** The feedback loop condition `speedData.length < 1` blocked ALL event detection — including harsh braking and cornering — which do not require speed data. GPS `speedData` only updates every ~5 seconds outdoors, so events were completely suppressed.

**Fix:** Removed the `speedData` length gate. Braking/cornering events are now evaluated unconditionally. Speed-dependent events (speeding) still check for GPS data before proceeding.

---

### 6. Fix EventTimeline / SpeedGraph Empty in Results
**File:** `app/api/diagnostic_rides.py`, `app/services/diagnostic_evaluator.py`

**Root Cause:** After a ride, `ride.speed_data` was never populated — telemetry is stored in MongoDB, not the SQL model. `SpeedGraph` and `EventTimeline` on the results screen had no data to render.

**Fix:** After evaluation, the processed `speed_data` array is written back to the `DiagnosticRide` SQL record so the results screen can display the speed graph and event timeline without requiring a MongoDB query.

---

### 7. Fix False Speeding Alerts on Highways (Three-Layer Fix)
**Files:** `app/services/speed_limit_service.py`, `mobile-app/src/hooks/useSpeedLimit.js`, `app/services/diagnostic_evaluator.py`

**Root Cause:** OSM `Overpass API` sometimes returns multiple road segments at a GPS point, including lower-class roads (e.g., a service road next to a motorway), causing the speed limit to jump between 30 mph and 70 mph.

**Fixes:**
1. **Backend:** When multiple road types are returned, prefer the highest-class road (`motorway > trunk > primary > secondary …`). Ignore `_link` suffix roads unless they're the only result.
2. **Frontend:** Raised `DRAMATIC_CHANGE_THRESHOLD`. Require 3 agreeing consecutive readings before updating the displayed speed limit.
3. **Evaluator:** Require 3+ consecutive GPS points over the limit before generating a speeding event.

---

### 8. Fix 500 Error: Missing `OVERPASS_URL` Constant
**File:** `app/services/speed_limit_service.py`

**Root Cause:** The `OVERPASS_URL` constant was referenced but never defined in the module after a refactor.

**Fix:** Added the missing constant definition at the top of `speed_limit_service.py`.

---

### 9. Fix Crash: Remove `expo-task-manager`
**File:** `mobile-app/package.json`, `mobile-app/app.json`

**Root Cause:** `expo-task-manager` is not compatible with Expo Go (managed workflow). Importing it caused an immediate crash on app launch.

**Fix:** Removed `expo-task-manager` from dependencies and replaced background location tracking with a standard foreground location watcher.

---

### 10. Fix WebSocket Background Disconnect + Background GPS Tracking
**Files:** `mobile-app/src/screens/DiagnosticRideActiveScreen.js`, `mobile-app/src/hooks/useGPSTracking.js`

**Root Cause:** WebSocket connection was dropped when the app moved to the background (screen off / home button). GPS tracking also paused.

**Fix:** Added an `AppState` listener to detect background/foreground transitions. WebSocket reconnects on foreground resume. GPS now uses `watchPositionAsync` with `foreground` accuracy mode, which continues updating while the screen is on.

---

### 11. Fix DeviceMotion Crash on Android
**Files:** `mobile-app/src/hooks/useDeviceMotion.js`

**Root Cause:** `DeviceMotion` from `expo-sensors` is not available on all Android devices and throws a crash when unavailable.

**Fix:** Replaced `DeviceMotion` with direct `Accelerometer` + `Gyroscope` subscriptions, which are universally available on Android. Data is combined into the same format consumed by the evaluator.

---

### 12. Fix: Remove `current_user` Dep from Speed Limit Endpoint
**File:** `app/api/diagnostic_rides.py`

**Root Cause:** The `get_speed_limit_for_location` endpoint had a `current_user: models.User = Depends(deps.get_current_user)` parameter that was unused and added an unnecessary authentication requirement to what is effectively a utility/lookup endpoint.

**Fix:** Removed the unused `current_user` dependency.

---

### 13. Merge main → alpha: Leaflet `app.json` (Drop react-native-maps plugin)
**Branch:** alpha

Merged changes from `main` to `alpha` to drop the `react-native-maps` Expo plugin from `app.json` now that the project uses Leaflet WebView for all maps.

---

## Files Changed (Summary)

| File | Changes |
|------|---------|
| `mobile-app/src/screens/DiagnosticRideActiveScreen.js` | Refs fix, interval fix, stale closure fix, live events gate fix |
| `mobile-app/src/components/OSMMap.js` | New Leaflet WebView map component |
| `mobile-app/src/hooks/useDeviceMotion.js` | Replaced DeviceMotion with Accelerometer+Gyroscope |
| `mobile-app/src/hooks/useGPSTracking.js` | Background GPS tracking via watchPositionAsync |
| `mobile-app/src/hooks/useSpeedLimit.js` | Raised change threshold, require 3 agreeing readings |
| `mobile-app/package.json` | Removed expo-task-manager |
| `mobile-app/app.json` | Removed react-native-maps plugin |
| `app/services/speed_limit_service.py` | Road class preference, added OVERPASS_URL constant |
| `app/services/diagnostic_evaluator.py` | Require 3 consecutive speeding points; write speed_data to SQL |
| `app/api/diagnostic_rides.py` | Write speed_data after eval; remove unused current_user dep |

---

## Status
All fixes committed and pushed to `alpha` branch. No regressions observed in the test suite.
