# Specification: Open Source Map Migration

## Overview
Replace the current non-functional Google Maps implementation with an open-source mapping solution across the Road Ready platform. This migration will use Leaflet.js for the web frontend and react-native-maps for the mobile application, utilizing OpenStreetMap tiles with a dark-themed aesthetic.

## Functional Requirements
- **Web Migration (Leaflet.js):**
  - Replace Google Maps JavaScript API with Leaflet.js in all Jinja2 templates.
  - Configure MapTile layer using CartoDB Dark Matter or similar OpenStreetMap-based dark tiles.
- **Mobile Migration (react-native-maps):**
  - Configure `react-native-maps` to use OpenStreetMap tile providers.
  - Ensure dark theme consistency with the web implementation.
- **Core Features:**
  - **Live Ride Monitoring:** Real-time marker updates for vehicle position and a polyline "breadcrumb" trail of the current session.
  - **Diagnostic Ride Detail:** Rendering of historical ride paths (polylines) and event markers on the summary map.
  - **Booking Form:** Interactive map for selecting or verifying pick-up/drop-off locations.
  - **Search/Geocoding:** Integration of an open-source geocoding service (e.g., Nominatim) for address searches.

## Non-Functional Requirements
- **Performance:** Maps should load and render markers/polylines efficiently, especially during live streaming.
- **Privacy:** Avoid transmitting user location data to proprietary third-party mapping services where possible.
- **Consistency:** Maintain a unified map visual style (Dark Theme) across Web and Mobile.

## Acceptance Criteria
- [ ] No remaining dependencies on Google Maps APIs in the codebase.
- [ ] Map renders correctly in "Live Ride Monitoring" with real-time telemetry updates.
- [ ] Historical routes are accurately displayed on "Diagnostic Ride Detail" pages.
- [ ] Address search in the "Booking Form" successfully places a marker using an open-source geocoder.
- [ ] All maps follow the Dark Theme style guidelines.

## Out of Scope
- Hosting a private tile server.
- Turn-by-turn navigation logic.
- Satellite imagery layers.
