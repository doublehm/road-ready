package com.roadready.ui.screens.diagnostic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.data.remote.ApiClient
import com.roadready.data.repository.SpeedLimitDataPoint
import com.roadready.data.repository.SpeedLimitFlagResponse
import com.roadready.ui.components.*
import com.roadready.ui.theme.*
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.roundToInt

private val jsonParser = Json { ignoreUnknownKeys = true }

// Speed limit → hex color (string for use in legend)
private fun limitColor(kmh: Int): Color = when {
    kmh <= 30  -> Color(0xFF22C55E)
    kmh <= 50  -> Color(0xFF3B82F6)
    kmh <= 70  -> Color(0xFFF59E0B)
    kmh <= 90  -> Color(0xFFF97316)
    else       -> Color(0xFFEF4444)
}

private fun limitLabel(kmh: Int): String = when {
    kmh <= 30  -> "≤30 km/h"
    kmh <= 50  -> "31–50 km/h"
    kmh <= 70  -> "51–70 km/h"
    kmh <= 90  -> "71–90 km/h"
    else       -> "91+ km/h"
}

/** Build color-coded segments from a list of speed limit data points + route coordinates. */
private fun buildSegments(
    routeCoords: List<Pair<Double, Double>>,
    speedLimitPoints: List<SpeedLimitDataPoint>,
): List<SpeedSegment> {
    if (routeCoords.isEmpty()) return emptyList()
    if (speedLimitPoints.isEmpty()) {
        return listOf(SpeedSegment(routeCoords, 50))
    }

    fun nearestLimit(lat: Double, lon: Double): Int {
        var best = speedLimitPoints.first()
        var bestDist = Double.MAX_VALUE
        speedLimitPoints.forEach { pt ->
            val d = abs(pt.latitude - lat) + abs(pt.longitude - lon)
            if (d < bestDist) { bestDist = d; best = pt }
        }
        return best.speedLimit.roundToInt()
    }

    val segments = mutableListOf<SpeedSegment>()
    var currentLimit = nearestLimit(routeCoords.first().first, routeCoords.first().second)
    var currentPoints = mutableListOf(routeCoords.first())

    for (i in 1 until routeCoords.size) {
        val coord = routeCoords[i]
        val limit = nearestLimit(coord.first, coord.second)
        if (limit != currentLimit) {
            currentPoints.add(coord)   // bridge point for visual continuity
            segments.add(SpeedSegment(currentPoints.toList(), currentLimit))
            currentPoints = mutableListOf(coord)
            currentLimit = limit
        } else {
            currentPoints.add(coord)
        }
    }
    if (currentPoints.size > 1) {
        segments.add(SpeedSegment(currentPoints.toList(), currentLimit))
    }
    return segments
}

/**
 * Full-screen map showing:
 *  - Route color-coded by speed limit
 *  - Crowdsourced speed limit flag markers
 *  - Tappable flag detail card at the bottom
 */
@Composable
fun FlaggedIncidentsScreen(
    rideId: Int,
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val scope = rememberCoroutineScope()

    var segments by remember { mutableStateOf<List<SpeedSegment>>(emptyList()) }
    var flags by remember { mutableStateOf<List<SpeedFlag>>(emptyList()) }
    var selectedFlag by remember { mutableStateOf<SpeedFlag?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var centerLat by remember { mutableStateOf(49.2827) }
    var centerLon by remember { mutableStateOf(-123.1207) }

    LaunchedEffect(rideId) {
        val rideResult = apiClient.getDiagnosticRide(rideId)
        rideResult.onSuccess { ride ->
            // Parse route
            val routeCoords: List<Pair<Double, Double>> = try {
                jsonParser.decodeFromString<List<RouteCoordinate>>(ride.routeCoords ?: "[]")
                    .map { it.latitude to it.longitude }
            } catch (_: Exception) { emptyList() }

            // Parse speed limit data
            val speedLimitPoints: List<SpeedLimitDataPoint> = try {
                jsonParser.decodeFromString<List<SpeedLimitDataPoint>>(ride.speedLimitData ?: "[]")
            } catch (_: Exception) { emptyList() }

            segments = buildSegments(routeCoords, speedLimitPoints)

            if (routeCoords.isNotEmpty()) {
                centerLat = routeCoords.map { it.first }.average()
                centerLon = routeCoords.map { it.second }.average()

                // Fetch nearby flags around the route center
                apiClient.getNearbyFlags(centerLat, centerLon).onSuccess { flagList ->
                    flags = flagList.map { f ->
                        SpeedFlag(
                            id = f.id,
                            lat = f.lat,
                            lon = f.lon,
                            osmSpeedKmh = f.osmSpeedKmh,
                            observedSpeedKmh = f.observedSpeedKmh,
                            reportedSpeedKmh = f.reportedSpeedKmh,
                            status = f.status,
                        )
                    }
                }
            }
        }
        isLoading = false
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Primary,
            )
        } else {
            // Full-screen map
            FlaggedIncidentsMap(
                segments = segments,
                flags = flags,
                onFlagTapped = { selectedFlag = it },
                modifier = Modifier.fillMaxSize(),
            )

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 12.dp, end = 16.dp)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Background.copy(alpha = 0.88f)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Background.copy(alpha = 0.88f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        "Route Analysis Map",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary,
                    )
                }
            }

            // Speed limit legend
            SpeedLimitLegend(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 12.dp),
            )

            // Flag count pill
            if (flags.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Warning.copy(alpha = 0.92f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        "${flags.size} speed limit flag${if (flags.size != 1) "s" else ""} in this area",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                }
            }
        }

        // Flag detail card — slides up from bottom
        AnimatedVisibility(
            visible = selectedFlag != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            selectedFlag?.let { flag ->
                FlagDetailCard(
                    flag = flag,
                    onDismiss = { selectedFlag = null },
                )
            }
        }
    }
}

@Composable
private fun SpeedLimitLegend(modifier: Modifier = Modifier) {
    val entries = listOf(
        Color(0xFF22C55E) to "≤30",
        Color(0xFF3B82F6) to "31–50",
        Color(0xFFF59E0B) to "51–70",
        Color(0xFFF97316) to "71–90",
        Color(0xFFEF4444) to "91+",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Background.copy(alpha = 0.88f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SPEED LIMIT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
            entries.forEach { (color, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("$label km/h", fontSize = 10.sp, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun FlagDetailCard(flag: SpeedFlag, onDismiss: () -> Unit) {
    val statusColor = if (flag.status == "corrected") Color(0xFF22C55E) else Warning
    val statusLabel = if (flag.status == "corrected") "Corrected in OSM" else "Pending Review"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Speed Limit Flag",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = TextPrimary,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(statusLabel, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FlagStat("OSM Limit", "${flag.osmSpeedKmh.toInt()} km/h", Error)
                FlagStat("Observed Speed", "${flag.observedSpeedKmh.toInt()} km/h", Warning)
                flag.reportedSpeedKmh?.let {
                    FlagStat("Reported Limit", "${it.toInt()} km/h", Color(0xFF22C55E))
                }
            }

            if (flag.status == "pending") {
                Spacer(Modifier.height(12.dp))
                Text(
                    "This area has been flagged for a possible speed limit error. " +
                    "When 5 or more drivers confirm this discrepancy, the map data will be corrected automatically.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 18.sp,
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    "This speed limit discrepancy has been confirmed and the OpenStreetMap data has been updated.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 18.sp,
                )
            }

            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Dismiss", color = Primary)
            }
        }
    }
}

@Composable
private fun FlagStat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = valueColor)
        Text(label, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Medium)
    }
}
