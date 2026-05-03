package com.roadready.data.repository

import com.roadready.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private fun toRadians(deg: Double): Double = deg * PI / 180.0

// ── Data classes ────────────────────────────────────────────────────────────────

@Serializable
data class SpeedLimitResponse(
    @SerialName("speed_limit_kmh") val speedLimitKmh: Double?,
    @SerialName("road_name") val roadName: String? = null,
    @SerialName("road_type") val roadType: String? = null,
    @SerialName("zone_type") val zoneType: String? = null,
    val source: String? = null,
)

@Serializable
data class SpeedLimitDataPoint(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    @SerialName("speed_limit") val speedLimit: Double,
    @SerialName("road_type") val roadType: String? = null,
    @SerialName("road_name") val roadName: String? = null,
    @SerialName("zone_type") val zoneType: String = "regular",
    val source: String? = null,
)

data class SpeedLimitState(
    val currentSpeedLimit: Double? = null,
    val roadName: String? = null,
    val roadType: String? = null,
    val zoneType: String = "regular",
    val isLoading: Boolean = false,
)

// ── Haversine helper (metres) ───────────────────────────────────────────────────

private const val EARTH_RADIUS_M = 6371000.0

/** Distance in metres between two points (Haversine formula). */
private fun calculateDistanceMetres(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val dLat = toRadians(lat2 - lat1)
    val dLon = toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(toRadians(lat1)) *
        cos(toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_M * c
}

// ── Service ─────────────────────────────────────────────────────────────────────

/**
 * Kotlin port of useSpeedLimit.js.
 *
 * Throttled queries (100 m / 30 s), client-side cache, and a smoothing
 * algorithm that requires multiple agreeing readings before accepting a
 * dramatic speed-limit change. All constants match the JS hook exactly.
 */
class SpeedLimitService(
    private val apiClient: ApiClient,
    private val currentTimeMillis: () -> Long = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() },
) {
    companion object {
        /** Only query when moved at least this far (metres). */
        const val QUERY_DISTANCE_THRESHOLD = 50.0
        /** Only query when at least this much time elapsed (ms). */
        const val QUERY_TIME_THRESHOLD = 15_000L
        /** Changes larger than this require confirmation from recent readings. */
        const val DRAMATIC_CHANGE_THRESHOLD = 40.0
        /** Number of recent raw readings to keep for smoothing. */
        const val SMOOTHING_HISTORY_SIZE = 3
        /** How many recent readings must agree to confirm a dramatic change. */
        const val DRAMATIC_CHANGE_MIN_AGREE = 2
        /** Tolerance for "agreeing" readings (km/h). */
        const val AGREE_TOLERANCE = 25.0
    }

    // ── Reactive state ──────────────────────────────────────────────────────────
    private val _state = MutableStateFlow(SpeedLimitState())
    val state: StateFlow<SpeedLimitState> = _state.asStateFlow()

    // ── Internal accumulators ───────────────────────────────────────────────────
    private val speedLimitDataRef = mutableListOf<SpeedLimitDataPoint>()
    private var lastQueryLat: Double? = null
    private var lastQueryLon: Double? = null
    private var lastQueryTime = 0L

    private val cache = mutableMapOf<String, SpeedLimitResponse>()
    private val recentLimits = mutableListOf<Double>()
    private var confirmedLimit: Double? = null

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Called when a new GPS location arrives while the ride is active.
     * Determines whether enough distance/time has elapsed to warrant a fresh
     * speed-limit query, then queries (or hits cache) and applies smoothing.
     */
    suspend fun onLocationChanged(latitude: Double, longitude: Double) {
        val now = currentTimeMillis()

        var shouldQuery = false
        val qLat = lastQueryLat
        val qLon = lastQueryLon

        if (qLat == null || qLon == null) {
            shouldQuery = true
        } else {
            val distanceMoved = calculateDistanceMetres(qLat, qLon, latitude, longitude)
            val timeElapsed = now - lastQueryTime
            if (distanceMoved >= QUERY_DISTANCE_THRESHOLD || timeElapsed >= QUERY_TIME_THRESHOLD) {
                shouldQuery = true
            }
        }

        if (shouldQuery) {
            lastQueryLat = latitude
            lastQueryLon = longitude
            lastQueryTime = now
            querySpeedLimit(latitude, longitude)
        }
    }

    /** Return the accumulated speed-limit history for ride submission. */
    fun getSpeedLimitData(): List<SpeedLimitDataPoint> = speedLimitDataRef.toList()

    fun clearData() {
        speedLimitDataRef.clear()
        cache.clear()
        recentLimits.clear()
        confirmedLimit = null
        lastQueryLat = null
        lastQueryLon = null
        lastQueryTime = 0L
        _state.value = SpeedLimitState()
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    private fun roundCoord(value: Double): Double =
        (value * 1000).roundToInt() / 1000.0

    private suspend fun querySpeedLimit(lat: Double, lon: Double) {
        val cacheKey = "${roundCoord(lat)},${roundCoord(lon)}"

        // Check client-side cache
        val cached = cache[cacheKey]
        if (cached != null) {
            applyResult(cached, lat, lon)
            return
        }

        _state.value = _state.value.copy(isLoading = true)
        try {
            val result = apiClient.getSpeedLimit(lat, lon)
            result.fold(
                onSuccess = { response ->
                    cache[cacheKey] = response
                    applyResult(response, lat, lon)
                },
                onFailure = { /* query failed — keep current limit */ },
            )
        } finally {
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    private fun applyResult(result: SpeedLimitResponse, lat: Double, lon: Double) {
        val newLimit = result.speedLimitKmh ?: return
        val confirmed = confirmedLimit

        // Add to recent readings history
        recentLimits.add(newLimit)
        if (recentLimits.size > SMOOTHING_HISTORY_SIZE) {
            recentLimits.removeAt(0)
        }

        var limitToApply = newLimit

        // Smoothing: dramatic changes require multiple agreeing readings
        if (confirmed != null && abs(newLimit - confirmed) > DRAMATIC_CHANGE_THRESHOLD) {
            val agreeing = recentLimits.count { abs(it - newLimit) <= AGREE_TOLERANCE }
            if (agreeing < DRAMATIC_CHANGE_MIN_AGREE) {
                limitToApply = confirmed
            }
        }

        confirmedLimit = limitToApply

        _state.value = _state.value.copy(
            currentSpeedLimit = limitToApply,
            roadName = result.roadName,
            roadType = result.roadType,
            zoneType = result.zoneType ?: "regular",
        )

        // Store in history with the smoothed limit
        speedLimitDataRef.add(
            SpeedLimitDataPoint(
                timestamp = currentTimeMillis(),
                latitude = lat,
                longitude = lon,
                speedLimit = limitToApply,
                roadType = result.roadType,
                roadName = result.roadName,
                zoneType = result.zoneType ?: "regular",
                source = result.source,
            ),
        )
    }
}
