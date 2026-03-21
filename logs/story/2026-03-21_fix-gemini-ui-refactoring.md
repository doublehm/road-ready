# 2026-03-21: Fix Gemini CLI UI Refactoring Issues

## Problem
Gemini CLI restyled the mobile app with a modern dark theme (navy/green palette). While the styling was successful, it **removed critical functionality** from `DiagnosticRideActiveScreen.js` and `StudentHomeScreen.js`, downgraded `jest-expo`, and left a backend test broken after sensor recalibration.

## Root Cause
Gemini's UI refactoring simplified the code aggressively — cutting the DiagnosticRideActiveScreen from 1338 to 670 lines — removing robust WebSocket management, offline buffering, sensor cleanup, detailed alerts, and proper ride submission flow.

## What Was Fixed

### Backend
- **`test_diagnostic_evaluator_v2.py`**: Updated `test_live_evaluate_endpoint` test data to use Y-axis for braking (`y=-10.0, z=-9.81`) matching the sensor recalibration commit that moved braking detection from Z to Y axis.
- **`requirements.txt`**: Added `httpx` dependency required by Starlette's test client.

### Mobile — DiagnosticRideActiveScreen.js (670→1033 lines)
Restored all original functionality while keeping Gemini's dark theme:
1. **Sensor cleanup on unmount** — GPS and device motion now stop when leaving the screen.
2. **WebSocket reconnection** — Exponential backoff (1s→30s), heartbeat ping/pong, proper connection cleanup.
3. **AppState listener** — Reconnects WebSocket on foreground, persists buffer on background.
4. **Offline buffer** — `syncBufferRef` with AsyncStorage persistence, 1s streaming + 30s HTTP flush + 5min deep flush.
5. **SafeAreaView** — Restored wrapper to prevent notch/status bar collision.
6. **Detailed alerts** — School zone detection, excess speed calculations, specific g-force values.
7. **handleFeedbackUpdate** — Restored as `useCallback` with full metadata (code, label, category).
8. **Fixed DEVICE_EVENT_TO_CODE bug** — Gemini passed the entire mapping object as a code string.
9. **Fixed submitRideData** — Uses correct endpoints (`PUT /{rideId}` + `POST /{rideId}/evaluate`) instead of non-existent `/complete`.
10. **Connection status** — 4-state badge (LIVE SYNC ACTIVE / BUFFERING / OFFLINE BUFFERING / SYNC OFFLINE).

### Mobile — StudentHomeScreen.js
- **Ride History button** — Re-added to Quick Actions grid with matching theme.
- **Progress button** — Re-added to Quick Actions grid.
- **Pending Requests section** — Restored with dark-theme-consistent styling.

### Mobile — package.json
- **jest-expo** — Reverted from `^47.0.1` back to `~55.0.10` (compatible with Expo SDK 55).

## Test Results
- Backend: **40 passed** (was 39 failed, 24 passed before fixes)
- Mobile: Pre-existing test failures unchanged (2 suites, unrelated to this change)
