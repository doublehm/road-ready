# 2026-03-21: Real-Time G-Force Edge Glow Overlay

## Problem
Drivers had no real-time visual feedback for the intensity of braking and cornering forces during a diagnostic ride. The existing system only showed alerts after a threshold was exceeded (harsh_braking / sharp_turn events), with no continuous indication.

## Solution
Added a `GForceOverlay` component that lights up the screen edges with a yellow → red colour gradient based on live accelerometer data.

### Cornering (Lateral G-Force)
- **Left turn** → left edge of the screen glows.
- **Right turn** → right edge of the screen glows.
- Colour ramps from **amber** (0.15–0.35 G) through **orange** (0.35–0.6 G) to **red** (0.6 G+).
- 5-layer gradient band (60 px) with decreasing opacity creates a smooth glow effect.

### Braking (Longitudinal G-Force)
- **Top edge** of the screen glows when significant braking (or acceleration) force is detected.
- Same amber → red colour ramp as cornering.

### G-Force Badge
- A floating badge near the bottom of the screen shows the current peak G-force value (max of lateral and longitudinal) with the matching colour.

## Technical Details
- **Lateral axis**: `acceleration.x` — consistent in both flat and mounted phone orientations.
- **Longitudinal axis**: `max(|acceleration.y|, |acceleration.z|)` — orientation-agnostic; after gravity removal, the axis aligned with the road is whichever has the larger magnitude.
- **Dead zone**: Forces below 0.15 G are invisible (filters engine vibration, GPS jitter, gentle lane changes).
- **Performance**: `useMemo` prevents recalculation unless acceleration values change. At most 15 lightweight `<View>` elements are rendered (5 layers × up to 3 edges). Early bail-out when all forces are below the dead zone.
- **pointerEvents="none"**: The overlay does not intercept touches — all buttons and controls beneath remain fully interactive.

## Files Changed
- **`mobile-app/src/components/GForceOverlay.js`** — New component.
- **`mobile-app/src/screens/DiagnosticRideActiveScreen.js`** — Import and render `GForceOverlay` above the map layer, below modals.
