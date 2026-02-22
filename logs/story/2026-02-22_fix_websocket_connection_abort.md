# Log: Fix WebSocket "Software caused connection abort"
**Date:** 2026-02-22
**Session:** Copilot CLI

---

## Context

Android was logging `WebSocket error: Software caused connection abort` immediately on ride start. The WebSocket connection for live telemetry streaming was never successfully established.

---

## Root Causes (Two Bugs)

### Bug 1: Missing `@router.websocket` decorator
**File:** `app/api/diagnostic_rides.py`

The `websocket_endpoint` function was defined as a plain `async def` with no route decorator. It was never registered with the FastAPI router, so the server simply had no WebSocket endpoint at all. Any connection attempt resulted in an HTTP 404, which Android's WebSocket client surfaces as "Software caused connection abort".

**Fix:** Added `@router.websocket("/live-ride-stream")` decorator above the function.

### Bug 2: Wrong WebSocket URL in mobile app
**File:** `mobile-app/src/screens/DiagnosticRideActiveScreen.js`

The mobile was constructing the URL as:
```
ws://10.32.100.57:8000/api/v1/live-ride-stream
```
But the correct path (after the `/diagnostic-rides` router prefix) is:
```
ws://10.32.100.57:8000/api/v1/diagnostic-rides/live-ride-stream
```

**Fix:** Updated the `wsUrl` construction to include `/diagnostic-rides/` prefix.

---

## Files Changed

| File | Change |
|---|---|
| `app/api/diagnostic_rides.py` | Added `@router.websocket("/live-ride-stream")` decorator |
| `mobile-app/src/screens/DiagnosticRideActiveScreen.js` | Fixed wsUrl to include `/diagnostic-rides/` path segment |

---

## Status
Committed and pushed to `alpha` branch (commit `99857abf`).
