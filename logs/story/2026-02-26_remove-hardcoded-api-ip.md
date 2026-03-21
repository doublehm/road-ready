# Remove Hardcoded API IP — 2026-02-26

## Problem
After switching to a new device, sign-up (and all API calls) were failing silently.
The mobile app could not reach the backend server.

## Root Cause
`mobile-app/src/api/client.js` had a hardcoded fallback IP `http://10.32.100.57:8000`
(old machine). When Expo's `hostUri` dynamic detection returned `null` on the new device,
the app fell through to this stale IP — making every API request fail.

## What Was Changed
- `mobile-app/src/api/client.js` line 19: replaced hardcoded IP with
  `process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8000'`
- Created `mobile-app/.env` with `EXPO_PUBLIC_API_URL=http://192.168.1.74:8081`
  (git-ignored via root `.gitignore`)

## Result
No IPs are hardcoded in source. To change server address, update `.env` only.
