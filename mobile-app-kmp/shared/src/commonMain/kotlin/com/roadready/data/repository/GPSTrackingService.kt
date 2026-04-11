package com.roadready.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private fun toRadians(deg: Double): Double = deg * PI / 180.0

// ── Data classes ────────────────────────────────────────────────────────────────

@Serializable
data class LocationUpdate(
    val latitude: Double,
    val longitude: Double,
    val speed: Float?,
    val timestamp: Long,
)

@Serializable
data class SpeedDataPoint(
    val timestamp: Long,
    val speed: Double,
    val latitude: Double,
    val longitude: Double,
)

data class Coordinate(val latitude: Double, val longitude: Double)

data class GPSTrackingState(
    val isTracking: Boolean = false,
    val location: LocationUpdate? = null,
    val speed: Double = 0.0,
    val routeCoordinates: List<Coordinate> = emptyList(),
    val distance: Double = 0.0,
    val speedData: List<SpeedDataPoint> = emptyList(),
)

// ── Platform expect ─────────────────────────────────────────────────────────────

expect class PlatformLocationProvider() {
    fun startTracking(onUpdate: (LocationUpdate) -> Unit)
    fun stopTracking()
}

// ── Haversine helper ────────────────────────────────────────────────────────────

private const val EARTH_RADIUS_KM = 6371.0

/** Distance in kilometres between two coordinates (Haversine formula). */
fun calculateDistanceKm(coord1: Coordinate, coord2: Coordinate): Double {
    val dLat = toRadians(coord2.latitude - coord1.latitude)
    val dLon = toRadians(coord2.longitude - coord1.longitude)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(toRadians(coord1.latitude)) *
        cos(toRadians(coord2.latitude)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_KM * c
}

// ── Service ─────────────────────────────────────────────────────────────────────

/**
 * Kotlin port of useGPSTracking.js.
 *
 * Thresholds and constants are identical to the React Native hook so that ride
 * telemetry is consistent across platforms.
 */
class GPSTrackingService(
    private val scope: CoroutineScope,
    private val locationProvider: PlatformLocationProvider,
    private val currentTimeMillis: () -> Long = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() },
) {
    // ── Constants (match JS hook exactly) ───────────────────────────────────────
    companion object {
        /** Minimum ms between reactive state updates (UI throttle). */
        const val GPS_STATE_THROTTLE_MS = 2000L
        /** Below this (km/h), GPS speed is treated as 0 to filter drift. */
        const val SPEED_DEAD_ZONE_KMH = 3.0
        /** Speed change (km/h) that bypasses render throttle (e.g. hard braking). */
        const val SPEED_CHANGE_THRESHOLD_KMH = 5.0
        /** If no GPS fix within this interval (ms), reset speed to 0. */
        const val STALE_SPEED_TIMEOUT_MS = 3000L
    }

    // ── Reactive state ──────────────────────────────────────────────────────────
    private val _state = MutableStateFlow(GPSTrackingState())
    val state: StateFlow<GPSTrackingState> = _state.asStateFlow()

    // ── Internal accumulators (full-rate, not throttled) ────────────────────────
    private val routeRef = mutableListOf<Coordinate>()
    private var distanceRef = 0.0
    private val speedDataRef = mutableListOf<SpeedDataPoint>()
    private var lastGpsStateUpdate = 0L
    private var lastReportedSpeed = 0.0
    private var lastGpsFix = 0L

    private var staleSpeedJob: Job? = null

    // ── Public API ──────────────────────────────────────────────────────────────

    fun startTracking() {
        clearData()

        locationProvider.startTracking { loc -> onLocationUpdate(loc) }

        // Staleness timer: reset speed to 0 when GPS callbacks stop firing
        staleSpeedJob = scope.launch {
            while (isActive) {
                delay(1000)
                val now = currentTimeMillis()
                if (lastGpsFix > 0 &&
                    now - lastGpsFix > STALE_SPEED_TIMEOUT_MS &&
                    lastReportedSpeed != 0.0
                ) {
                    lastReportedSpeed = 0.0
                    _state.value = _state.value.copy(speed = 0.0)
                }
            }
        }

        _state.value = _state.value.copy(isTracking = true)
    }

    fun stopTracking(): GPSTrackingState {
        locationProvider.stopTracking()
        staleSpeedJob?.cancel()
        staleSpeedJob = null

        val finalState = _state.value.copy(
            isTracking = false,
            routeCoordinates = routeRef.toList(),
            distance = distanceRef,
            speedData = speedDataRef.toList(),
        )
        _state.value = finalState
        return finalState
    }

    fun clearData() {
        routeRef.clear()
        distanceRef = 0.0
        speedDataRef.clear()
        lastGpsStateUpdate = 0L
        lastReportedSpeed = 0.0
        lastGpsFix = 0L
        _state.value = GPSTrackingState()
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    private fun onLocationUpdate(loc: LocationUpdate) {
        val timestamp = loc.timestamp
        val latitude = loc.latitude
        val longitude = loc.longitude

        // Dead-zone filter: GPS chipsets report phantom speeds from signal drift
        val rawKmh = if (loc.speed != null && loc.speed > 0) loc.speed * 3.6 else 0.0
        val speedKmh = if (rawKmh < SPEED_DEAD_ZONE_KMH) 0.0 else rawKmh

        lastGpsFix = timestamp

        // Always accumulate into refs at full rate
        val newCoordinate = Coordinate(latitude, longitude)
        routeRef.add(newCoordinate)

        // Distance on every fix
        if (routeRef.size > 1) {
            val prevCoord = routeRef[routeRef.size - 2]
            val segmentDistance = calculateDistanceKm(prevCoord, newCoordinate)
            distanceRef += segmentDistance
        }

        // Store speed data point
        speedDataRef.add(
            SpeedDataPoint(
                timestamp = timestamp,
                speed = speedKmh,
                latitude = latitude,
                longitude = longitude,
            ),
        )

        // Throttle state updates; bypass when speed changed significantly
        val now = currentTimeMillis()
        val speedDelta = abs(speedKmh - lastReportedSpeed)
        val withinThrottle = now - lastGpsStateUpdate < GPS_STATE_THROTTLE_MS
        if (withinThrottle && speedDelta < SPEED_CHANGE_THRESHOLD_KMH) return

        lastGpsStateUpdate = now
        lastReportedSpeed = speedKmh

        _state.value = _state.value.copy(
            location = loc,
            speed = speedKmh,
            routeCoordinates = routeRef.takeLast(200),
            distance = distanceRef,
            speedData = speedDataRef.takeLast(100),
        )
    }
}
