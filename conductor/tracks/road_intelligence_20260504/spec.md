# Specification: Road Intelligence & Condition Learning System

## Overview
Add a crowdsourced, ML-driven road condition intelligence layer to Road Ready. The system learns from vehicle sensor data across every trip, building a persistent map of road hazards (potholes, rough surfaces, speed bumps) keyed to GPS coordinates. Drivers are warned proactively as they approach known problem areas — not after they hit them.

OSM zone compliance (school/playground speed limits by time-of-day) is already implemented; this track extends intelligence to physical road conditions.

## Functional Requirements

### Road Condition Detection (On-Device)
- Classify road surface quality in real-time from the existing 10 Hz accelerometer Z-axis stream using a lightweight on-device model.
- Classes: `smooth`, `rough`, `bump`, `pothole`, `speed_bump`
- Model must run on-device (no network call during detection), under 500 KB, inference < 5 ms per window.

### Road Condition Database (Backend)
- Every classified event is uploaded to a `road_conditions` MongoDB collection with GPS coordinates, speed, confidence, and device ID.
- Backend aggregates multi-trip, multi-device observations per road segment using H3 hexagonal grid (resolution 10 ≈ 15m segments).
- A segment is only marked as confirmed once ≥ 3 independent devices report it, weighted by observation speed (low-speed readings are more reliable).

### Proactive Approach Warnings (Mobile)
- At trip start, prefetch all confirmed hazard points within a 5 km radius into local SQLite cache.
- At each 1 Hz GPS tick, project trajectory 200 m ahead and query the local cache with a Haversine check.
- Warning triggers: 200 m at highway speed (> 60 km/h), 80 m at city speed.
- 60-second cooldown per hazard to prevent alert fatigue.

### OSM Speed Zone Integration
- Already implemented in `SpeedLimitService.kt` + `SafetyZoneCalculator.kt`.
- This track adds road surface `smoothness` and `surface` tags from OSM Overpass to dynamically relax harsh-braking thresholds on rough or unpaved roads.

## Technical Architecture

### On-Device Model
- **Algorithm**: Quantized Decision Tree / Random Forest → exported to ONNX (< 200 KB).
- **Features** (extracted over a 2-second / 20-sample sliding window, Z-axis):
  - Root Mean Square Acceleration (RMSA) — correlated with International Roughness Index
  - Peak-to-peak amplitude
  - Z-axis variance
  - High-pass filtered energy (removes suspension sway, keeps impact spikes)
  - Speed at time of window (from GPS) — used to weight confidence at backend
- **Training data**: Existing `acceleration_data` JSONB from `diagnostic_rides` table, labelled by the current threshold detector as seed labels, then improved iteratively.
- **Training pipeline**: `train_road_condition_model.py` using scikit-learn → `skl2onnx` export.
- **Runtime**: ONNX Runtime Mobile (Android), Core ML via ONNX converter (iOS).

### Backend
- **New collection**: `road_conditions` in MongoDB with `2dsphere` + H3 resolution-10 index.
- **New endpoints**:
  - `POST /road-conditions/report` — receive a batch of classified events from a completed trip.
  - `GET /road-conditions/nearby?lat=&lng=&radius_m=` — return confirmed segments within radius.
- **Aggregation job**: Background FastAPI task triggered after each report ingestion. Groups by H3 cell, applies speed-weighted confidence scoring, marks segment confirmed at threshold ≥ 3 devices.
- **Caching**: Confirmed segments cached in-memory (TTL 1 hour) to serve the nearby endpoint fast.

### KMP Shared
- `RoadConditionClassifier.kt` — wraps ONNX Runtime, exposes `classify(window: FloatArray): RoadCondition`.
- `RoadConditionRepository.kt` — fetches nearby hazards from backend, stores in local Room/SQLite DB.
- `HazardApproachDetector.kt` — 1 Hz loop: project heading × speed → query local DB → emit `HazardApproach` event with distance + type.

### UI
- `RoadHazardAlert` composable in `DiagnosticRideActiveScreen` — slides up from bottom (same pattern as `SafetyAlertOverlay`) with road condition icon + distance ("Pothole · 150m ahead").
- Road hazard markers overlaid on `RouteReplayMap` in the post-ride results screen.

## Acceptance Criteria
- [x] Model size ≤ 500 KB — exported at 345 KB (0.978 macro-F1 on synthetic + real data).
- [x] `road_conditions` MongoDB collection with 2dsphere + H3 indexes created and queryable — bootstrapped via `ensure_indexes()` at startup.
- [x] Trip-end upload batches classified events to backend — `RoadConditionRepository.uploadTrip()` → `POST /road-conditions/report`.
- [x] Backend aggregation confirms segments after ≥ 3 independent device reports — `_aggregate_cell()` with speed-weighted confidence.
- [x] Nearby endpoint returns results in < 100 ms — served from 1-hour in-memory cache; `GET /road-conditions/nearby`.
- [x] KMP prefetch loads hazards at trip start without blocking the UI thread — `RoadConditionRepository.prefetchHazards()` is a suspend function.
- [x] Proactive warning fires ≤ 200 m before a confirmed hazard — `HazardApproachDetector` (200 m highway, 80 m city, 3 s lookahead).
- [x] OSM `surface`/`smoothness` tags fed via `SpeedLimitState` — backend + KMP both updated.
- [ ] On-device ONNX model classifies road surface in < 5 ms on Pixel 8 Pro — pending Phase 3 integration and live timing test.
- [ ] Post-ride results screen shows road hazard markers on route replay map — Phase 4.
- [ ] `DiagnosticEvaluator` threshold relaxation from OSM surface tags — Phase 3.
