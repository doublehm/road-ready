package com.roadready.data.repository

import com.roadready.ml.HazardApproach
import com.roadready.ml.RoadConditionLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private fun toRadians(deg: Double): Double = deg * PI / 180.0

private const val EARTH_RADIUS_M = 6_371_000.0

/** Haversine distance in metres. */
private fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = toRadians(lat2 - lat1)
    val dLon = toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(toRadians(lat1)) * cos(toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** Project a point 1 second ahead along [headingDeg] at [speedMps]. */
private fun projectPoint(lat: Double, lon: Double, headingDeg: Double, speedMps: Double): Pair<Double, Double> {
    val d = speedMps  // 1 second of travel in metres
    val angDist = d / EARTH_RADIUS_M
    val bearing = toRadians(headingDeg)
    val lat1 = toRadians(lat)
    val lon1 = toRadians(lon)
    val lat2 = Math.asin(sin(lat1) * cos(angDist) + cos(lat1) * sin(angDist) * cos(bearing))
    val lon2 = lon1 + atan2(
        sin(bearing) * sin(angDist) * cos(lat1),
        cos(angDist) - sin(lat1) * sin(lat2),
    )
    return Pair(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

/**
 * 1 Hz trajectory-projection hazard approach detector.
 *
 * On each GPS tick, projects the vehicle's position [LOOK_AHEAD_SECONDS] ahead
 * and checks the local hazard cache for any confirmed segment within [warningMetres].
 * Emits a [HazardApproach] event — null when no hazard is close enough.
 *
 * Warning distances:
 *   - Highway speed (> 60 km/h): 200 m
 *   - City speed (≤ 60 km/h): 80 m
 *
 * A 60-second cooldown per hazard cell prevents alert fatigue.
 */
class HazardApproachDetector(
    private val repository: RoadConditionRepository,
) {
    companion object {
        const val HIGHWAY_SPEED_KMPH = 60.0
        const val HIGHWAY_WARNING_METRES = 200.0
        const val CITY_WARNING_METRES = 80.0
        const val COOLDOWN_MS = 60_000L
        const val LOOK_AHEAD_SECONDS = 3.0  // project this far ahead
    }

    private val _approach = MutableStateFlow<HazardApproach?>(null)
    val approach: StateFlow<HazardApproach?> = _approach.asStateFlow()

    private val lastAlertTime = mutableMapOf<String, Long>()

    /**
     * Call this on every GPS location update (1 Hz).
     *
     * @param lat       current latitude
     * @param lon       current longitude
     * @param speedKmh  current speed in km/h
     * @param headingDeg current compass heading in degrees (0 = North)
     * @param nowMs     current epoch milliseconds (injectable for testing)
     */
    fun onLocationUpdate(
        lat: Double,
        lon: Double,
        speedKmh: Double,
        headingDeg: Double,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val warningDist = if (speedKmh > HIGHWAY_SPEED_KMPH) HIGHWAY_WARNING_METRES else CITY_WARNING_METRES
        val speedMps = speedKmh / 3.6

        // Project ahead to where vehicle will be in LOOK_AHEAD_SECONDS
        val (projLat, projLon) = projectPoint(lat, lon, headingDeg, speedMps * LOOK_AHEAD_SECONDS)

        val hazards = repository.hazards
        if (hazards.isEmpty()) {
            _approach.value = null
            return
        }

        // Find the closest confirmed hazard within warning distance of projected position
        val nearest = hazards
            .filter { it.label != RoadConditionLabel.SMOOTH }
            .mapNotNull { hazard ->
                val d = distanceMetres(projLat, projLon, hazard.lat, hazard.lon)
                if (d <= warningDist) Pair(hazard, d) else null
            }
            .minByOrNull { it.second }

        if (nearest == null) {
            _approach.value = null
            return
        }

        val (hazard, distance) = nearest
        val lastAlert = lastAlertTime[hazard.h3Index] ?: 0L
        if (nowMs - lastAlert < COOLDOWN_MS) return  // still in cooldown

        lastAlertTime[hazard.h3Index] = nowMs
        _approach.value = HazardApproach(
            label = hazard.label,
            distanceMetres = distance,
            lat = hazard.lat,
            lon = hazard.lon,
        )
    }

    fun reset() {
        lastAlertTime.clear()
        _approach.value = null
    }
}
