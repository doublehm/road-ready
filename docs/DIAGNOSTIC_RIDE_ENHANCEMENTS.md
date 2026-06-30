# Diagnostic Ride Enhancements

This document details the recent major upgrades to the RoadReady Diagnostic Ride feature, improving accuracy, providing richer feedback, and adding real-time visual coaching.

## 1. Real-time Speed Limit Integration
The system now retrieves and displays the actual speed limit for the vehicle's current location.

- **OSM Integration**: Queries the Overpass API for OpenStreetMap road data.
- **Local Fallbacks**: Uses BC-specific default speed limits (e.g., 50km/h residential) when OSM data is unavailable.
- **School Zone Support**: Automatically detects nearby schools and applies the BC school zone limit (30km/h) during school hours (Mon-Fri, 8am-5pm).
- **Visual Feedback**: The speed indicator in the mobile app changes color based on compliance:
  - **Green**: Within limit
  - **Yellow**: 1-10 km/h over
  - **Red**: >10 km/h over

## 2. Advanced Diagnostic Evaluation
The backend evaluator has been significantly upgraded to provide deeper insights.

- **Route Segmentation**: The ride is now broken into color-coded segments based on speed compliance, allowing students to see exactly where they were speeding on a map.
- **Lane Discipline Analysis**: Uses heading variance from GPS data to detect excessive weaving or poor lane centering.
- **School Zone Monitoring**: Specifically tracks and penalizes speeding within school zones with a 2x penalty multiplier.
- **Enhanced Scoring**: More nuanced scoring for braking, cornering, and speed control, including "Smoothness Bonuses."
- **Actionable Tips**: Generates specific, pedagogical tips for improvement (e.g., "Brake BEFORE the turn, not during it").

## 3. Rich Visual Results
The results screen has been completely overhauled with interactive components.

- **Route Replay Map**: An interactive map showing the full route with color-coded segments and event markers for mistakes.
- **Speed vs. Limit Graph**: A detailed chart showing the vehicle's speed plotted against the actual speed limit throughout the ride.
- **Event Timeline**: A chronological list of all significant events (harsh braking, sharp turns, speeding) with timestamps and descriptions.
- **Category Breakdown**: Detailed cards for Braking, Speed Control, and Cornering with individual scores and specific feedback notes.

## 4. Technical Implementation Details
- **Backend Service**: `app/services/speed_limit_service.py` handles API calls and caching.
- **Mobile Hook**: `useSpeedLimit.js` provides throttled, cached speed limit lookups to the UI.
- **Data Schemas**: Updated `DiagnosticRide` model to store `speed_limit_data` and `heading_data`.
- **Navigation**: Added `DiagnosticRideHistoryScreen` for students and instructors to review past performances.
