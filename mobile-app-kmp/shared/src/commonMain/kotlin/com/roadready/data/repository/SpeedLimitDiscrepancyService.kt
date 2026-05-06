package com.roadready.data.repository

import com.roadready.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class SpeedLimitFlagRequest(
    val lat: Double,
    val lon: Double,
    @SerialName("osm_speed_kmh") val osmSpeedKmh: Double,
    @SerialName("observed_speed_kmh") val observedSpeedKmh: Double,
    @SerialName("reported_speed_kmh") val reportedSpeedKmh: Double? = null,
)

@Serializable
data class SpeedLimitFlagResponse(
    val id: Int,
    val lat: Double,
    val lon: Double,
    @SerialName("h3_cell") val h3Cell: String,
    @SerialName("osm_speed_kmh") val osmSpeedKmh: Double,
    @SerialName("observed_speed_kmh") val observedSpeedKmh: Double,
    @SerialName("reported_speed_kmh") val reportedSpeedKmh: Double? = null,
    val status: String,
)

data class DiscrepancyState(
    val isVisible: Boolean = false,
    val osmSpeedKmh: Double = 0.0,
    val observedSpeedKmh: Double = 0.0,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
)

/**
 * Detects sustained speed vs OSM speed limit discrepancies that may indicate
 * incorrect map data rather than driver speeding.
 *
 * Logic:
 *  - |observed - osm| ≥ DISCREPANCY_THRESHOLD for SUSPICIOUS_DURATION_MS → show dialog
 *  - Only triggers when speed > MIN_SPEED_KMH (avoids traffic-stop false positives)
 *  - 120-second cooldown per prompt to avoid pestering the driver
 */
class SpeedLimitDiscrepancyService(private val apiClient: ApiClient) {
    companion object {
        private const val DISCREPANCY_THRESHOLD_KMH = 25.0
        private const val SUSPICIOUS_DURATION_MS = 10_000L
        private const val COOLDOWN_MS = 120_000L
        private const val MIN_SPEED_KMH = 15.0
    }

    private val _discrepancy = MutableStateFlow(DiscrepancyState())
    val discrepancy: StateFlow<DiscrepancyState> = _discrepancy.asStateFlow()

    private var suspiciousStartMs = 0L
    private var lastPromptMs = 0L

    fun onSpeedAndLimit(
        speedKmh: Double,
        osmLimitKmh: Double,
        lat: Double,
        lon: Double,
        nowMs: Long,
    ) {
        if (speedKmh < MIN_SPEED_KMH) {
            suspiciousStartMs = 0L
            return
        }
        if (abs(speedKmh - osmLimitKmh) >= DISCREPANCY_THRESHOLD_KMH) {
            if (suspiciousStartMs == 0L) suspiciousStartMs = nowMs
            val elapsed = nowMs - suspiciousStartMs
            val cooldownPassed = nowMs - lastPromptMs >= COOLDOWN_MS
            if (elapsed >= SUSPICIOUS_DURATION_MS && cooldownPassed && !_discrepancy.value.isVisible) {
                _discrepancy.value = DiscrepancyState(
                    isVisible = true,
                    osmSpeedKmh = osmLimitKmh,
                    observedSpeedKmh = speedKmh,
                    lat = lat,
                    lon = lon,
                )
                lastPromptMs = nowMs
            }
        } else {
            suspiciousStartMs = 0L
        }
    }

    fun dismiss() {
        _discrepancy.value = _discrepancy.value.copy(isVisible = false)
        suspiciousStartMs = 0L
    }

    suspend fun submitFlag(reportedSpeedKmh: Double?) {
        val d = _discrepancy.value
        if (!d.isVisible) return
        apiClient.flagSpeedLimit(
            SpeedLimitFlagRequest(
                lat = d.lat,
                lon = d.lon,
                osmSpeedKmh = d.osmSpeedKmh,
                observedSpeedKmh = d.observedSpeedKmh,
                reportedSpeedKmh = reportedSpeedKmh,
            )
        )
        dismiss()
    }
}
