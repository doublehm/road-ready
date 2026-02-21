# Specification: OpenStreetMap Smoothness & Performance Optimization

## Overview
This track aims to eliminate "blinking" tiles, laggy marker updates, and container resizing issues within the Leaflet-based map implementation. The goal is to provide a fluid, high-performance mapping experience across all platforms (Web and Mobile WebView), particularly during live diagnostic rides and trip history playback.

## Functional Requirements
*   **Anti-Blinking:** Implement strategies to prevent tile flashing/blinking when the map pans or zooms.
*   **Fluent Marker Movement:** Implement interpolation for marker updates so vehicles "glide" instead of jumping between coordinate points.
*   **Robust Resizing:** Ensure the map container correctly calculates its dimensions in all views (modals, dashboards, hidden tabs) to prevent "gray tile" issues.
*   **Auto-Centering Logic:** Refine how the map follows a moving marker to ensure it stays centered without jerky movements.

## Non-Functional Requirements
*   **Performance:** Map interactions should maintain 60FPS where possible.
*   **Reliability:** Map initialization must be robust across different browser and WebView environments.
*   **Visual Consistency:** Use the established Dark Theme (CartoDB Dark Matter) consistently.

## Acceptance Criteria
*   [ ] Map tiles do not blink or flash during continuous movement.
*   [ ] Markers move smoothly across the map during live updates (no "teleporting").
*   [ ] The map renders fully and correctly whenever a modal or hidden tab is opened (no manual `invalidateSize` hacks visible to the user).
*   [ ] The map follows the active vehicle smoothly in Live Ride mode.

## Out of Scope
*   Replacing Leaflet with Mapbox/Google Maps.
*   Adding new geofencing or routing features.
