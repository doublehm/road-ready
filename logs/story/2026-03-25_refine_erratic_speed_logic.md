# 2026-03-25: Refine Erratic Speed Detection Logic

## Problem
The "Erratic Speed" detection was overly sensitive, flagging 100+ events in a 10-minute city drive. This was primarily due to:
1.  **Sensor Noise:** GPS speed jitter was being interpreted as intentional speed oscillations.
2.  **Traffic Flow:** Normal adjustments for following distances and traffic lights were triggering thresholds designed for steady-state driving.
3.  **Narrow Window:** A 10-second analysis window was too short to distinguish between a single adjustment and a sustained erratic pattern.

## Physicist-Instructor Solution
I re-engineered the logic using a "Physicist's filter" for noise and an "Instructor's lens" for context:

1.  **Increased Analysis Window:** Doubled the window from 10s to 15-20s to ensure a sustained pattern is detected.
2.  **Raised Amplitude Threshold:** Increased the required speed range from 5 km/h to 8 km/h.
3.  **Noise Floor (Delta Threshold):** Introduced `SPEED_OSCILLATION_MIN_DELTA = 2.0`. Individual speed changes must be at least 2 km/h per second to be counted as a "direction change," effectively filtering out GPS jitter.
4.  **Traffic Crawl Filter:** Introduced `SPEED_OSCILLATION_MIN_SPEED = 20.0`. If the average speed in a window is below 20 km/h, the evaluator assumes the driver is in heavy traffic or stop-and-go conditions and suppresses erratic flags.
5.  **Longer Cooldown:** Increased the cooldown between erratic events to 10 seconds to avoid flooding the report.

## Implementation Changes
-   Updated constants in `app/services/diagnostic_evaluator.py`.
-   Refactored `_evaluate_erratic_driving` to implement the speed floor and delta filtering.
-   Updated `tests/test_diagnostic_evaluator_v2.py` to match new window requirements.
-   Added `test_traffic_crawl_no_erratic_flag` to verify the traffic filter works as intended.

## Results
-   **Old Logic:** Flaggings occurred on almost any non-constant speed.
-   **New Logic:** Only flags significant, high-frequency "surging" (hunting for speed) at arterial/highway speeds, while ignoring normal city traffic adjustments.
-   **All Tests Passed.**
