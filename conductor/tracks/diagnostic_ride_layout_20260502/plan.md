# Diagnostic Ride UI Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve component collisions between side telemetry gauges and top HUD elements while improving information hierarchy.

**Architecture:** Refactor `DiagnosticRideActiveScreen` to integrate elevation data into the `FloatingSessionCard` and reposition `TelemetryPanel` gauges lower on the screen with tighter spacing.

**Tech Stack:** Jetpack Compose (Multiplatform), Kotlin.

---

### Task 1: Integrate Elevation into FloatingSessionCard

**Files:**
- Modify: `mobile-app-kmp/shared/src/commonMain/kotlin/com/roadready/ui/screens/diagnostic/DiagnosticRideActiveScreen.kt`

- [x] **Step 1: Update FloatingSessionCard signature and layout**
- [x] **Step 2: Update DiagnosticRideActiveScreen to pass elevation data**
- [x] **Step 3: Commit changes**

---

### Task 2: Reposition and Tighten Telemetry Rails

**Files:**
- Modify: `mobile-app-kmp/shared/src/commonMain/kotlin/com/roadready/ui/components/TelemetryPanel.kt`
- Modify: `mobile-app-kmp/shared/src/commonMain/kotlin/com/roadready/ui/screens/diagnostic/DiagnosticRideActiveScreen.kt`

- [x] **Step 1: Reduce gauge spacing in TelemetryPanel**
- [x] **Step 2: Change TelemetryPanel alignment in DiagnosticRideActiveScreen**
- [x] **Step 3: Commit changes**

---

### Task 3: Implement Height Constraints for Side Rails

**Files:**
- Modify: `mobile-app-kmp/shared/src/commonMain/kotlin/com/roadready/ui/screens/diagnostic/DiagnosticRideActiveScreen.kt`

- [x] **Step 1: Wrap InsightRail and Advice Banners in height-constrained Box**
- [x] **Step 2: Commit changes**

---

### Task 4: Final Verification

- [x] **Step 1: Visual check on mobile simulator**
