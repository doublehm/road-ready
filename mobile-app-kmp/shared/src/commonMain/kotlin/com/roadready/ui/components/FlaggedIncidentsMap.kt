package com.roadready.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class ComplianceLevel { COMPLIANT, MARGINAL, SPEEDING }

data class SpeedSegment(
    val points: List<Pair<Double, Double>>,
    val compliance: ComplianceLevel,
    val avgSpeedKmh: Float = 0f,
    val limitKmh: Float = 0f,
)

data class RideIncident(
    val lat: Double,
    val lon: Double,
    val type: String,
    val severity: String,
    val description: String,
)

/**
 * Interactive map showing:
 *  - Route polyline color-coded by speed compliance (green=ok, amber=marginal, red=speeding)
 *  - Markers at every actual ride incident (harsh braking, speeding, sharp turns, etc.)
 *
 * Android: Google Maps Compose  |  iOS: stub placeholder
 */
@Composable
expect fun FlaggedIncidentsMap(
    segments: List<SpeedSegment>,
    incidents: List<RideIncident>,
    onIncidentTapped: (RideIncident) -> Unit,
    modifier: Modifier = Modifier,
)
