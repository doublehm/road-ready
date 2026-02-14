# Specification: Integrated Student Progress Tracking and Diagnostic Ride Logging

## Overview
This track aims to provide a unified experience for students and instructors by integrating diagnostic ride logging with real-time progress tracking across the web and mobile platforms.

## User Stories
*   **As an Instructor:** I want to log the results of a diagnostic ride directly from the mobile app or web portal so that the student's progress is immediately updated.
*   **As a Student:** I want to see my progress updated in real-time on my mobile dashboard after a lesson or diagnostic ride.
*   **As an Admin:** I want to ensure that all diagnostic rides are correctly logged and associated with the correct student and instructor for audit purposes.

## Functional Requirements
*   **API Enhancements:**
    *   Endpoint to submit diagnostic ride results (Pass/Fail per criteria).
    *   Endpoint to fetch comprehensive student progress (aggregated from lessons, quizzes, and diagnostic rides).
*   **Web Portal Changes:**
    *   Instructor dashboard: Add a "Log Diagnostic Ride" button and form.
    *   Student dashboard: Enhance the progress visualization to include diagnostic ride history.
*   **Mobile App Changes:**
    *   Instructor View: Add functionality to log diagnostic rides on the go.
    *   Student View: Real-time dashboard updates using the new progress endpoint.
*   **Data Model:**
    *   Ensure `DiagnosticRide` and `StudentProgress` models are robust and linked to existing `User` and `Booking` models.

## Non-Functional Requirements
*   **Responsiveness:** Progress updates should be reflected within 2 seconds of submission.
*   **Offline Support:** (Mobile) Logged rides should be queued if the instructor is offline and synced when a connection is restored.
*   **Security:** Ensure only authorized instructors can log rides for their assigned students.
