package com.roadready.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class SpeedSegment(
    val points: List<Pair<Double, Double>>,
    val speedLimitKmh: Int,
)

data class SpeedFlag(
    val id: Int,
    val lat: Double,
    val lon: Double,
    val osmSpeedKmh: Double,
    val observedSpeedKmh: Double,
    val reportedSpeedKmh: Double?,
    val status: String,
)

/**
 * Interactive map that renders:
 *  - Route polyline color-coded by speed limit (green → red)
 *  - Flag markers for crowdsourced speed limit discrepancy reports
 *
 * Android: Google Maps Compose  |  iOS: stub placeholder
 */
@Composable
expect fun FlaggedIncidentsMap(
    segments: List<SpeedSegment>,
    flags: List<SpeedFlag>,
    onFlagTapped: (SpeedFlag) -> Unit,
    modifier: Modifier = Modifier,
)
