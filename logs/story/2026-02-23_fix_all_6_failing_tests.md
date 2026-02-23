# Session Log: 2026-02-23 — Fix All 6 Failing Tests (38/38 Green)

## Problem
After pulling latest alpha, the full test suite had 6 failures:

```
FAILED tests/test_api_live_ride.py::test_websocket_connection
FAILED tests/test_api_live_ride.py::test_websocket_broadcast
FAILED tests/test_api_live_ride.py::test_websocket_event_detection
FAILED tests/test_api_live_ride.py::test_websocket_persistence
FAILED tests/test_diagnostic_evaluator_v2.py::test_evaluate_aggregates_all_events
FAILED tests/test_nosql_api.py::test_get_telemetry_endpoint
```

---

## Root Causes & Fixes

### 1. WebSocket URL Mismatch (4 tests)
**Root cause:** Tests connected to `/api/v1/diagnostic-rides/ws/{ride_id}` (path param) but the actual WebSocket endpoint is `/api/v1/diagnostic-rides/live-ride-stream?ride_id=...` (query param).

**Fix:** Updated all 4 WS test URLs to use the correct `live-ride-stream?ride_id=...` pattern.

---

### 2. WebSocket Tests Hanging on MongoDB (broadcast, persistence)
**Root cause:** The `ConnectionManager.persist_data()` method calls `nosql_repo.save_telemetry_chunk()` / `nosql_repo.save_event()`, which tries to connect to MongoDB. In CI / dev without a running MongoDB, Motor's default `serverSelectionTimeoutMS` (30s) causes the tests to hang.

**Fix:** Added `unittest.mock.patch` for both `nosql_repo.save_telemetry_chunk` and `nosql_repo.save_event` in the affected tests.

---

### 3. Persistence Test Asserting Stale SQLite Columns
**Root cause:** `test_websocket_persistence` was checking `ride.route_coords` and `ride.acceleration_data` SQLite columns. But the WS handler was refactored to persist telemetry to **MongoDB** (not SQLite). The old assertions were never updated.

**Fix:** Replaced SQLite column assertions with assertions on the mocked nosql_repo calls — verifying the correct `ride_id`, location, and acceleration data was passed to `save_telemetry_chunk` and `save_event`.

---

### 4. Broadcast Test: Timestamp Added by Server
**Root cause:** `persist_data` adds a `timestamp` field to telemetry points that lack one. This mutates the data dict in-place before `broadcast()` sends it. The test's `assert received_data == telemetry_data` failed because the received message had an extra `timestamp` key.

**Fix:** Changed assertion to check individual fields (`type`, `speed`, `location`) instead of full dict equality.

---

### 5. Broadcast Test: Mobile Echo Not Drained
**Root cause:** `ConnectionManager.broadcast()` sends the message to ALL clients including the sender (mobile). The mobile_ws had unread data in its buffer, which blocked the second `receive_json()` in the test flow.

**Fix:** Added `mobile_ws.receive_json()` to drain the echo before checking `web_ws.receive_json()`.

---

### 6. `test_evaluate_aggregates_all_events` — Missing async + ride_id arg
**Root cause:** `DiagnosticEvaluator.evaluate()` signature is `async def evaluate(self, ride_id: str, ride_data: Dict)`. The test called it as `evaluator.evaluate(ride_data)` — missing `ride_id` AND not awaited.

**Fix:** Decorated test with `@pytest.mark.asyncio`, changed to `async def`, and called as `await evaluator.evaluate("test_ride_id", ride_data)`.

---

### 7. `MagicMock` Not Imported in test_nosql_api.py
**Root cause:** `MagicMock` was used but not imported (only `patch` and `AsyncMock` were imported from `unittest.mock`).

**Fix:** Added `MagicMock` to the import.

---

### 8. pytest.ini for asyncio_mode
**Added:** `pytest.ini` with `asyncio_mode = auto` to support `@pytest.mark.asyncio` tests without per-test decoration issues.

---

## Result

```
38 passed, 0 failed, 39 warnings in 0.68s
```

## Files Changed

| File | Change |
|------|--------|
| `tests/test_api_live_ride.py` | WS URL fix, nosql mocks, persistence assertions, echo drain, timestamp assertion |
| `tests/test_diagnostic_evaluator_v2.py` | async + ride_id arg for aggregate test |
| `tests/test_nosql_api.py` | MagicMock import |
| `pytest.ini` | New file: `asyncio_mode = auto` |
