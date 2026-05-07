package com.roadready.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Platform-specific map composable using OSM tiles.
 * Android: WebView + Leaflet.js
 * iOS: stub placeholder
 */
@Composable
expect fun PlatformOsmMap(
    coordinates: List<Pair<Double, Double>> = emptyList(),
    events: List<RouteEvent> = emptyList(),
    height: Dp = 300.dp,
    modifier: Modifier = Modifier,
    followCurrentLocation: Boolean = false,
    speedKmh: Double = 0.0,
)
