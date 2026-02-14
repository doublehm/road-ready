# Specification: Safety & Supervisor-Led Workflow

## Overview
This track implements mandatory safety features to ensure the app is never used by the student while driving. It shifts the primary interaction during active driving sessions to the supervisor or instructor in the passenger seat.

## User Stories
*   **As a Student:** I want the app to be in a "Safe Mode" while I drive so that I can focus entirely on the road without distraction.
*   **As a Supervisor/Instructor:** I want to be the one responsible for starting, monitoring, and stopping the diagnostic session so that I can coach the student effectively.
*   **As a Business Owner:** I want to enforce strict safety protocols to minimize liability and promote safe driving habits.

## Functional Requirements
*   **Supervisor Hand-off Screen:** A mandatory interstitial screen before a diagnostic ride begins, requiring confirmation that the phone has been handed to the supervisor.
*   **Safety Disclaimer:** A clear legal disclaimer that must be accepted before every session.
*   **Supervisor-Only Controls:** In-ride buttons (Stop, Pause, Add Note) designed for the passenger's use.
*   **Voice Alerts (Accessibility):** Optional audio feedback for performance milestones (e.g., "Smooth braking detected") intended for the supervisor's ears.

## Non-Functional Requirements
*   **UX/UI:** The "Active Ride" screen should clearly indicate it is in "Driver Safety Mode."
*   **Compliance:** All logging features must be accessible only when the session is correctly initialized by a supervisor.
