# Log: Fix 401 Unauthorized on /live-evaluate
**Date:** 2026-02-23
**Session:** Copilot CLI

---

## Context

`/api/v1/diagnostic-rides/live-evaluate` was returning 401 Unauthorized on every call during an active diagnostic ride. The endpoint is hit every 2 seconds, so this meant zero real-time event detection for the entire ride.

The speed-limit endpoint had the same issue and was fixed in a prior session (Feb 22). This was the same pattern.

---

## Root Cause

The endpoint had `current_user: models.User = Depends(deps.get_current_user)` — requiring a valid JWT. The auth token was either not being attached or timing out mid-ride, causing every request to 401.

`/live-evaluate` only receives raw sensor numbers (accelerometer, GPS speed) and returns detected events. It does not query the database or access any user-specific data — there is nothing to protect with auth here.

---

## Fix

**File:** `app/api/diagnostic_rides.py`

Removed:
- `current_user: models.User = Depends(deps.get_current_user)` parameter
- The role check (`if current_user.role not in ["student", "instructor"]`) that depended on it

This is identical to the pattern applied to `get_speed_limit_for_location` in the Feb 22 session.

---

## Status
Committed and pushed to `alpha` branch (commit `2cb7acce`). Test `test_live_evaluate_endpoint` still passes.
