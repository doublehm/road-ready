# Plan: Google Maps & Advanced Physics Integration

## Background & Motivation
The user requested two major upgrades:
1. Replace the problematic OSM WebView map with the native Google Maps SDK for Android to utilize free tier features (Maps, Places Autocomplete, and Navigation).
2. Deep research and refactoring of the driving physics evaluator (`diagnostic_evaluator.py`) to correctly calculate stopping force, acceleration, and cornering G-forces to accurately detect driving errors.
3. Integrate free speed limit data using OpenStreetMap (OSM) via the Overpass API since Google Maps restricts speed limit data to expensive enterprise contracts.

## Scope & Impact
1. **Google Maps**: Update `build.gradle.kts` to use `google-maps-compose` and `google-places`. Modify booking and diagnostic screens to use native maps and address autocomplete.
2. **Speed Limits**: Update the backend to query OSM (Overpass API) for `maxspeed` tags using the GPS coordinates recorded during the ride.
3. **Physics Engine**: Rewrite the `diagnostic_evaluator.py` backend logic. It must isolate linear acceleration (remove gravity), align the phone's axes with the vehicle's reference frame, and evaluate forces in G-units ($1G = 9.81 m/s^2$).
4. **Copilot Delegation**: We will delegate the complex math and physics rewrite of the Python backend to GitHub Copilot (Opus 4.6).

## Proposed Solution

### Part 1: Google Maps Integration
*   **Maps SDK for Android**: 100% free and unlimited on mobile devices. We will replace the WebView in `DiagnosticRideActiveScreen` and `DiagnosticRideDetailScreen` with the native `GoogleMap` composable.
*   **Places API (Address Lookup)**: "Place Autocomplete" falls under the Essentials tier (10,000 free sessions/month). We will integrate this into the booking flow for students to easily look up pickup/dropoff addresses.

### Part 2: OSM Speed Limit Integration
*   Use the **Overpass API** (OpenStreetMap) to query the `maxspeed` tag for roads based on the GPS coordinates collected during the ride. This provides a free, open-source alternative to Google's restricted Speed Limits API.

### Part 3: Physics Research (Driving Error Detection)
To accurately detect driving errors, we must treat the phone as an inertial measurement unit (IMU) inside a moving vehicle reference frame.

1.  **Gravity Filtration (Linear Acceleration)**: Raw accelerometers measure gravity ($9.81 m/s^2$). We must subtract the gravity vector (derived from the low-pass filtered accelerometer or rotation vector) from the raw data to get pure linear acceleration.
2.  **Reference Frame Alignment**: The phone's physical axes ($X_{phone}, Y_{phone}, Z_{phone}$) must be rotated to match the vehicle's axes ($X_{lat}, Y_{long}, Z_{vert}$). 
    *   $Z_{vert}$ is aligned with gravity.
    *   $Y_{long}$ (forward/backward) is found by correlating sustained acceleration with GPS speed changes.
3.  **Thresholds & Force Calculation**:
    *   **Hard Acceleration ($+Y_{long}$)**: Normal cars max out around $0.4G$. We flag "Harsh Acceleration" if $a_{long} > 0.3G$ ($2.94 m/s^2$).
    *   **Hard Braking ($-Y_{long}$)**: Normal braking is $-0.2G$. We flag "Hard Braking" if $a_{long} < -0.4G$ ($-3.92 m/s^2$). Emergency braking is $-0.8G$.
    *   **Harsh Cornering ($X_{lat}$)**: Lateral acceleration ($a_{lat} = v^2/r$). We flag "Sharp Turn" if $|a_{lat}| > 0.3G$.
4.  **Jerk Calculation ($da/dt$)**: High jerk ($> 1.0 m/s^3$) causes a "whiplash" feeling and indicates uncoordinated pedal control.

## Implementation Steps
1. Add `google-maps-compose` and `places` to KMP dependencies.
2. Refactor `PlatformOsmMap.android.kt` to export a native `GoogleMap` composable.
3. Add a Places Autocomplete text field to the `BookingFlowScreen.kt`.
4. Update backend speed limit service to query OSM via Overpass API.
5. Run `mcp_github_create_pull_request_with_copilot` to delegate the `diagnostic_evaluator.py` physics refactoring to Copilot Opus 4.6, providing it with the exact G-force thresholds and vector transformation requirements.

## Verification
- Test that native Google Maps loads instantly without WebView black screen issues.
- Verify address autocomplete returns valid places.
- Ensure speed limits are accurately fetched from OSM.
- Ensure the Copilot PR passes `test_advanced_physics.py` with the new rigorous math.