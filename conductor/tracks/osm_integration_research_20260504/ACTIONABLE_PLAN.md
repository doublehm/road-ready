# Actionable Plan: OpenStreetMap Enhancements (KMP Implementation)

This plan provides the technical roadmap for implementing OSM-driven features within the Road Ready Kotlin Multiplatform (KMP) application.

## Priority 1: Native Performance & Safety (Short Term)
### 1. Enhanced Native Mapping (Android/iOS)
- **Feature:** Seamlessly overlay OSM tiles and metadata on native map SDKs.
- **Tasks:**
    - [x] **Android:** Update `PlatformOsmMap.android.kt` to support optional OSM tile overlays via `TileOverlay` for areas with poor Google Maps detail. *(commit: 67eca31)*
    - [x] **iOS:** Implement `PlatformOsmMap.ios.kt` using `MapKit` and `MKTileOverlay` to replace or overlay Apple Maps tiles with OSM. *(commit: 67eca31)*
    - [x] **Shared:** Implement a custom Douglas-Peucker simplification algorithm in `commonMain` to optimize polyline rendering for long rides. *(commit: a14eac6 — `GeometryUtils.kt`)*
    - [ ] **Shared:** Add a `MapAttribution` component in `commonMain` to display required OSM and MapKit/Google attribution.

### 2. Intelligent Safety Zone Alerts
- **Feature:** Real-time school and playground zone warnings with BC-specific logic.
- **Tasks:**
    - [x] **Backend:** Refactor `speed_limit_service.py` to return `zone_type` (school/playground) and specific `maxspeed:conditional` tags. *(commit: 67eca31)*
    - [x] **Shared:** Implement `SafetyZoneCalculator` in `commonMain` using the `Kastro` library for sunrise/sunset calculations (playground zones) and BC-specific school hour logic. *(commit: a14eac6)*
    - [x] **Shared:** Update `SpeedLimitService.kt` to fetch and cache these safety zone details. *(commit: a14eac6)*
    - [x] **UI:** Create a `SafetyAlertOverlay` that triggers visual and auditory warnings when entering an active 30km/h zone. *(commit: 67eca31)*

## Priority 2: Precision & Accuracy (Medium Term)
### 3. HMM Map-Matching Integration
- **Feature:** Correct GPS 'drift' by snapping tracks to the OSM road network.
- **Tasks:**
    - [ ] **Backend:** Deploy a Map-Matching service (e.g., Valhalla `trace_attributes` or GraphHopper).
    - [ ] **Shared:** Implement `MapMatchingRepository` using Ktor to send simplified polylines and receive snapped paths.
    - [ ] **UI:** Add a "Corrected Path" toggle in the `RouteReplayMap` results screen.

### 4. Road Attribute Validation
- **Feature:** Use OSM attributes to refine driving evaluation.
- **Tasks:**
    - [ ] **Backend:** Fetch road `surface` and `smoothness` tags during evaluation.
    - [ ] **Shared:** Pass these attributes to the `DiagnosticEvaluator` to dynamically adjust jerk/force thresholds (e.g., higher tolerance on unpaved roads).

## Priority 3: Intelligent Logistics (Long Term)
### 5. Smart Scheduling & Routing
- **Feature:** Automated instructor routing and lesson scheduling.
- **Tasks:**
    - [ ] **Backend:** Integrate Valhalla's Matrix API to calculate travel times between multiple student locations.
    - [ ] **Backend:** Implement a VRPTW (Vehicle Routing Problem with Time Windows) solver using Google OR-Tools.
    - [ ] **Shared:** Create `RoutingRepository` to provide instructors with optimized daily schedules based on real-time OSM travel estimates.

## Technical Summary
- **Client Mapping:** `Maps Compose` (Android) / `MapKit` (iOS).
- **Network Client:** `Ktor` with `kotlinx.serialization`.
- **Astronomical Logic:** `Kastro` (KMP) for sunrise/sunset.
- **Geometry Logic:** Shared custom Douglas-Peucker implementation.
