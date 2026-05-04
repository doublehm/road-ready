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
    val lastCoord = coordinates.lastOrNull()
    val center = remember(lastCoord) {
        if (lastCoord != null) {
            LatLng(lastCoord.first, lastCoord.second)
        } else {
            LatLng(49.2827, -123.1207) // Default to Vancouver
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 17f)
    }

    // Smoothly follow the current location — ZERO COST Native API
    LaunchedEffect(center) {
        if (followCurrentLocation) {
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLng(center),
                1000
            )
        }
    }

    val osmTileProvider = remember {
        object : UrlTileProvider(256, 256) {
            override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
                return try {
                    URL("https://tile.openstreetmap.org/$zoom/$x/$y.png")
                } catch (e: Exception) {
                    null
                }
            }
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
                mapToolbarEnabled = false
            )
        ) {
            // Optional OSM Overlay
            TileOverlay(tileProvider = osmTileProvider, transparency = 0.3f)

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

        // OSM Attribution
        Text(
            text = "© OpenStreetMap contributors",
            fontSize = 10.sp,
            color = Color.Gray.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )
    }
}
