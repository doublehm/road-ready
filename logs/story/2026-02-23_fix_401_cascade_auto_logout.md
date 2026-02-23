# Log: Fix 401 Cascade & Add Auto-Logout
**Date:** 2026-02-23
**Session:** Copilot CLI

---

## Context

App was flooding the console with 401 errors on startup and during rides:
- `Error fetching user [AxiosError: 401]`
- `Error fetching bookings [AxiosError: 401]`
- `Error starting ride record [AxiosError: 401]`

Followed by Network Errors. The 401s indicate a stale/invalid JWT (token from a previous device session or after server restart on a different machine).

---

## Root Cause

No response interceptor existed. When a 401 was received:
- Every screen and hook that made API calls logged its own error independently
- The invalid token stayed in SecureStore and kept being sent on every request
- The user was never redirected to login — they were just stuck with a broken session

---

## Changes Implemented

### `mobile-app/src/api/client.js`

**Added global 401 response interceptor:**
- On any 401 response: delete `userToken` and `userRole` from SecureStore, then call a registered logout callback
- `registerLogoutCallback(fn)` exported so AuthContext can hook in without a circular import
- Added `console.log('[client] API base URL:', BASE_URL)` at startup to verify IP auto-detection is working (check Expo console on first app launch)

### `mobile-app/src/context/AuthContext.js`

- Import `registerLogoutCallback` from client
- On mount, register a callback that clears `userToken`, `userRole`, `userInfo` state
- When the interceptor fires, React state clears → navigation stack reads null token → redirects to login screen automatically

---

## Result

| Before | After |
|--------|-------|
| Dozens of 401 errors across all screens | Single 401 handled globally |
| Stale token kept in SecureStore | Token immediately cleared |
| User stuck with broken session | Auto-redirected to login |

---

## Files Changed

| File | Change |
|---|---|
| `mobile-app/src/api/client.js` | 401 response interceptor + `registerLogoutCallback` + startup URL log |
| `mobile-app/src/context/AuthContext.js` | Register logout callback on mount |

---

## Status
Committed and pushed to `alpha` branch (commit `6295c396`).
