# Specification: OpenStreetMap Data Integration Research (KMP)

## Overview
This track focuses on conducting deep research into leveraging OpenStreetMap (OSM) data to enhance the Road Ready Kotlin Multiplatform (KMP) application's features and performance. The goal is to move beyond basic map display and tap into OSM's rich metadata to improve routing, live ride accuracy, and safety features while maintaining native performance using Google Maps (Android) and MapKit (iOS).

## Functional Requirements
*   **KMP Native Mapping Implementation:** Implement native mapping components for Android (Google Maps SDK) and iOS (MapKit) that leverage OSM for supplemental data overlays.
*   **Intelligent Safety Zone Alerts:** Implement real-time school and playground zone alerts in the KMP app using OSM metadata and solar-time logic.
*   **Polyline Simplification:** Implement KMP-shared geometry logic to optimize route rendering.
*   **Map-Matching for KMP:** Integrate server-side map-matching to snap GPS telemetry to the OSM road network.

## Acceptance Criteria
*   [x] Successful deep research conducted using NotebookLM for KMP-specific mapping.
*   [x] Delivery of an "Actionable Plan" focused on KMP implementation.
*   [x] Functional `SafetyZoneCalculator` in the KMP shared repository. *(commit: a14eac6)*
*   [x] Real-time safety alerts active in the KMP UI (Android). *(commit: 67eca31 — `SafetyAlertOverlay` integrated into `DiagnosticRideActiveScreen`)*
*   [ ] Polyline simplification active for historical ride views. *(`GeometryUtils.kt` exists but not yet wired into `RouteReplayMap`)*
*   [x] Native iOS MapKit integration (stub or basic implementation). *(commit: 67eca31)*
