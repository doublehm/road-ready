# Research Resources: OpenStreetMap Integration

This document serves as the primary context for deep research using NotebookLM.

## Current System Architecture
- **Backend:** FastAPI (Python)
- **Database:** Hybrid SQLite (Business) + MongoDB (Telemetry)
- **Frontend:** Leaflet.js (Web), native Google Maps (Mobile)
- **OSM Integration:**
    - **Speed Limits:** `app/services/speed_limit_service.py` queries Overpass API.
    - **Geocoding:** Nominatim used in `app/templates/booking_form.html`.
    - **Routing:** Currently limited; mostly manual pinning in `app/static/maps.js`.

## Existing Files to Analyze
- `app/services/speed_limit_service.py`: Core logic for speed limit fetching and school zone detection.
- `app/static/maps.js`: Leaflet initialization and helper functions.
- `app/templates/booking_form.html`: Geocoding implementation using Nominatim.
- `conductor/tech-stack.md`: Context on the mapping stack.

## Research Focus Areas
1. **OSM Metadata for Routing:**
    - Tags: `highway`, `oneway`, `turn:lanes`, `restriction`.
    - Tools: OSRM (Open Source Routing Machine), Valhalla, or custom Overpass queries for route optimization.
2. **Map Matching & Telemetry:**
    - Algorithms: Hidden Markov Model (HMM) based map matching.
    - OSM Tags for surface, smoothness, and lane information to refine telemetry.
3. **Safety (School & Playground Zones):**
    - Tag refinement: `leisure=playground`, `amenity=school`, `maxspeed:conditional`.
    - Proximity logic improvements (currently 200m).
4. **Map Performance:**
    - Tile caching strategies.
    - Vector tiles vs. raster tiles for Leaflet.
    - Leaflet optimization plugins.

## Specific Constraints
- Must focus on British Columbia (BC) regulations (school zone hours, default speeds).
- Must work within the current tech stack (Leaflet/Nominatim).
- No migration to Google Maps for speed limits (too expensive).
