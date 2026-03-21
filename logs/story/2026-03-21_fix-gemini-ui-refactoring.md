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

## Additional Fix: Settings/Profile Screen Crash

### Problem
Tapping the Settings (gear) icon on StudentHomeScreen crashed the app because `StudentEditProfileScreen.js` (and `EditProfileScreen.js`) were missing critical imports: `SafeAreaView`, `Ionicons`, `ImagePicker`, and `DateTimePicker`.

### Root Cause
These files were written referencing components that were never imported at the top. Pre-existing bug, not caused by Gemini.

### Fix
- Added missing imports to both `StudentEditProfileScreen.js` and `EditProfileScreen.js`
- Updated `StudentEditProfileScreen` styles to match the new navy/green theme

---

## Fix 3: Learning Tab Crash (EducationHomeScreen, ModulesScreen)

### Problem
Navigating to the Learn tab crashed — `Ionicons` used but not imported. Same Gemini pattern.

### Fix
- Added `import Ionicons from '@expo/vector-icons/Ionicons'` to both files.

---

## Fix 4: Evaluator Sensitivity & New Detection Features

### Problem
Automatic driving flags were not sensitive enough — cornering and stop detection thresholds had been raised too high. User also requested new detection capabilities for erratic/inconsistent driving patterns.

### Threshold Tuning (More Sensitive)
| Constant | Old | New | Effect |
|---|---|---|---|
| `HARSH_BRAKING_THRESHOLD` | 0.6g | 0.5g | Catches firm braking earlier |
| `SUDDEN_STOP_SPEED_DROP` | 10 km/h | 8 km/h | Catches less dramatic stops |
| `CORNERING_THRESHOLD_FLOOR` | 0.25g | 0.18g | Detects gentler sharp turns at high speed |
| `CORNERING_SPEED_SENSITIVITY` | 0.0015 | 0.002 | Threshold drops faster with speed |
| `CORNERING_HEADING_CHANGE_MIN` | 5.0° | 3.0° | Smaller heading change confirms real turn |
| `CORNERING_HEADING_WINDOW_MS` | 2000ms | 3000ms | Wider window finds heading confirmation |

### New Detection: Erratic Driving (`_evaluate_erratic_driving`)
- **Harsh Acceleration (F5)**: Flags aggressive forward acceleration > 0.4g. Uses same orientation matrix and incline compensation as braking detection. Penalty: -8 points per event.
- **Speed Oscillation (F6)**: Detects repeated acceleration/deceleration cycles (speed hunting). Analyzes sliding windows of 10 data points for ≥4 direction changes with ≥5 km/h amplitude. Penalty: -5 per instance.

### New Detection: Lane Discipline (`_evaluate_lane_discipline`)
- **Lane Weaving (F7)**: Detects sudden heading changes > 5° at speeds above 40 km/h. Uses existing `WEAVING_THRESHOLD` constant (previously defined but unused). Penalty: -5 per event.

### Scoring Integration
- Erratic driving penalties reduce the **smoothness score**
- Lane discipline penalties reduce the **cornering score**
- 6 new tests added; all 46 backend tests pass

### Files Changed
- `app/services/diagnostic_evaluator.py` — thresholds + 2 new methods + integration
- `mobile-app/src/data/faults.js` — new F5/F6/F7 fault codes
- `tests/test_diagnostic_evaluator_v2.py` — 6 new tests
- `tests/test_advanced_physics.py` — updated hill compensation test for new threshold
