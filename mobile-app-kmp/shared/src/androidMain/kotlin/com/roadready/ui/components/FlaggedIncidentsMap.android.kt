package com.roadready.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*

/** Returns a color appropriate for a given speed limit. */
private fun speedLimitColor(kmh: Int): Color = when {
    kmh <= 30  -> Color(0xFF22C55E)   // green  — school/playground zone
    kmh <= 50  -> Color(0xFF3B82F6)   // blue   — residential
    kmh <= 70  -> Color(0xFFF59E0B)   // amber  — secondary
    kmh <= 90  -> Color(0xFFF97316)   // orange — primary/trunk
    else       -> Color(0xFFEF4444)   // red    — motorway
}

@Composable
actual fun FlaggedIncidentsMap(
    segments: List<SpeedSegment>,
    flags: List<SpeedFlag>,
    onFlagTapped: (SpeedFlag) -> Unit,
    modifier: Modifier,
) {
    val allPoints = segments.flatMap { it.points }
    val center = remember(allPoints) {
        if (allPoints.isEmpty()) LatLng(49.2827, -123.1207)
        else {
            val latAvg = allPoints.map { it.first }.average()
            val lonAvg = allPoints.map { it.second }.average()
            LatLng(latAvg, lonAvg)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 14f)
    }

    // Auto-fit camera to all points on first load
    LaunchedEffect(allPoints) {
        if (allPoints.size > 1) {
            val bounds = LatLngBounds.builder().apply {
                allPoints.forEach { include(LatLng(it.first, it.second)) }
                flags.forEach { include(LatLng(it.lat, it.lon)) }
            }.build()
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(bounds, 80)
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.NORMAL),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                compassEnabled = true,
                mapToolbarEnabled = false,
            ),
        ) {
            // Color-coded route segments by speed limit
            segments.forEach { segment ->
                if (segment.points.size > 1) {
                    Polyline(
                        points = segment.points.map { LatLng(it.first, it.second) },
                        color = speedLimitColor(segment.speedLimitKmh),
                        width = 14f,
                        zIndex = 1f,
                    )
                }
            }

            // Flag markers — orange for pending, grey for corrected
            flags.forEach { flag ->
                val hue = when (flag.status) {
                    "corrected" -> BitmapDescriptorFactory.HUE_CYAN
                    else        -> BitmapDescriptorFactory.HUE_ORANGE
                }
                val reported = flag.reportedSpeedKmh?.let { "→ ${it.toInt()} km/h" } ?: ""
                Marker(
                    state = MarkerState(position = LatLng(flag.lat, flag.lon)),
                    title = "Speed Limit Flag",
                    snippet = "OSM: ${flag.osmSpeedKmh.toInt()} km/h  Observed: ${flag.observedSpeedKmh.toInt()} km/h  $reported".trim(),
                    icon = BitmapDescriptorFactory.defaultMarker(hue),
                    onClick = { onFlagTapped(flag); false },
                )
            }
        }

        Text(
            text = "© OpenStreetMap contributors",
            fontSize = 10.sp,
            color = Color.Gray.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
        )
    }
}
