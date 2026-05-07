package com.roadready.ui.screens.diagnostic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.roadready.ui.components.*
import com.roadready.ui.theme.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.koin.compose.koinInject
import kotlin.math.abs

private val jsonParser = Json { ignoreUnknownKeys = true }

// Speed limit point using the same lat/lon keys the phone stores
@Serializable
private data class LimitPoint(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    @SerialName("speed_limit") val speedLimit: Float = 50f,
)

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun parseSpeedPoints(raw: String?): List<SpeedDataPoint> {
    if (raw.isNullOrBlank()) return emptyList()
    return try { jsonParser.decodeFromString(raw) } catch (_: Exception) { emptyList() }
}

private fun parseLimitPoints(raw: String?): List<LimitPoint> {
    if (raw.isNullOrBlank()) return emptyList()
    return try { jsonParser.decodeFromString(raw) } catch (_: Exception) { emptyList() }
}

private fun parseEvalEvents(raw: String?): List<RouteEvent> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val obj = jsonParser.parseToJsonElement(raw).jsonObject
        val arr = obj["events"]?.jsonArray ?: return emptyList()
        jsonParser.decodeFromString(arr.toString())
    } catch (_: Exception) { emptyList() }
}

/**
 * For events stored without GPS (acceleration-based events before the backend fix),
 * resolve coordinates from the nearest speed_data point by timestamp.
 */
private fun resolveEventGps(events: List<RouteEvent>, speedPoints: List<SpeedDataPoint>): List<RouteEvent> {
    if (speedPoints.isEmpty()) return events
    return events.map { event ->
        if (event.lat != 0.0 && event.lng != 0.0) event
        else if (event.timestamp == 0L) event
        else {
            val closest = speedPoints.minByOrNull { abs(it.timestamp - event.timestamp) }
            if (closest != null) event.copy(lat = closest.lat, lng = closest.lon)
            else event
        }
    }
}

private fun nearestLimit(lat: Double, lon: Double, limits: List<LimitPoint>): Float {
    if (limits.isEmpty()) return 50f
    return limits.minByOrNull { pt ->
        val dlat = pt.lat - lat
        val dlon = pt.lon - lon
        dlat * dlat + dlon * dlon
    }?.speedLimit ?: 50f
}

private fun complianceLevel(speedKmh: Float, limitKmh: Float): ComplianceLevel = when {
    speedKmh <= limitKmh      -> ComplianceLevel.COMPLIANT
    speedKmh <= limitKmh + 10 -> ComplianceLevel.MARGINAL
    else                       -> ComplianceLevel.SPEEDING
}

/** Build compliance-colored segments from GPS speed points + OSM limit points. */
private fun buildSegments(
    speedPoints: List<SpeedDataPoint>,
    limitPoints: List<LimitPoint>,
): List<SpeedSegment> {
    if (speedPoints.size < 2) return emptyList()

    val segments = mutableListOf<SpeedSegment>()
    val first = speedPoints.first()
    var curLimit = nearestLimit(first.lat, first.lon, limitPoints)
    var curCompliance = complianceLevel(first.speed, curLimit)
    var curPts = mutableListOf(first.lat to first.lon)
    var speedSum = first.speed
    var ptCount = 1

    for (i in 1 until speedPoints.size) {
        val pt = speedPoints[i]
        val lim = nearestLimit(pt.lat, pt.lon, limitPoints)
        val comp = complianceLevel(pt.speed, lim)
        if (comp != curCompliance) {
            curPts.add(pt.lat to pt.lon)  // bridge point for visual continuity
            segments.add(SpeedSegment(curPts.toList(), curCompliance, speedSum / ptCount, curLimit))
            curPts = mutableListOf(pt.lat to pt.lon)
            curCompliance = comp
            curLimit = lim
            speedSum = pt.speed
            ptCount = 1
        } else {
            curPts.add(pt.lat to pt.lon)
            speedSum += pt.speed
            ptCount++
        }
    }
    if (curPts.size > 1) {
        segments.add(SpeedSegment(curPts.toList(), curCompliance, speedSum / ptCount, curLimit))
    }
    return segments
}

private fun improvementTip(type: String): String = when (type) {
    "speeding"             -> "Reduce speed to comply with posted limits. Speeding significantly increases collision risk."
    "harsh_braking"        -> "Increase following distance so you can brake gradually rather than suddenly."
    "sharp_turn"           -> "Slow down before entering curves — don't brake mid-corner where traction is limited."
    "sudden_stop"          -> "Maintain a safe gap so you can stop progressively without jarring passengers."
    "harsh_acceleration"   -> "Accelerate smoothly and progressively — avoid jackrabbit starts for safety and fuel economy."
    "erratic_speed"        -> "Maintain consistent speed. Unnecessary acceleration and deceleration waste fuel and unsettle traffic."
    "hard_acceleration"    -> "Build speed gradually. Smooth acceleration extends brake and tire life."
    "human_flag"           -> "Review the supervisor note and discuss with your instructor."
    else                   -> "Focus on smooth, anticipatory driving to improve your overall score."
}

private fun formatEventType(type: String): String =
    type.replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun FlaggedIncidentsScreen(
    rideId: Int,
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()

    var segments by remember { mutableStateOf<List<SpeedSegment>>(emptyList()) }
    var incidents by remember { mutableStateOf<List<RideIncident>>(emptyList()) }
    var selectedIncident by remember { mutableStateOf<RideIncident?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(rideId) {
        apiClient.getDiagnosticRide(rideId).onSuccess { ride ->
            val speedPoints = parseSpeedPoints(ride.speedData)
            val limitPoints = parseLimitPoints(ride.speedLimitData)
            segments = buildSegments(speedPoints, limitPoints)

            // Resolve GPS for events — acceleration-based events have no lat/lng
            // in older evaluations, so we cross-reference by timestamp with speed_data.
            val rawEvents = parseEvalEvents(ride.evaluationResult)
            val resolvedEvents = resolveEventGps(rawEvents, speedPoints)
            incidents = resolvedEvents
                .filter { it.lat != 0.0 && it.lng != 0.0 }
                .map { event ->
                    RideIncident(
                        lat = event.lat,
                        lon = event.lng,
                        type = event.type,
                        severity = event.severity,
                        description = event.description,
                    )
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
            FlaggedIncidentsMap(
                segments = segments,
                incidents = incidents,
                onIncidentTapped = { selectedIncident = it },
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

            // Compliance legend (top-right)
            ComplianceLegend(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 12.dp),
            )

            // Incident count pill
            if (incidents.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Warning.copy(alpha = 0.92f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        "${incidents.size} incident${if (incidents.size != 1) "s" else ""} flagged",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                }
            }
        }

        // Incident detail card — slides up when a marker is tapped
        AnimatedVisibility(
            visible = selectedIncident != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            selectedIncident?.let { incident ->
                IncidentDetailCard(
                    incident = incident,
                    onDismiss = { selectedIncident = null },
                )
            }
        }
    }
}

@Composable
private fun ComplianceLegend(modifier: Modifier = Modifier) {
    val entries = listOf(
        Color(0xFF22C55E) to "Compliant",
        Color(0xFFF59E0B) to "1–10 over",
        Color(0xFFEF4444) to "10+ over",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Background.copy(alpha = 0.88f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "SPEED LIMIT",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
            )
            entries.forEach { (color, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(label, fontSize = 10.sp, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun IncidentDetailCard(incident: RideIncident, onDismiss: () -> Unit) {
    val severityColor = when (incident.severity) {
        "high", "critical" -> Error
        "medium"           -> Warning
        else               -> Color(0xFF3B82F6)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
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
                    formatEventType(incident.type),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = TextPrimary,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(severityColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        incident.severity.replaceFirstChar { it.uppercase() },
                        fontSize = 11.sp,
                        color = severityColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (incident.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    incident.description,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    lineHeight = 19.sp,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Tip: ${improvementTip(incident.type)}",
                fontSize = 12.sp,
                color = TextMuted,
                lineHeight = 18.sp,
            )

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
