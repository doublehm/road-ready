# Implementation Plan: Stitch-Refined Diagnostic Ride Layout

## Objective
Refine the `DiagnosticRideActiveScreen` and `TelemetryPanel` layouts using the **Stitch MCP** tools to resolve overlapping UI elements (specifically the Telemetry gauges colliding with the Speedometer HUD). The goal is to generate a pristine, non-overlapping, modern Glassmorphism layout that aligns with the 'safety-first' navy/green aesthetic, and implement this refined layout in Jetpack Compose.

## Key Files & Context
- `mobile-app-kmp/shared/src/commonMain/kotlin/com/roadready/ui/screens/diagnostic/DiagnosticRideActiveScreen.kt`: The main HUD layout.
- `mobile-app-kmp/shared/src/commonMain/kotlin/com/roadready/ui/components/TelemetryPanel.kt`: The arc gauges.
- `improve_road_ready_ui.md`: Stitch aesthetic guidelines.

## Implementation Steps

### Phase 1: Stitch Layout Generation & Analysis
1.  **Invoke Generalist Agent**: Delegate to the `generalist` sub-agent to utilize the connected Stitch MCP tools.
    -   Provide Stitch with the current layout dimensions (e.g., Bottom HUD at `padding(bottom=24.dp)` + `140.dp` height vs. Telemetry at `padding(bottom=108.dp)` + `66.dp` height).
    -   Prompt Stitch to produce a comprehensive, responsive layout spec that correctly positions the telemetry gauges, top HUD, and bottom HUD without any overlap.
    -   Request precise design tokens (spacing, sizing, corner radii) for a modern, 'safety-first' aesthetic.

### Phase 2: Jetpack Compose Refactoring
1.  **Refine `DiagnosticRideActiveScreen.kt`**:
    -   Update the padding, arrangement, and alignment of the Top Floating HUD, Bottom Floating HUD, and Side Telemetry based on the Stitch spec.
    -   Ensure dynamic spacing so the layout remains responsive across different device sizes.
2.  **Refine `TelemetryPanel.kt`**:
    -   Adjust the sizing and bottom padding of the arc gauges so they sit comfortably above or beside the primary Speedometer HUD.
    -   Refine the visual aesthetic (colors, glows, stroke widths) to match the Stitch design tokens.

## Verification & Testing
1.  **Compile & Deploy**: Build the updated KMP Android app and deploy it to the connected device (`10.0.0.156:33581`) via wireless ADB.
2.  **Visual QA**: Confirm that the overlapping gauges issue is completely resolved and the UI feels cohesive and modern on the physical screen.