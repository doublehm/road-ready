package com.roadready.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.ui.theme.*
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.round

@Serializable
data class RouteCoordinate(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

@Serializable
data class RouteEvent(
    val type: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val severity: String = "medium",
    val description: String = "",
    val timestamp: Long = 0L,
)

/**
 * Placeholder route map component for Compose Multiplatform.
 *
 * Since native maps (Google Maps / MapKit) aren't available in KMP common code,
 * this shows route statistics (distance, duration, event counts) in a styled card.
 * A platform-specific map can be injected via `expect`/`actual` later.
 */
@Composable
fun RouteReplayMap(
    routeCoordinates: List<RouteCoordinate> = emptyList(),
    events: List<RouteEvent> = emptyList(),
    height: Dp = 300.dp,
    modifier: Modifier = Modifier,
) {
    val coordPairs = remember(routeCoordinates) {
        routeCoordinates.map { it.latitude to it.longitude }
    }

    val routeStats = remember(routeCoordinates, events) {
        computeRouteStats(routeCoordinates, events)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(height)) {
            // Render the real platform map
            PlatformOsmMap(
                coordinates = coordPairs,
                events = events,
                height = height,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
            )
            
            if (routeCoordinates.isEmpty() && events.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().background(SurfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No route data available", color = TextMuted, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stats bar below map
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RouteStatItem(
                label = "DISTANCE",
                value = "${formatDouble(routeStats.distanceKm, 2)} km",
                color = Primary,
            )
            RouteStatItem(
                label = "POINTS",
                value = routeStats.pointCount.toString(),
                color = AccentLight,
            )
            RouteStatItem(
                label = "EVENTS",
                value = events.size.toString(),
                color = if (events.isEmpty()) TextSecondary else Warning,
            )
        }
    }
}

@Composable
private fun RouteStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextMuted,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = color,
        )
    }
}

@Composable
private fun EventCountSummary(events: List<RouteEvent>) {
    val counts = remember(events) {
        events.groupBy { it.type }.mapValues { it.value.size }.toList()
            .sortedByDescending { it.second }
    }

    val eventMeta = mapOf(
        "speeding" to ("⚡" to Error),
        "harsh_braking" to ("✋" to Warning),
        "sharp_turn" to ("↻" to Warning),
        "sudden_stop" to ("⏹" to Error),
        "harsh_acceleration" to ("🚀" to Color(0xFFF97316)),
        "human_flag" to ("⚑" to Primary),
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((type, count) in counts.take(6)) {
            val (symbol, color) = eventMeta[type] ?: ("⚠" to TextSecondary)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(symbol, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = type.replace("_", " ").replaceFirstChar { it.uppercase() },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = count.toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
        }
    }
}

private data class RouteStats(
    val distanceKm: Double,
    val pointCount: Int,
)

private fun computeRouteStats(
    coords: List<RouteCoordinate>,
    events: List<RouteEvent>,
): RouteStats {
    var totalDistKm = 0.0
    for (i in 1 until coords.size) {
        totalDistKm += haversineKm(
            coords[i - 1].latitude, coords[i - 1].longitude,
            coords[i].latitude, coords[i].longitude,
        )
    }
    return RouteStats(
        distanceKm = totalDistKm,
        pointCount = coords.size,
    )
}

private fun formatDouble(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = round(value * factor) / factor
    val parts = rounded.toString().split(".")
    val intPart = parts[0]
    val fracPart = (parts.getOrElse(1) { "" }).take(decimals).padEnd(decimals, '0')
    return "$intPart.$fracPart"
}

private fun toRadians(deg: Double): Double = deg * kotlin.math.PI / 180.0

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = toRadians(lat2 - lat1)
    val dLon = toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(toRadians(lat1)) * kotlin.math.cos(toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return r * c
}
