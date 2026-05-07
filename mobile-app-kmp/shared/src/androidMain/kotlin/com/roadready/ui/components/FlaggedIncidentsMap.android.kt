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

private fun complianceColor(level: ComplianceLevel): Color = when (level) {
    ComplianceLevel.COMPLIANT -> Color(0xFF22C55E)   // green  — at or under the limit
    ComplianceLevel.MARGINAL  -> Color(0xFFF59E0B)   // amber  — 1–10 km/h over
    ComplianceLevel.SPEEDING  -> Color(0xFFEF4444)   // red    — 10+ km/h over
}

private fun incidentMarkerHue(type: String, severity: String): Float = when {
    type == "speeding" || type == "sudden_stop" -> BitmapDescriptorFactory.HUE_RED
    type == "harsh_braking" || type == "harsh_acceleration" -> BitmapDescriptorFactory.HUE_ORANGE
    severity == "high" || severity == "critical" -> BitmapDescriptorFactory.HUE_RED
    else -> BitmapDescriptorFactory.HUE_YELLOW
}

@Composable
actual fun FlaggedIncidentsMap(
    segments: List<SpeedSegment>,
    incidents: List<RideIncident>,
    onIncidentTapped: (RideIncident) -> Unit,
    modifier: Modifier,
) {
    val allPoints = segments.flatMap { it.points }
    val center = remember(allPoints) {
        if (allPoints.isEmpty()) LatLng(49.2827, -123.1207)
        else LatLng(allPoints.map { it.first }.average(), allPoints.map { it.second }.average())
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 14f)
    }

    LaunchedEffect(allPoints) {
        if (allPoints.size > 1) {
            val builder = LatLngBounds.builder()
            allPoints.forEach { builder.include(LatLng(it.first, it.second)) }
            incidents.forEach { builder.include(LatLng(it.lat, it.lon)) }
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(builder.build(), 80)
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
            // Compliance-colored route polyline
            segments.forEach { segment ->
                if (segment.points.size > 1) {
                    Polyline(
                        points = segment.points.map { LatLng(it.first, it.second) },
                        color = complianceColor(segment.compliance),
                        width = 14f,
                        zIndex = 1f,
                    )
                }
            }

            // Incident markers from actual ride events
            incidents.forEach { incident ->
                Marker(
                    state = MarkerState(position = LatLng(incident.lat, incident.lon)),
                    title = incident.type.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    snippet = incident.description,
                    icon = BitmapDescriptorFactory.defaultMarker(
                        incidentMarkerHue(incident.type, incident.severity)
                    ),
                    onClick = { onIncidentTapped(incident); false },
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
