# Specification: Diagnostic Ride UI Refinement

## 1. Problem Statement
In the active diagnostic ride session, the vertical telemetry rails (circular arc gauges) overlap with top-aligned UI elements such as the Elevation Chip, Insight Rail, and Advice Banners. This collision reduces legibility and compromises the professional, high-tech aesthetic of the application.

## 2. Objective
Resolve UI component collisions and improve information hierarchy using **Stitch MCP** design patterns. The goal is a pristine, non-overlapping, responsive layout that maintains the "premium cockpit" feel.

## 3. Proposed Changes

### 3.1. Floating Session Card (Top Center)
- **Consolidation**: Integrate altitude and grade percentage data directly into the bottom of the session card.
- **Visuals**: Use a subtle horizontal divider or tonal shift within the card to separate time/distance from elevation data.
- **Benefit**: Removes the standalone `ElevationChip` from the top-left corner, freeing up space for the `InsightRail`.

### 3.2. Telemetry Panel (Sides)
- **Positioning**: Change alignment from `Center` to `Bottom` (with a ~100dp vertical offset from the screen bottom).
- **Grouping**: Reduce vertical spacing between the 4 gauges from `12.dp` to `8.dp`.
- **Sizing**: Maintain existing gauge sizes (72dp down to 54dp) but ensure they are vertically stacked with high density.
- **Benefit**: Keeps gauges clear of top-aligned rails and banners while remaining accessible in the "thumb zone."

### 3.3. Insight Rail & Advice Banners (Top Sides)
- **Containment**: Implement a maximum height constraint for both the `InsightRail` (Top Left) and `Advice Banners` (Top Right).
- **Behavior**: If the list of faults or advice items exceeds the allocated space, the rails should become scrollable or use a "fading edge" effect to prevent downward growth into the telemetry gauges.

## 4. Design Tokens (RoadReady High-Tech)
- **Colors**: Background #1E293B (Navy), Accents #6366F1 (Indigo), Warnings #EF4444 (Red).
- **Aesthetic**: 20px backdrop-blur glassmorphism, semi-transparent overlays.
- **Rounding**: `ROUND_EIGHT` (8px).

## 5. Non-Functional Requirements
- **Responsiveness**: Layout must adapt to different screen heights without component overlap.
- **Performance**: High-frequency telemetry updates must remain smooth (60fps) during the transition.

## 6. Verification Plan
- **Automated**: Update UI tests to verify component boundaries using `onNodeWithTag`.
- **Manual**: Deploy to Android device and verify layout on both standard and short-screen simulators.
