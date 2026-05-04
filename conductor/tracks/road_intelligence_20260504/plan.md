# Implementation Plan: Road Intelligence & Condition Learning System

## Phase 1: Training Pipeline & Model (Backend)
- [x] Task: Add `road_conditions` MongoDB collection with 2dsphere + H3-res10 indexes — bootstrapped at startup via `ensure_indexes()` in `road_condition_service.py`.
- [x] Task: Write `train_road_condition_model.py` — extracts RMSA, peak amplitude, Z-axis variance, high-pass energy features; trains Random Forest; exports ONNX via `skl2onnx`; 345 KB, 0.978 macro-F1.
- [x] Task: Add `app/services/road_condition_service.py` — ingest batch reports, H3 grouping, speed-weighted confidence scoring, ≥3-device confirmation logic, 1-hour in-memory segment cache.
- [x] Task: Add `POST /road-conditions/report` and `GET /road-conditions/nearby` FastAPI endpoints in `app/api/road_conditions.py`.
- [ ] Task: Conductor - User Manual Verification 'Phase 1' (Protocol in workflow.md)

## Phase 2: KMP Shared Logic
- [x] Task: ONNX Runtime Mobile dependency already present in `shared/build.gradle.kts` (androidMain).
- [x] Task: Implement `RoadConditionClassifier.kt` in `commonMain` — 5-feature extractor (RMSA, peak-amp, variance, HP-energy, speed) + `PlatformConditionInference` expect/actual wrappers (Android ONNX, iOS threshold fallback).
- [x] Task: Implement `RoadConditionRepository.kt` — backend fetch (`getNearbyHazards`) + in-memory hazard cache; `uploadTrip()` batch upload.
- [x] Task: Implement `HazardApproachDetector.kt` — 1 Hz trajectory projection (3 s lookahead), Haversine check, 200 m highway / 80 m city threshold, 60 s cooldown.
- [x] Task: Extend `SpeedLimitService.kt` and `SpeedLimitState` with `surface` and `smoothness` OSM tags; backend `speed_limit_service.py` now returns both.
- [ ] Task: Conductor - User Manual Verification 'Phase 2' (Protocol in workflow.md)

## Phase 3: Integration with Ride Services
- [x] Task: Hook `RoadConditionClassifier` into `DiagnosticRideActiveScreen` — `snapshotFlow` on `motionState.data.size` triggers Z-axis classification every 20 samples; events buffered with GPS coordinates in `roadConditionEvents`.
- [x] Task: Hook `HazardApproachDetector` into `DiagnosticRideActiveScreen` — prefetch hazards on first GPS fix, call `onLocationUpdate` every GPS tick with computed bearing, reset on dispose.
- [x] Task: On ride completion, batch-upload classified events via `RoadConditionRepository.uploadTrip()` in background coroutine.
- [x] Task: Pass OSM `surface`/`smoothness` tags to ride payload as `road_surface` and `road_smoothness` fields.
- [ ] Task: Conductor - User Manual Verification 'Phase 3' (Protocol in workflow.md)

## Phase 4: UI
- [x] Task: Implement `RoadHazardAlert` composable — amber/red frosted pill with icon + label + distance, spring slide-up `AnimatedVisibility`, inside `DiagnosticRideActiveScreen`.
- [x] Task: Integrated above `SafetyAlertOverlay` in the bottom column.
- [x] Task: Road hazard markers added to `RouteReplayMap` in results screen — yellow/red `BitmapDescriptorFactory` markers; fetched via `RoadConditionRepository.prefetchHazards` from the route midpoint.
- [ ] Task: Conductor - User Manual Verification 'Phase 4' (Protocol in workflow.md)
