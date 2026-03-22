# 2026-03-22: G-Force Overlay Sensitivity Fix + App-Wide Dark Theme

## Problems
1. **G-force overlay not showing forces during ride**: 5 braking events detected by backend but no visual feedback shown during ride. Harsh intentional corners not detected even as yellow.
2. **G-force badge disappears when idle**: User wants to see the force readout at all times to verify detection is working.
3. **Most screens still light-themed**: 28 screens used light backgrounds (#F6FAFE, #fff, #f5f5f5) creating jarring transitions between dark diagnostic screens and light everything else.

## Root Causes
- **G-force thresholds too high**: DEAD_ZONE was 0.15G (filters out moderate forces), YELLOW_CEIL was 0.35G, RED_FLOOR was 0.6G — most normal driving maneuvers fell below visibility.
- **State throttle too slow**: useDeviceMotion throttled React state updates to 500ms. Peak forces during 1-2s braking events were missed between updates.
- **Early return hid badge**: `if (latOp === 0 && brkOp === 0) return null` removed the entire overlay including the G-force badge when no force detected.

## Changes Made

### GForceOverlay.js
- Lowered DEAD_ZONE_G: 0.15 → 0.05 (catches lighter maneuvers)
- Lowered YELLOW_CEIL_G: 0.35 → 0.20 (yellow starts sooner)
- Lowered RED_FLOOR_G: 0.6 → 0.40 (red at moderate-hard braking)
- Lowered MAX_DISPLAY_G: 1.0 → 0.80 (full opacity reached sooner)
- Badge always visible during ride with muted grey (#94A3B8) when idle
- Edge glow still conditional on force exceeding dead zone

### useDeviceMotion.js
- Reduced STATE_THROTTLE_MS: 500 → 150ms (~6-7 state updates/sec)
- Provides faster force data to the overlay without dropping peaks

### Dark Theme — 28 Screens Converted
All screens now use the unified dark palette:
- Login, Register, SetupStudent, SetupInstructor
- StudentHome, InstructorHome, EducationHome
- BookingFlow, BookingRequests, Checkout, SupervisorHandoff
- InstructorEarnings, InstructorProfile, InstructorSchedule, EditProfile
- SessionDetail, GradeStudent, Notifications, Legal
- Conversations, Chat, DriveLog, Modules, StudentDetailStats
- QuizScreen, FindInstructor, StudentEditProfile, StudentProgress

Combined with the 5 diagnostic screens converted previously (2026-03-21), every screen in the app now uses dark theme.
