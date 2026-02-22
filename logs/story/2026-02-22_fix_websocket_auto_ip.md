# Log: Fix WebSocket Error & Auto-detect Server IP
**Date:** 2026-02-22
**Session:** Copilot CLI

---

## Context

WebSocket error `Software caused connection abort` persisted. Investigation revealed two problems:
1. A wrong URL fix was applied in the previous session (incorrect path prefix)
2. The root cause was a hardcoded IP in `client.js` that becomes stale whenever the device or network changes

---

## Changes Implemented

### Fix 1: Revert incorrect WebSocket URL change
**File:** `mobile-app/src/screens/DiagnosticRideActiveScreen.js`

The WebSocket is registered in `main.py` at `/api/v1/live-ride-stream` (direct route, bypassing the router prefix). A previous fix incorrectly changed the mobile URL to `/api/v1/diagnostic-rides/live-ride-stream`. Reverted to the correct path.

### Fix 2: Auto-detect server IP from Metro bundler
**File:** `mobile-app/src/api/client.js`

The server IP (`10.32.100.57`) was hardcoded and would break every time the device switched networks or machines. 

**Fix:** In `__DEV__` mode, read `Constants.expoConfig.hostUri` (Expo's Metro bundler host) to dynamically extract the current machine's IP. The Metro bundler always runs on the same machine as the backend server, so the IP is always correct with zero manual configuration.

```js
const getServerUrl = () => {
  if (__DEV__) {
    const hostUri = Constants.expoConfig?.hostUri || Constants.manifest?.debuggerHost;
    if (hostUri) {
      const host = hostUri.split(':')[0]; // strip Metro port, keep IP
      return `http://${host}:8000`;
    }
  }
  return 'http://PRODUCTION_URL';
};
```

No more IP editing when switching between Mac, laptop, home network, office network, etc.

---

## Files Changed

| File | Change |
|---|---|
| `mobile-app/src/api/client.js` | Dynamic IP resolution from Expo Metro bundler |
| `mobile-app/src/screens/DiagnosticRideActiveScreen.js` | Reverted WebSocket URL to correct `/api/v1/live-ride-stream` |

---

## Status
Committed and pushed to `alpha` branch (commit `fc174a87`).
