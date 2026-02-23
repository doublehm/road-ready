# 2026-02-23: Ride Performance Fix — Highway Lag & Memory Overload

## Problem
At highway speeds (~100 km/h) the active ride screen became extremely sluggish:
- Location updates lagged badly — the app showed the driver stopped at a red light while they were already far down the highway.
- The phone was hard to navigate; live feedback stopped arriving.
- At slow speeds (city driving) the app worked fine and felt responsive.

Root cause analysis revealed the app was triggering **25+ React state updates per second**:
- GPS `watchPositionAsync` with `distanceInterval: 5 m` fires 5–6×/sec on highways (every 5 m at 28 m/s). Each callback called 4–5 `setState` functions → 25–30 re-renders/sec from GPS alone.
- Accelerometer at 10 Hz called `setAcceleration` on every tick; gyroscope called `setRotation` at 10 Hz → 20 additional re-renders/sec.
- `dataRef.current` in `useDeviceMotion` grew without bound — never trimmed during tracking.
- The sync buffer in the active screen accumulated all telemetry in memory, flushed every 10 s via HTTP (frequent network interruptions on top of rendering load).

## Fix

### `mobile-app/src/hooks/useDeviceMotion.js`
- Added `STATE_THROTTLE_MS = 500`: `setAcceleration` and `setRotation` now update at most every 500 ms (2 Hz) instead of every sensor event (10 Hz). Sensor values are still sampled at full rate into refs.
- Added `DATA_WINDOW = 600`: `dataRef.current` is capped to the last 600 samples (~60 s at 10 Hz). Older data is already on the server via the live-stream; the cap prevents unbounded memory growth on long rides.

### `mobile-app/src/hooks/useGPSTracking.js`
- Added `GPS_STATE_THROTTLE_MS = 2000`: all GPS state setters (`setLocation`, `setSpeed`, `setRouteCoordinates`, `setDistance`, `setSpeedData`) fire at most once every 2 seconds. The callback still runs on every GPS fix for accurate distance accumulation in refs.
- `distanceInterval` increased from 5 m to 10 m, halving the GPS callback frequency on highways.
- Map trail capped at 200 points (was 500) for faster map renders.

### `mobile-app/src/screens/DiagnosticRideActiveScreen.js`
- HTTP sync interval increased from 10 s to 30 s to reduce network interruptions during driving.
- Added a 5-minute deep flush interval: forces any pending buffer to the server and clears it from memory. On a failed flush, the chunk is discarded (data is already in the WebSocket stream) rather than re-queued, preventing the buffer from growing unbounded on long/highway rides.

### `app/services/diagnostic_evaluator.py`
- Wrapped MongoDB `get_ride_telemetry` / `get_ride_events` calls in a try/except. When MongoDB is unavailable (e.g., not running locally), the evaluator now falls back gracefully to the SQL-stored sensor blobs instead of crashing with a 500 error.

## Result
- Re-renders reduced from ~25+/sec to ~2–3/sec at highway speeds.
- Memory stays bounded during long rides.
- Evaluate endpoint no longer 500s when MongoDB is not running.
- The 5-minute flush keeps the sync buffer small regardless of ride duration.
