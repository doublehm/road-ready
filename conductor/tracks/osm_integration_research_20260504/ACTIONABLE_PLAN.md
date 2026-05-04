# Actionable Plan: OpenStreetMap Enhancements (KMP Focus)

This plan prioritizes implementing OSM-driven enhancements within the Kotlin Multiplatform (KMP) application, leveraging native mapping SDKs (Google Maps on Android, MapKit on iOS) while using OSM for metadata and intelligent backend services.

## Priority 1: Native Performance & User Experience (Short Term)
### 1. Optimize Native Map Rendering (Android/iOS)
- **Feature:** Seamless route and event visualization using native SDKs.
- **Tasks:**
    - [x] (Already Implemented) Use `Maps Compose` for Android to ensure ZERO-COST native performance.
    - [ ] Implement native `MapKit` actual for iOS in `PlatformOsmMap.ios.kt` (currently a stub).
    - [ ] Implement a custom dark theme for Google Maps and MapKit to match the Road Ready aesthetic.
    - [ ] Add Polyline simplification in the `shared` module (using a KMP-compatible Douglas-Peucker implementation) to optimize long-ride rendering.

### 2. Intelligent Speed Limit Alerts (Backend + KMP Integration)
- **Feature:** Real-time speed limit and safety zone alerts in the KMP app.
- **Tasks:**
    - [ ] Update `shared/src/commonMain/kotlin/com/roadready/data/repository/SpeedLimitService.kt` to handle the new `zone_type` and `source` metadata from the backend.
    - [ ] Create a `SafetyAlertOverlay` in the KMP UI to display school/playground zone warnings.
    - [ ] Implement a local buffer in the KMP app to store upcoming safety zones to ensure alerts work even with intermittent connectivity.

## Priority 2: Safety & Precision (Medium Term)
### 3. Advanced Safety Zone Detection (Backend)
- **Feature:** Accurate school and playground zone identification using OSM metadata.
- **Tasks:**
    - [ ] (Backend) Update `_query_overpass` to fetch `leisure=playground` and `traffic_sign` nodes.
    - [ ] (Backend) Implement stateful 'Safety Zone' logic for entry/exit detection.
    - [ ] (Shared KMP) Refine local speed limit logic to prioritize these safety zones during active rides.

### 4. HMM Map-Matching Integration
- **Feature:** Align GPS tracks to the OSM road network for precise scoring.
- **Tasks:**
    - [ ] (Backend) Implement HMM-based map matching using OSM way geometry.
    - [ ] (Shared KMP) Add a 'Snapped Route' toggle in the `RouteReplayMap` to show the corrected path vs. raw GPS data.

## Priority 3: Smart Navigation (Long Term)
### 5. Smart Lesson Routing (Valhalla + KMP)
- **Feature:** Proximity-based lesson scheduling and navigation.
- **Tasks:**
    - [ ] (Backend) Deploy/Integrate Valhalla for routing matrices and dynamic costing.
    - [ ] (Shared KMP) Create a `RoutingRepository` to fetch optimized lesson paths.
    - [ ] (Android/iOS) Implement native turn-by-turn guidance or intent-based navigation handoff (e.g., to Google Maps/Apple Maps app).

## Summary of Technical Strategy
- **Mapping:** Native SDKs (Google Maps Android / MapKit iOS) for performance.
- **OSM Usage:** Strategic metadata retrieval (Overpass API) and advanced routing (Valhalla).
- **Communication:** Incremental telemetry sync between KMP app and FastAPI backend.
