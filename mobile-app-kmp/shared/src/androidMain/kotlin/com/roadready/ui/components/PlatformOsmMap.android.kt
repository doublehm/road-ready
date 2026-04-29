package com.roadready.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.roadready.ui.theme.Primary
import com.roadready.ui.theme.Secondary
import com.roadready.ui.theme.Error
import com.roadready.ui.theme.Warning

@Composable
actual fun PlatformOsmMap(
    coordinates: List<Pair<Double, Double>>,
    events: List<RouteEvent>,
    height: Dp,
    modifier: Modifier,
    followCurrentLocation: Boolean,
) {
    val center = remember(coordinates) {
        if (coordinates.isNotEmpty()) {
            LatLng(coordinates.last().first, coordinates.last().second)
        } else {
            LatLng(45.4215, -75.6972) // Default to Ottawa or similar
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 15f)
    }

    // Auto-zoom to fit route
    LaunchedEffect(coordinates) {
        if (coordinates.size > 5) {
            val builder = LatLngBounds.Builder()
            coordinates.forEach { builder.include(LatLng(it.first, it.second)) }
            // Note: CameraUpdateFactory requires the view to be laid out, 
            // maps-compose handles some of this but we might need a small delay or check
        } else if (coordinates.isNotEmpty()) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(coordinates.last().first, coordinates.last().second), 
                cameraPositionState.position.zoom.coerceAtLeast(15f)
            )
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxWidth().height(height),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = true,
            // Custom styling for dark mode could be added here via MapStyleOptions
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false
        )
    ) {
        // Draw Route
        if (coordinates.size > 1) {
            Polyline(
                points = coordinates.map { LatLng(it.first, it.second) },
                color = Primary,
                width = 12f
            )
            
            // Start Marker
            Marker(
                state = MarkerState(position = LatLng(coordinates.first().first, coordinates.first().second)),
                title = "Start",
                icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_GREEN)
            )
        }

        // Draw Events
        events.forEach { event ->
            if (event.lat != 0.0 && event.lng != 0.0) {
                val hue = when (event.severity) {
                    "high" -> com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED
                    "medium" -> com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_ORANGE
                    else -> com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_BLUE
                }
                
                Marker(
                    state = MarkerState(position = LatLng(event.lat, event.lng)),
                    title = event.type.replace("_", " ").uppercase(),
                    snippet = event.description,
                    icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(hue)
                )
            }
        }
    }
}
