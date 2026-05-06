package com.roadready.data.repository

import com.roadready.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class ElevationResponse(
    @SerialName("elevation_m") val elevationM: Double? = null,
    @SerialName("grade_pct") val gradePct: Double? = null,
    val category: String = "unknown",
    val tips: List<String> = emptyList(),
)

data class ElevationState(
    val elevationM: Double? = null,
    val gradePct: Double? = null,
    val category: String = "flat",
    val terrainTip: String? = null,
)

/**
 * Polls the backend elevation endpoint every ~100 m of travel.
 * Emits [ElevationState] with the current grade and an optional driving tip.
 */
class ElevationService(private val apiClient: ApiClient) {
    companion object {
        private const val QUERY_DISTANCE_THRESHOLD_M = 100.0
        private const val GRADE_TIP_THRESHOLD_PCT = 2.0
    }

    private val _state = MutableStateFlow(ElevationState())
    val state: StateFlow<ElevationState> = _state.asStateFlow()

    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var prevLat: Double? = null
    private var prevLon: Double? = null

    suspend fun onLocationChanged(lat: Double, lon: Double) {
        val prev = lastLat?.let { pLat -> lastLon?.let { pLon -> pLat to pLon } }
        if (prev != null && haversineM(prev.first, prev.second, lat, lon) < QUERY_DISTANCE_THRESHOLD_M) {
            return
        }
        prevLat = lastLat
        prevLon = lastLon
        lastLat = lat
        lastLon = lon

        val p = prevLat?.let { pLat -> prevLon?.let { pLon -> pLat to pLon } } ?: return

        apiClient.getElevation(lat, lon, p.first, p.second).onSuccess { response ->
            val gradePct = response.gradePct ?: 0.0
            val tip = if (response.tips.isNotEmpty() && abs(gradePct) >= GRADE_TIP_THRESHOLD_PCT)
                response.tips.first()
            else null
            _state.value = ElevationState(
                elevationM = response.elevationM,
                gradePct = response.gradePct,
                category = response.category,
                terrainTip = tip,
            )
        }
    }

    fun clearTip() {
        _state.value = _state.value.copy(terrainTip = null)
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6_371_000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLam = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLam / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
