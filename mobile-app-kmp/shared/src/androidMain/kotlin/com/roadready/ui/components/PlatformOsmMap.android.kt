package com.roadready.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.roadready.ui.theme.Primary

/** Zoom level that keeps the driver's immediate context visible at each speed range. */
private fun zoomForSpeed(kmh: Double): Float = when {
    kmh < 20  -> 17.5f   // parking / very slow — maximum street detail
    kmh < 40  -> 16.5f   // urban residential
    kmh < 65  -> 15.5f   // arterial / city roads
    kmh < 90  -> 14.5f   // primary roads / regional
    kmh < 120 -> 13.5f   // highway
    else      -> 12.5f   // motorway
}

@Composable
actual fun PlatformOsmMap(
    coordinates: List<Pair<Double, Double>>,
    events: List<RouteEvent>,
    height: Dp,
    modifier: Modifier,
    followCurrentLocation: Boolean,
    speedKmh: Double,
) {
    val lastCoord = coordinates.lastOrNull()
    val center = remember(lastCoord) {
        if (lastCoord != null) LatLng(lastCoord.first, lastCoord.second)
        else LatLng(49.2827, -123.1207)
    }

    val targetZoom = zoomForSpeed(speedKmh)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, targetZoom)
    }

    // Follow current position and auto-scale zoom to speed
    LaunchedEffect(center, targetZoom) {
        if (followCurrentLocation) {
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(center)
                        .zoom(targetZoom)
                        .build()
                ),
                durationMs = 1200,
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = MapType.NORMAL,
                isMyLocationEnabled = true,
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false,
                mapToolbarEnabled = false,
            ),
        ) {
            if (coordinates.size > 1) {
                Polyline(
                    points = coordinates.map { LatLng(it.first, it.second) },
                    color = Primary,
                    width = 12f,
                )
                Marker(
                    state = MarkerState(position = LatLng(coordinates.first().first, coordinates.first().second)),
                    title = "Start",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                )
            }

            events.forEach { event ->
                if (event.lat != 0.0 && event.lng != 0.0) {
                    val hue = when (event.severity) {
                        "high", "road_hazard_high" -> BitmapDescriptorFactory.HUE_RED
                        "medium", "road_hazard_med" -> BitmapDescriptorFactory.HUE_ORANGE
                        else -> BitmapDescriptorFactory.HUE_YELLOW
                    }
                    Marker(
                        state = MarkerState(position = LatLng(event.lat, event.lng)),
                        title = event.type.replace("_", " ").uppercase(),
                        snippet = event.description,
                        icon = BitmapDescriptorFactory.defaultMarker(hue),
                    )
                }
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
