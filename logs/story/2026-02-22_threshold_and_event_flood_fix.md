# Log: Fix Over-Sensitive Thresholds & Event Flooding (Freeze/Crash)
**Date:** 2026-02-22
**Session:** Copilot CLI

---

## Context

During real-world driving, the app was generating massive numbers of false-positive flags even during smooth, normal driving. This caused:
1. The UI to flood with alerts every 2 seconds
2. The `allEvents` array to grow unboundedly, causing React Native to freeze and crash

Root causes were traced to thresholds that were too low for real-world phone sensor data, and the evaluator counting every raw sensor sample as a separate event (one hard stop = 20–30 events at 10Hz).

---

## Changes Implemented

### Backend (`app/services/diagnostic_evaluator.py`)

**Raised thresholds to realistic real-world values:**

| Constant | Before | After | Reason |
|---|---|---|---|
| `HARSH_BRAKING_THRESHOLD` | 0.4g | **0.6g** | 0.4g fires on road bumps and vibration; genuine hard braking is ≥0.6g |
| `SHARP_TURN_LATERAL_THRESHOLD` | 0.3g | **0.45g** | Normal city corners are 0.2–0.3g; aggressive turns are ≥0.45g |
| `SUDDEN_STOP_SPEED_DROP` | 5 km/h | **10 km/h** | 5 km/h drop in a sample window fires in normal traffic deceleration |
| `JERKY_STEERING_RATE_THRESHOLD` | 0.5 rad/s | **1.0 rad/s** | Minor steering corrections and lane holds were triggering this constantly |
| `SMOOTH_BRAKING_MAX` | 0.3g | **0.4g** | Adjusted upper bound to match new threshold range |
| `SMOOTH_TURN_THRESHOLD` | 0.15g | **0.2g** | Adjusted lower bound to match new threshold range |

**Added `EVENT_COOLDOWN_MS = 3000`:**

At 10Hz, one braking action produces ~20–50 consecutive samples above threshold. Each sample was being counted as a separate event and deducting points separately. A single pothole could deduct −50 pts from braking score and generate 30 entries in the event log.

Fix: Added a per-type cooldown tracker (`last_harsh_braking_ts`, `last_sharp_turn_ts`, `last_sudden_stop_ts`). Consecutive samples within 3 seconds of the same type are merged into a single event.

**Severity boundary updated:**
- `harsh_braking`: `high` threshold raised from 0.6g → 0.8g (since base threshold is now 0.6g)
- `sharp_turn`: `high` threshold raised from 0.5g → 0.65g

### Frontend (`mobile-app/src/screens/DiagnosticRideActiveScreen.js`)

**Capped `allEvents` array at 300 entries:**

`allEvents` was appended to on every feedback loop cycle (every 2 seconds) with no upper bound. After a 20-minute drive at even 5 events/minute, this was hundreds of entries being re-rendered in a flat list on every state update — causing freeze and eventual crash.

Fix: `setAllEvents(prev => [...newEvents, ...prev].slice(0, MAX_EVENTS))` where `MAX_EVENTS = 300`.

**Added 15-second per-type alert cooldown:**

The alert banner was firing for every event returned by the live-evaluate endpoint — potentially every 2 seconds for the same event type (e.g., continuous speeding). A `lastAlertByTypeRef` ref now tracks the last time each type fired, suppressing repeats within 15 seconds.

### Tests (`tests/test_diagnostic_evaluator_v2.py`)

Updated test data to match new thresholds:
- Braking test now uses −6.5 m/s² ≈ 0.66g (above new 0.6g threshold)
- Speeding test now uses 7 consecutive data points (event fires at `i=5` where `i%5==0` and `consecutive_over=6≥3`)
- Aggregate test similarly updated with 7 speed points and explicit `speed_limit_data`

---

## Files Changed

| File | Changes |
|---|---|
| `app/services/diagnostic_evaluator.py` | Raised thresholds; added EVENT_COOLDOWN_MS; per-event cooldown logic in `_evaluate_braking` and `_evaluate_cornering` |
| `mobile-app/src/screens/DiagnosticRideActiveScreen.js` | Cap `allEvents` to 300; 15s per-type alert cooldown via `lastAlertByTypeRef` |
| `tests/test_diagnostic_evaluator_v2.py` | Updated test data to reflect new realistic thresholds |

---

## Status
Committed and pushed to `alpha` branch (commit `af09f6e5`). All previously-passing tests continue to pass.
