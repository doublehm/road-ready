package com.roadready.data.repository

import com.roadready.data.remote.ApiClient
import com.roadready.ml.RoadConditionEvent
import com.roadready.ml.RoadConditionLabel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── API response types ───────────────────────────────────────────────────────────

@Serializable
data class NearbyHazard(
    @SerialName("h3_index") val h3Index: String,
    val lat: Double,
    val lon: Double,
    val label: String,
    val confidence: Float,
    @SerialName("device_count") val deviceCount: Int,
)

@Serializable
data class NearbyHazardsResponse(
    val hazards: List<NearbyHazard>,
    val count: Int,
)

@Serializable
data class TripReportRequest(
    @SerialName("ride_id") val rideId: String,
    val events: List<RoadConditionEvent>,
)

@Serializable
data class TripReportResponse(val accepted: Int)

// ── Local cache entry ────────────────────────────────────────────────────────────

data class CachedHazard(
    val h3Index: String,
    val lat: Double,
    val lon: Double,
    val label: RoadConditionLabel,
    val confidence: Float,
)

/**
 * Fetches confirmed road hazards from the backend at trip start and keeps them
 * in an in-memory cache for offline Haversine proximity checks during the ride.
 *
 * Cache is scoped to a single trip — call [clear] at trip end.
 */
class RoadConditionRepository(private val apiClient: ApiClient) {

    private val _hazards = mutableListOf<CachedHazard>()

    /** Snapshot of the current hazard cache. */
    val hazards: List<CachedHazard> get() = _hazards.toList()

    /**
     * Prefetch confirmed hazards within [radiusMetres] of the given coordinates.
     * Populates the local cache — call once at trip start.
     */
    suspend fun prefetchHazards(lat: Double, lon: Double, radiusMetres: Double = 5000.0) {
        val result = apiClient.getNearbyHazards(lat, lon, radiusMetres)
        result.onSuccess { response ->
            _hazards.clear()
            _hazards.addAll(response.hazards.map { h ->
                CachedHazard(
                    h3Index    = h.h3Index,
                    lat        = h.lat,
                    lon        = h.lon,
                    label      = RoadConditionLabel.fromString(h.label),
                    confidence = h.confidence,
                )
            })
        }
    }

    /**
     * Upload a batch of classified road condition events from a completed trip.
     * Returns the number of events accepted by the backend.
     */
    suspend fun uploadTrip(rideId: String, events: List<RoadConditionEvent>): Int {
        if (events.isEmpty()) return 0
        val result = apiClient.reportRoadConditions(rideId, events)
        return result.getOrNull()?.accepted ?: 0
    }

    fun clear() {
        _hazards.clear()
    }
}
