# Speed-limit display & on-screen reporting redesign

**Date:** 2026-05-08
**Branch:** kotlin
**Scope:** active diagnostic ride screen + supporting services in the KMP shared module

## Problem

The active ride screen has three correlated issues:

1. **False school-zone display.** On highways, the speed-limit indicator can drop to 30 km/h (school-zone) and stick there even after the driver leaves the area. Caused by a combination of: a server returning a `school` zone tag for a highway-adjacent cell, the client's `SafetyZoneCalculator` applying the 30 km/h reduction without checking road class, a coarse client-side cache that lets one cell's reading apply to neighbouring road segments, and a smoothing algorithm that recovers from "dramatic" upward limit changes only after two agreeing readings (≥30 s).

2. **Discrepancy popup is symmetric and over-eager.** `SpeedLimitDiscrepancyService` fires the same full-screen "what's the actual speed limit?" dialog whether the driver is *over* or *under* the OSM limit. Slow stretches typically mean traffic / stop sign / hazard, not a wrong map — but the current modal still pushes the driver to correct the map.

3. **No on-screen reporting.** Manual reporting features (road conditions, lane closures, traffic incidents, hazards) are not present on the active ride screen. The only "discrepancy" entry point is the modal, which only appears when the detector fires and not when the driver proactively wants to report something.

Additionally, the **Coaching (indigo) and Terrain (teal) banners** are too cramped — the two-line label-plus-body structure at 10/13 sp is hard to read at a glance while driving.

## Goals

- Move the speed-limit number **inside** the existing FloatingSpeedometer dial; remove the separate red MAXIMUM-band sign.
- Add **five always-visible report buttons** orbiting the upper arc of the speedometer, each opening a small bottom sheet for sub-type/severity selection.
- **Split the discrepancy detector** into directional events: over-speed → inline correction strip; under-speed → button-pulse hint, no modal.
- **Replace the speed-limit verification modal** with a slim inline correction strip below the speedometer.
- Fix the **school-zone false positive and slow recovery** with three client-side guards (road-class gating, finer cache, asymmetric smoothing + force re-query).
- Make the **Coaching and Terrain banners** larger, single-line, more readable.

## Non-goals

- Server-side OSM zone-tag accuracy improvements. The fixes here are client-side guards that work regardless of server tag sloppiness.
- Changing the existing ICBC Observe sheet, real-time speeding alert, or hazard approach detector behaviour.
- Map-side rendering of new report types — incidents already render via the existing flagged-incidents pipeline; new categories use the same plumbing.

---

## Architecture overview

```
        ┌──────────────────────────────────┐
GPS ──▶ │     SpeedLimitService            │
        │  (server query + smoothing +     │
        │   road-class-aware cache)        │
        └────────────┬─────────────────────┘
                     │ SpeedLimitState
                     ▼
   ┌──────────────────────────────────────────────────┐
   │  SpeedLimitDiscrepancyService                    │
   │   ─ OverSpeed event  ────▶ correction strip      │
   │   ─ UnderSpeed event ────▶ button-pulse hint     │
   └──────────────────────────────────────────────────┘
                     │
                     ▼
   ┌──────────────────────────────────────────────────┐
   │   FloatingSpeedometer (redesigned)               │
   │     ┌─ inner dial: speed / limit ────────┐       │
   │     │  arc-ring with limit tick mark     │       │
   │     └────────────────────────────────────┘       │
   │     5 orbiting report buttons (upper arc)        │
   │     Pulses subset on UnderSpeed event             │
   └────────┬───────────────────────────┬─────────────┘
            │                           │
            ▼                           ▼
  SpeedLimitCorrectionStrip      ReportBottomSheet
   (over-speed + manual)         (per-category sub-types)
            │                           │
            └─────────┬─────────────────┘
                      ▼
                  ApiClient
              (existing endpoints +
               new report types)
```

---

## Components

### 1. FloatingSpeedometer (redesigned)

Lives in `DiagnosticRideActiveScreen.kt` as today.

**Inner dial:**
- Big speed reading: 52 sp Black, unchanged size.
- Below it, a small "/ NN" in muted text at 14 sp, where NN is the current `speedLimitState.currentSpeedLimit?.toInt()`. Hidden if no limit.
- Below "/ NN", the existing "KM/H" caption.
- The standalone red-band MAXIMUM sign (`SpeedLimitSign` composable) is **removed**.

**Arc ring:**
- A 1.5 dp tick mark is drawn on the arc at the fraction `limit / maxDisplay` of the 240° sweep — the visual reference for "you're crossing the limit."
- Existing colour logic (Secondary / Warning / Error based on speed vs limit) unchanged.

**Orbiting buttons:**
- Five 44 dp circular buttons positioned along an upper-half arc around the dial. Geometry: angles −150°, −120°, −90°, −60°, −30° from the dial centre (0° = right, −90° = straight up; symmetric across the vertical axis); radius = (dial radius + 28 dp). Implemented with `Modifier.offset` from the dial centre so they keep working at any container size.
- Because the orbit extends ~72 dp above the dial top, the existing `RoadHazardAlert` + `SafetyAlertOverlay` row above the speedometer needs to move. They relocate into the right-side advice column (where Coaching / Terrain / SpeedAlert already stack), so the air above the dial belongs to the orbit.
- Order, left to right: 🚧 construction · 🚗 traffic · ⚠️ hazard · 🕳️ road-condition · 🪧 speed-limit-sign.
- Tap → opens `ReportBottomSheet` with category-specific sub-type chips.
- Each button reads a `pulseFor: Set<ReportCategory>` state (from `SpeedLimitDiscrepancyService.pulses`); when its category is in the set, it animates a soft glow (alpha + scale, 1.5 s loop, 6 s total).

**Touch geometry caveat:** because the buttons orbit a fixed arc, on narrow screens (<360 dp) the row could clip the screen edges. The whole orbit container fades to a single horizontal row above the dial when measured width < 360 dp (fallback layout in the same composable, no separate component).

### 2. ReportBottomSheet

A new component in `ui/components/ReportBottomSheet.kt`.

```kotlin
enum class ReportCategory { Construction, Traffic, Hazard, RoadCondition, SpeedLimitSign }

data class ReportSubtype(val id: String, val label: String, val icon: String)

@Composable
fun ReportBottomSheet(
    category: ReportCategory,
    onSubmit: (subtype: ReportSubtype, severity: String) -> Unit,
    onDismiss: () -> Unit,
)
```

Sub-types per category (initial set, can grow):
- Construction → lane-closure, full-closure, detour, work-zone-no-closure
- Traffic → jam, stopped, accident, slow
- Hazard → debris, animal, stalled-vehicle, pedestrian
- RoadCondition → pothole, rough, ice, water, snow
- SpeedLimitSign → opens the SpeedLimitCorrectionStrip flow instead of this sheet (handled in the speedometer's onClick)

Severity chips: Low / Medium / High (default Medium). Submit posts via `ApiClient.reportRoadEvent(category, subtypeId, severity, lat, lon, rideId)`.

### 3. SpeedLimitCorrectionStrip

A new component in `ui/components/SpeedLimitCorrectionStrip.kt`. **Replaces** `SpeedLimitVerificationDialog.kt`, which is deleted.

```kotlin
@Composable
fun SpeedLimitCorrectionStrip(
    osmLimitKmh: Double,
    observedSpeedKmh: Double,
    onSubmit: (reportedKmh: Double?) -> Unit,
    onDismiss: () -> Unit,
)
```

- Slides up from below the speedometer (`AnimatedVisibility` + `slideInVertically`).
- Header line: `Map says NN · is the actual limit?` (16 sp, bold)
- Row of 4 chips with the 4 standard limits closest to `observedSpeedKmh`. Tap → submits + dismisses.
- "Custom" chip → expands an inline numeric input within the strip. Submit / cancel inline.
- "✕" dismiss button on the right.
- Auto-hides after 12 s of no interaction.

Triggered in two places:
1. `SpeedLimitDiscrepancyService.OverSpeed` event.
2. Tap on the 🪧 orbital button.

### 4. SpeedLimitDiscrepancyService (split)

```kotlin
sealed class DiscrepancyEvent {
    data class OverSpeed(val osmKmh: Double, val observedKmh: Double, val lat: Double, val lon: Double) : DiscrepancyEvent()
    data class UnderSpeed(val osmKmh: Double, val observedKmh: Double, val lat: Double, val lon: Double) : DiscrepancyEvent()
}

class SpeedLimitDiscrepancyService(private val apiClient: ApiClient) {
    val events: SharedFlow<DiscrepancyEvent>
    val pulses: StateFlow<Set<ReportCategory>>   // populated for ~6 s on UnderSpeed
    fun onSpeedAndLimit(speedKmh, osmLimitKmh, lat, lon, nowMs)
    suspend fun submitFlag(reportedKmh: Double?)   // unchanged signature, used by strip + manual button
    fun dismissOverSpeedPrompt()
}
```

Constants:
- `OVER_SPEED_THRESHOLD = 25.0` km/h, sustained for 10 s (existing).
- `UNDER_SPEED_THRESHOLD = 25.0` km/h, sustained for **15 s** (longer because slow stretches are noisier).
- `MIN_SPEED_KMH = 15.0` (existing).
- `COOLDOWN_MS = 120_000` per event type independently.
- Under-speed pulse maps to `setOf(Traffic, Hazard, Construction)`.

The over-speed flow also writes to backend through `submitFlag` (unchanged endpoint). The under-speed flow is **never** auto-submitted — tapping a pulsing button is what creates a backend record, via the same `ReportBottomSheet` plumbing as a manual tap.

### 5. SafetyZoneCalculator (road-class gated)

```kotlin
fun getEffectiveLimit(baseLimit: Int, zoneType: String, now: Instant = Clock.System.now()): Int {
    if (baseLimit > 60) return baseLimit  // arterial+ never gets school/playground reduction
    return when (zoneType) {
        "school"     -> if (isSchoolZoneActive(now))     30 else baseLimit
        "playground" -> if (isPlaygroundZoneActive(now)) 30 else baseLimit
        else         -> baseLimit
    }
}
```

The 60 km/h threshold matches BC's residential ceiling — schools / playgrounds sit on streets ≤50 km/h; arterials are 60+, highways 80+.

### 6. SpeedLimitService (cache + smoothing changes)

**Cache key** changes from 3-decimal (~111 m) to 4-decimal (~11 m) lat/lon, and FIFO-evicts at 200 entries:

```kotlin
private fun roundCoord(value: Double): Double = (value * 10000).roundToInt() / 10000.0
private val cacheKeys = ArrayDeque<String>()
private fun putCache(key: String, value: SpeedLimitResponse) {
    if (cache.size >= 200) cacheKeys.removeFirstOrNull()?.let { cache.remove(it) }
    cache[key] = value; cacheKeys.addLast(key)
}
```

**Smoothing** becomes asymmetric:

```kotlin
if (confirmed != null && abs(newLimit - confirmed) > DRAMATIC_CHANGE_THRESHOLD) {
    val needed = if (newLimit > confirmed) 1 else DRAMATIC_CHANGE_MIN_AGREE   // upward = trust faster
    val agreeing = recentLimits.count { abs(it - newLimit) <= AGREE_TOLERANCE }
    if (agreeing < needed) limitToApply = confirmed
}
```

**Force re-query** on speed mismatch — new method called from the active screen's existing speed/limit collector:

```kotlin
fun maybeForceRequery(observedSpeedKmh: Double, lat: Double, lon: Double, nowMs: Long) {
    val confirmed = confirmedLimit ?: return
    if (observedSpeedKmh - confirmed >= 30.0) {
        if (mismatchStartMs == 0L) mismatchStartMs = nowMs
        if (nowMs - mismatchStartMs >= 5_000) {
            // bypass the 50 m / 15 s throttle and refetch
            lastQueryTime = 0L; lastQueryLat = null; lastQueryLon = null
            confirmedLimit = null  // discard so smoothing doesn't reject the fresh reading
            mismatchStartMs = 0L
        }
    } else mismatchStartMs = 0L
}
```

The active screen already has a `LaunchedEffect` snapshot-flowing `gpsState.speed to speedLimitState.currentSpeedLimit`; a one-line addition wires `maybeForceRequery` into it.

### 7. RideAdviceBanner (replaces CoachingBanner & TerrainTipBanner)

```kotlin
@Composable
fun RideAdviceBanner(
    icon: String,
    shortMessage: String,
    gradient: Pair<Color, Color>,
)
```

- 240 dp wide, 16 dp padding, 32 dp icon circle.
- Single line: `shortMessage` at 16 sp `ExtraBold` white. Capped at ~24 chars; producers must provide a short imperative.
- No separate label / title row — what used to be the all-caps label is folded into the message itself.

Producer changes:
- `CoachingEvent.Type` gains `shortMessage: String` (e.g., `"EASE OFF"`, `"SHARP TURN AHEAD"`). Existing `message: String` stays for the post-ride report screen's history view.
- `ElevationService` builds short phrases like `"STEEP CLIMB +8%"`, `"DOWNHILL −6%"`. The existing longer `terrainTip` stays for any other consumer; `shortTerrainTip` is added for the banner.

Existing `CoachingBanner` and `TerrainTipBanner` private composables in `DiagnosticRideActiveScreen.kt` are deleted; the screen calls `RideAdviceBanner(...)` for both with the right gradient.

---

## Data flow — under-speed pulse

```
GPS tick ─▶ speedLimitService.onLocationChanged
GPS tick ─▶ discrepancyService.onSpeedAndLimit(speed, limit, lat, lon, now)
              │
              │  observed - osm ≤ -25 sustained 15 s
              ▼
          emit DiscrepancyEvent.UnderSpeed
          set pulses = {Traffic, Hazard, Construction}, then clear after 6 s
              │
              ▼
   FloatingSpeedometer collects pulses; affected buttons glow
              │
   driver taps ⚠️ Hazard
              ▼
   ReportBottomSheet(category=Hazard) opens
   driver picks "debris" + High → submit
              ▼
   ApiClient.reportRoadEvent(...)  ── single new endpoint
```

## Data flow — over-speed correction

```
GPS tick ─▶ discrepancyService.onSpeedAndLimit(...)
              │  observed - osm ≥ 25 sustained 10 s
              ▼
          emit DiscrepancyEvent.OverSpeed
              │
              ▼
   active screen sets `correctionStripVisible = true`
              ▼
   SpeedLimitCorrectionStrip slides up
   driver taps "50" chip
              ▼
   discrepancyService.submitFlag(50.0)   // existing endpoint, unchanged
   strip slides out
```

---

## Backend touch points

- **Existing:** `apiClient.flagSpeedLimit(SpeedLimitFlagRequest)` — unchanged.
- **Existing:** `apiClient.completeRide(...)` and `RoadConditionRepository.uploadTrip(...)` — unchanged.
- **New:** `apiClient.reportRoadEvent(category, subtypeId, severity, lat, lon, rideId)` — POST returning a created event id. Backend persists into the same flagged-incidents pipeline that already feeds FlaggedIncidentsMap. The implementation plan picks the concrete path/payload (likely `POST /road-events/` with the existing flagged-incident fields plus `category` and `subtype`); from a spec standpoint the contract is the Kotlin function signature on `ApiClient`.

---

## Error handling

- `reportRoadEvent` failure → bottom sheet shows an inline error strip ("Couldn't send. Tap to retry.") for 4 s; report is dropped if not retried (no offline queue in v1).
- `submitFlag` failure → correction strip flashes red border and stays open for retry; auto-dismisses after 8 s.
- Server zone tag missing or null → `currentSpeedLimit` stays null; speedometer shows `--` for limit; no over/under-speed events fire.

---

## Testing plan

- **Unit (`commonTest`):**
  - `SafetyZoneCalculatorTest`: school/playground ignored when `baseLimit > 60`; applied when ≤60.
  - `SpeedLimitServiceTest`: cache eviction at 200; finer cache key prevents collision; asymmetric smoothing accepts upward jump after 1 reading; `maybeForceRequery` discards stale confirmed limit after 5 s of mismatch.
  - `SpeedLimitDiscrepancyServiceTest`: over-speed and under-speed fire independently; cooldowns are independent; pulses set then clears after 6 s; under-speed never calls `submitFlag`.
- **Manual (Android device):**
  - Drive the highway-near-school stretch that triggered the original report; confirm the limit no longer collapses to 30 and recovers within ≤15 s after leaving any false school zone.
  - Trigger sustained over-speed: correction strip appears, picking a chip submits, dismiss works.
  - Trigger sustained under-speed: buttons pulse, no modal; tapping a pulsing button opens the right sub-type sheet.
  - Tap each of the 5 orbital buttons; confirm sub-type sheet, severity selection, submission.
  - Verify Coaching and Terrain banners are single-line, 16 sp, readable at arm's length.

---

## Build sequence (suggested)

1. `SafetyZoneCalculator` road-class guard + cache key tightening + asymmetric smoothing + `maybeForceRequery` (small, isolated, unit-testable).
2. Split `SpeedLimitDiscrepancyService` into directional events + pulses StateFlow.
3. New `SpeedLimitCorrectionStrip`, replace modal usage in `DiagnosticRideActiveScreen`. Delete `SpeedLimitVerificationDialog.kt`.
4. New `ReportBottomSheet` + `ApiClient.reportRoadEvent` interface + backend stub.
5. Redesign `FloatingSpeedometer`: inner-dial limit, tick mark, orbiting buttons, pulse animation. Remove `SpeedLimitSign` composable.
6. New `RideAdviceBanner`; add short-message fields to `CoachingEvent.Type` + `ElevationService`; replace existing two banner composables.
7. Tests + manual QA pass.

Each step builds independently and the screen continues to function after each.
