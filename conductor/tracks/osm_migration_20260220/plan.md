# Implementation Plan: Open Source Map Migration

## Phase 1: Preparation & Dependency Cleanup [checkpoint: 3ae1c62]
Goal: Remove all Google Maps dependencies and prepare the environment for open-source alternatives.

- [x] Task: Remove Google Maps scripts and API keys from `app/templates/base.html` and other templates. 89b36ed
- [x] Task: Remove Google Maps related packages from `mobile-app/package.json` if applicable. 89b36ed
- [x] Task: Install Leaflet.js and related CSS in the web frontend. 89b36ed
- [x] Task: Conductor - User Manual Verification 'Preparation & Dependency Cleanup' (Protocol in workflow.md) 3ae1c62

## Phase 2: Web Implementation (Leaflet.js) [checkpoint: b6491f4]
Goal: Replace web-based maps with Leaflet.js and integrate an open-source geocoder.

- [x] Task: Implement a reusable Leaflet map component in `app/static/custom.css` and a common JavaScript helper. 4563917
- [x] Task: Update `app/templates/diagnostic_ride_detail.html` to use Leaflet for route visualization. b0ff011
- [x] Task: Update `app/templates/booking_form.html` with Leaflet and Nominatim geocoding for address selection. f888616
- [x] Task: Update `app/templates/home.html` (or wherever Live Monitoring is) to support real-time Leaflet updates. 1de48a3
- [x] Task: Conductor - User Manual Verification 'Web Implementation' (Protocol in workflow.md) b6491f4

## Phase 3: Mobile Implementation (React Native)
Goal: Update the Expo application to use OpenStreetMap tiles with react-native-maps.

- [ ] Task: Configure `react-native-maps` to use OpenStreetMap tile providers in `mobile-app/App.js` or relevant screens.
- [ ] Task: Implement the Dark Theme style for the mobile map component.
- [ ] Task: Update the Live Ride screen in the mobile app to handle real-time marker and polyline updates via OSM.
- [ ] Task: Conductor - User Manual Verification 'Mobile Implementation' (Protocol in workflow.md)

## Phase 4: Integration & Final Polish
Goal: Ensure data consistency and perform final verification across all platforms.

- [ ] Task: Verify end-to-end telemetry flow from mobile app to web dashboard using the new maps.
- [ ] Task: Audit codebase for any remaining 'google' or 'maps.googleapis' strings.
- [ ] Task: Perform cross-browser and cross-platform (iOS/Android) map rendering checks.
- [ ] Task: Conductor - User Manual Verification 'Integration & Final Polish' (Protocol in workflow.md)
