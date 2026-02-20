# Log: Fix Instructor Earnings Crash and Diagnostic Trends
**Date:** 2026-02-20
**Author:** Gemini CLI Agent

## Context
Instructors were experiencing a "TypeError: Cannot read property 'full_name' of null" when viewing the Earnings screen. Additionally, the backend was returning 422 errors for the `/diagnostic-rides/progress-trends` endpoint when accessed by instructors.

## Changes Implemented

### Backend (SQLAlchemy/FastAPI)
1.  **Schema Update:** Added `student: Optional[User]` and `instructor: Optional[InstructorProfile]` to the `DiagnosticRide` schema in `app/schemas.py`.
2.  **API Enhancements:**
    -   `app/api/diagnostic_rides.py`: Updated `list_diagnostic_rides`, `get_diagnostic_ride`, and `get_student_rides` to use `joinedload` for the `student` and `instructor` relationships.
    -   `app/api/diagnostic_rides.py`: Refactored `get_progress_trends` to support instructors viewing aggregate trends for all rides they have supervised when no specific `student_id` is provided. This resolves the 400 error for instructors on the Ride History screen.
3.  **Imports:** Added `joinedload` and `Optional` where necessary.

### Mobile (React Native)
1.  **Earnings Screen Fix:** 
    -   `InstructorEarningsScreen.js`: Added a null check for the `student` object in the "My Students" list to prevent crashes when diagnostic ride data is incomplete or has a null student reference.
    -   `InstructorEarningsScreen.js`: Added a check for `student.full_name` before accessing its first character for the avatar.

## Status
The crash is fixed, and the diagnostic trends now correctly display for both students and instructors (individual vs. aggregate).
