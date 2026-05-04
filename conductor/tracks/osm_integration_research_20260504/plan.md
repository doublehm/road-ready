# Implementation Plan: OpenStreetMap Data Integration Research

## Phase 1: Research Preparation & Setup
- [x] Task: Initialize research context and gather existing OSM integration points in Road Ready.
- [x] Task: Prepare dataset or documentation pointers for NotebookLM (current tech stack, mapping files).

## Phase 2: Deep Research with NotebookLM (KMP Integration)
- [x] Task: Research native mapping SDK capabilities (Google Maps vs. MapKit) for OSM metadata overlay.
- [x] Task: Investigate KMP-compatible polyline simplification and client-side map matching.
- [x] Task: Detail OSM tag strategies for school zone and playground speed limit detection for KMP alerts.
- [x] Task: Research Valhalla routing integration within the KMP shared repository.
- [x] Task: Conductor - User Manual Verification 'Phase 2' (Protocol in workflow.md)

## Phase 3: Actionable Plan Generation
- [x] Task: Synthesize research findings into a prioritized list of features.
- [x] Task: Draft the "Actionable Plan" document with clear technical tasks for each feature.
- [x] Task: Final review and alignment with Product Definition and Tech Stack.

## Phase 4: Shared KMP Implementation (Core Logic)
- [x] Task: Implement custom Douglas-Peucker simplification in `commonMain`.
- [x] Task: Implement `SafetyZoneCalculator` with BC-specific school/playground logic in `commonMain`.
- [x] Task: Add `zone_type` and `source` support to `SpeedLimitService.kt`.
- [ ] Task: Conductor - User Manual Verification 'Phase 4' (Protocol in workflow.md)

## Phase 5: Platform Implementation (UI & Maps)
- [x] Task: Update `PlatformOsmMap.android.kt` to support optional OSM TileOverlay.
- [x] Task: Implement basic native `MapKit` actual for iOS.
- [x] Task: Create `SafetyAlertOverlay` UI component in `commonMain`.
- [ ] Task: Integrate HMM map-matching results toggle in `RouteReplayMap`.
- [ ] Task: Conductor - User Manual Verification 'Phase 5' (Protocol in workflow.md)
