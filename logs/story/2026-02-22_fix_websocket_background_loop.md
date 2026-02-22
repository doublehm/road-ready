# Log: Fix WebSocket Background Reconnect Loop
**Date:** 2026-02-22
**Session:** Copilot CLI

---

## Context

`WebSocket error: Software caused connection abort` was appearing in the console every time the app was sent to the background. This is expected Android OS behavior (TCP connections are killed when backgrounded), but the code was treating it as an unexpected error and making things worse.

---

## Root Cause

When Android backgrounds the app:
1. OS kills the TCP connection → `onerror` fires → logged as `console.error` (red in console)
2. `onclose` fires → starts exponential backoff retry timer (1s, 2s, 4s...)
3. Each retry in the background also fails → more errors, more timers
4. When returning to foreground: **both** the AppState handler AND the backoff timer fire simultaneously → duplicate reconnect race condition

---

## Fix

Added `isInBackgroundRef` to track app state:

**Going to background:**
- Set `isInBackgroundRef.current = true`
- In `onerror`: log as `console.log` ("expected, not an error") instead of `console.error`
- In `onclose`: if backgrounded → **return early**, skip exponential backoff entirely

**Returning to foreground:**
- Set `isInBackgroundRef.current = false`
- Cancel any pending backoff timer
- Call `connectWebSocket()` immediately (single, clean reconnect)

---

## Result

| Before | After |
|--------|-------|
| Red error in console on every background | `console.log` only (expected behavior) |
| Retry loop running in background | No retries while backgrounded |
| Race condition on foreground resume | Single clean reconnect via AppState |

---

## File Changed

| File | Change |
|---|---|
| `mobile-app/src/screens/DiagnosticRideActiveScreen.js` | Added `isInBackgroundRef`; gated `onclose` retry and `onerror` log level on background state |

---

## Status
Committed and pushed to `alpha` branch (commit `3855c69e`).
