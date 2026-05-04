package com.roadready.ml

import kotlinx.serialization.Serializable

enum class RoadConditionLabel(val displayName: String) {
    SMOOTH("Smooth"),
    ROUGH("Rough road"),
    BUMP("Bump"),
    POTHOLE("Pothole"),
    SPEED_BUMP("Speed bump"),
    ;

    companion object {
        fun fromIndex(index: Int): RoadConditionLabel = entries.getOrElse(index) { SMOOTH }
        fun fromString(s: String): RoadConditionLabel = entries.firstOrNull {
            it.name.equals(s, ignoreCase = true)
        } ?: SMOOTH
    }
}

@Serializable
data class RoadConditionEvent(
    val lat: Double,
    val lon: Double,
    val speedKmh: Float,
    val label: String,
    val confidence: Float,
    val timestamp: Long,
)

data class HazardApproach(
    val label: RoadConditionLabel,
    val distanceMetres: Double,
    val lat: Double,
    val lon: Double,
)
