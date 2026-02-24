# Fix: MongoDB Socket Timeout & WebSocket Silent Failures

**Date:** 2026-02-24

## Problem

During rides, the WebSocket live stream was reconnecting 20+ times per session. Telemetry data was being silently dropped with no errors visible in app logs.

## Root Cause

1. **MongoDB `socketTimeoutMS=2000`** — A 2-second socket timeout is too aggressive for Docker network I/O during active ride telemetry writes. Motor would abandon the request after 2s, but MongoDB would still try to send the response to the now-closed socket, causing ~20 "Broken pipe" / "Connection reset by peer" errors per ride in MongoDB logs. This caused connection pool churn and silent telemetry data loss.

2. **Silent exception swallowing** — `persist_data()` caught all exceptions with `pass`, and the WebSocket endpoint's `except Exception` handler disconnected silently without logging. This masked the MongoDB errors entirely, making the issue invisible in app logs.

## Changes

### `app/database.py`
- Increased `socketTimeoutMS`: `2000` → `10000` (10 seconds)
- Increased `serverSelectionTimeoutMS`: `2000` → `5000`
- Increased `connectTimeoutMS`: `2000` → `5000`
- Added `maxIdleTimeMS=30000` to prevent stale connections from accumulating in the pool

### `app/api/diagnostic_rides.py`
- Removed duplicate import block (file had all imports duplicated)
- Added `import logging` and `logger = logging.getLogger(__name__)`
- `persist_data()`: replaced `except Exception as e: pass` with `logger.error(...)` so MongoDB write failures are visible in logs
- `websocket_endpoint()`: replaced bare `except Exception:` with `except Exception as e: logger.error(...)` so WebSocket loop crashes are visible in logs
