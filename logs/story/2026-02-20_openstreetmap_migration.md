# Log: Map Provider Migration to OpenStreetMap
**Date:** 2026-02-20
**Author:** Gemini CLI Agent

## Context
Android devices were showing a blank map because Google Maps requires a production API key. To ensure immediate visibility during the prototype phase without requiring Google Cloud billing setup, the project has been migrated to use OpenStreetMap (OSM) tiles.

## Changes Implemented

### Mobile (React Native)
1.  **Component Update:** `RouteReplayMap.js` now uses `UrlTile` with the OSM tile server.
2.  **Screen Updates:**
    -   `DiagnosticRideActiveScreen.js`: Live tracking map switched to OSM.
    -   `BookingFlowScreen.js`: Pickup location selector switched to OSM.
    -   `DriveLogScreen.js`: Practice drive tracker switched to OSM.
3.  **Configuration:** Set `mapType="none"` on all `MapView` instances to disable the default (blank) Google layer and exclusively show OSM tiles.

## Status
The map is now fully visible on all Android devices immediately. The UI remains consistent across both Android and iOS. 

*Note: This can be easily reverted to Google Maps in the future by removing the `UrlTile` and `mapType="none"` props once an API key is provided in `app.json`.*
