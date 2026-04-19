package com.roadready.ui.screens.diagnostic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.data.model.DiagnosticRide
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.*
import com.roadready.ui.theme.*
import com.roadready.ui.util.fmtDouble
import kotlinx.serialization.json.*
import org.koin.compose.koinInject
import kotlin.math.roundToInt

private val jsonParser = Json { ignoreUnknownKeys = true }

// ── JSON parsing helpers ────────────────────────────────────────────

private fun parseAccelData(raw: String?): List<AccelDataPoint> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        jsonParser.decodeFromString<List<AccelDataPoint>>(raw)
    } catch (_: Exception) { emptyList() }
}

private fun parseRotationData(raw: String?): List<RotationDataPoint> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        jsonParser.decodeFromString<List<RotationDataPoint>>(raw)
    } catch (_: Exception) { emptyList() }
}

private fun parseRouteCoords(raw: String?): List<RouteCoordinate> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        jsonParser.decodeFromString<List<RouteCoordinate>>(raw)
    } catch (_: Exception) { emptyList() }
}

private fun parseSpeedData(raw: String?): List<SpeedDataPoint> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        jsonParser.decodeFromString<List<SpeedDataPoint>>(raw)
    } catch (_: Exception) { emptyList() }
}

private fun parseSpeedLimitData(raw: String?): List<SpeedLimitPoint> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        jsonParser.decodeFromString<List<SpeedLimitPoint>>(raw)
    } catch (_: Exception) { emptyList() }
}

private fun parseHumanFeedback(raw: String?): List<HumanFeedbackItem> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        jsonParser.decodeFromString<List<HumanFeedbackItem>>(raw)
    } catch (_: Exception) { emptyList() }
}

private fun parseEvaluationResult(raw: String?): JsonObject? {
    if (raw.isNullOrBlank()) return null
    return try {
        jsonParser.parseToJsonElement(raw).jsonObject
    } catch (_: Exception) { null }
}

private fun extractEvents(evalObj: JsonObject?): List<DrivingEvent> {
    if (evalObj == null) return emptyList()
    return try {
        val arr = evalObj["events"]?.jsonArray ?: return emptyList()
        jsonParser.decodeFromString<List<DrivingEvent>>(arr.toString())
    } catch (_: Exception) { emptyList() }
}

private fun extractRouteEvents(evalObj: JsonObject?): List<RouteEvent> {
    if (evalObj == null) return emptyList()
    return try {
        val arr = evalObj["events"]?.jsonArray ?: return emptyList()
        jsonParser.decodeFromString<List<RouteEvent>>(arr.toString())
    } catch (_: Exception) { emptyList() }
}

data class ResultCategoryFeedback(
    val notes: List<String>,
    val tips: List<String>,
    val events: List<DrivingEvent>,
)

private fun extractResultCategoryFeedback(evalObj: JsonObject?, key: String): ResultCategoryFeedback {
    if (evalObj == null) return ResultCategoryFeedback(emptyList(), emptyList(), emptyList())
    return try {
        val cat = evalObj[key]?.jsonObject
            ?: return ResultCategoryFeedback(emptyList(), emptyList(), emptyList())
        val notes = cat["notes"]?.jsonArray?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        } ?: emptyList()
        val tips = cat["tips"]?.jsonArray?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        } ?: emptyList()
        val events = try {
            cat["events"]?.jsonArray?.let {
                jsonParser.decodeFromString<List<DrivingEvent>>(it.toString())
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
        ResultCategoryFeedback(notes, tips, events)
    } catch (_: Exception) {
        ResultCategoryFeedback(emptyList(), emptyList(), emptyList())
    }
}

private fun extractCategoryExtraInfo(evalObj: JsonObject?, key: String): Map<String, String> {
    if (evalObj == null) return emptyMap()
    return try {
        val cat = evalObj[key]?.jsonObject ?: return emptyMap()
        when (key) {
            "braking" -> buildMap {
                cat["harsh_braking_events"]?.jsonPrimitive?.intOrNull?.let {
                    put("Harsh braking", "$it events")
                }
                cat["sudden_stops"]?.jsonPrimitive?.intOrNull?.let {
                    put("Sudden stops", "$it")
                }
                cat["smooth_braking_events"]?.jsonPrimitive?.intOrNull?.let {
                    put("Smooth braking", "$it events")
                }
            }
            "speed" -> buildMap {
                cat["speeding_percentage"]?.jsonPrimitive?.floatOrNull?.let {
                    put("Over limit", "${it.roundToInt()}% of time")
                }
                cat["under_speed_percentage"]?.jsonPrimitive?.floatOrNull?.let {
                    put("Under speed", "${it.roundToInt()}% of time")
                }
                cat["school_zone_violations"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }?.let {
                    put("School zone", "${it}s over limit")
                }
            }
            "cornering" -> buildMap {
                cat["sharp_turns"]?.jsonPrimitive?.intOrNull?.let {
                    put("Sharp turns", "$it")
                }
                cat["smooth_turns"]?.jsonPrimitive?.intOrNull?.let {
                    put("Smooth turns", "$it")
                }
                cat["lane_discipline"]?.jsonPrimitive?.contentOrNull?.let {
                    put("Lane discipline", it)
                }
            }
            else -> emptyMap()
        }
    } catch (_: Exception) { emptyMap() }
}

private fun extractSmoothnessExtraInfo(evalObj: JsonObject?): Map<String, String> {
    if (evalObj == null) return emptyMap()
    return try {
        buildMap {
            evalObj["erratic_driving"]?.jsonObject?.let { ed ->
                ed["harsh_acceleration_count"]?.jsonPrimitive?.intOrNull?.let {
                    put("Harsh accel", "$it")
                }
                ed["erratic_count"]?.jsonPrimitive?.intOrNull?.let {
                    put("Erratic speed", "$it")
                }
            }
            evalObj["lane_discipline"]?.jsonObject?.let { ld ->
                ld["weaving_count"]?.jsonPrimitive?.intOrNull?.let {
                    put("Lane weaving", "$it")
                }
            }
        }
    } catch (_: Exception) { emptyMap() }
}

private fun extractSpeedAnalysis(evalObj: JsonObject?): SpeedAnalysis? {
    if (evalObj == null) return null
    return try {
        val speed = evalObj["speed"]?.jsonObject ?: return null
        val maxExcess = speed["max_excess_kmh"]?.jsonPrimitive?.floatOrNull ?: 0f
        if (maxExcess <= 0f) return null
        SpeedAnalysis(
            speedingPercentage = speed["speeding_percentage"]?.jsonPrimitive?.floatOrNull ?: 0f,
            maxExcessKmh = maxExcess,
            violationCount = speed["speed_violations"]?.jsonArray?.size ?: 0,
            schoolZoneViolations = speed["school_zone_violations"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    } catch (_: Exception) { null }
}

private fun extractSummary(evalObj: JsonObject?): String? {
    if (evalObj == null) return null
    return try {
        evalObj["summary"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }
}

private data class SpeedAnalysis(
    val speedingPercentage: Float,
    val maxExcessKmh: Float,
    val violationCount: Int,
    val schoolZoneViolations: Int,
)

private fun countEventsByType(events: List<DrivingEvent>, vararg types: String): Int =
    events.count { it.type in types }

private fun buildTimelineEvents(
    allEvents: List<DrivingEvent>,
    humanFeedbackItems: List<HumanFeedbackItem>,
    evaluatorNotes: String?,
    startTimestamp: Long,
): List<DrivingEvent> {
    val combined = allEvents.toMutableList()
    val hasHumanFlags = combined.any { it.type == "human_flag" }
    if (!hasHumanFlags && humanFeedbackItems.isNotEmpty()) {
        for (flag in humanFeedbackItems) {
            for (ts in flag.timestamps) {
                combined.add(
                    DrivingEvent(
                        type = "human_flag",
                        timestamp = ts.timestamp,
                        severity = "human",
                        label = flag.label,
                    )
                )
            }
        }
    }
    if (!evaluatorNotes.isNullOrBlank()) {
        val noteRegex = Regex("""\[(\d+):(\d+)]\s*(.*)""")
        for (line in evaluatorNotes.lines()) {
            noteRegex.matchEntire(line.trim())?.let { match ->
                val mins = match.groupValues[1].toIntOrNull() ?: 0
                val secs = match.groupValues[2].toIntOrNull() ?: 0
                val elapsedMs = (mins * 60 + secs) * 1000L
                combined.add(
                    DrivingEvent(
                        type = "coach_note",
                        timestamp = startTimestamp + elapsedMs,
                        severity = "note",
                        description = match.groupValues[3],
                    )
                )
            }
        }
    }
    return combined.sortedBy { it.timestamp }
}

// ── Main screen ─────────────────────────────────────────────────────

@Composable
fun DiagnosticRideResultsScreen(
    rideId: Int,
    onViewDetail: (Int) -> Unit,
    onDone: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    var isLoading by remember { mutableStateOf(true) }
    var ride by remember { mutableStateOf<DiagnosticRide?>(null) }

    LaunchedEffect(rideId) {
        // Fetch ride, then poll until evaluated (status = "completed")
        var attempts = 0
        while (attempts < 15) {
            apiClient.getDiagnosticRide(rideId).onSuccess { ride = it }
            if (ride?.status == "completed" && ride?.overallScore != null) break
            attempts++
            kotlinx.coroutines.delay(2000L)
        }
        isLoading = false
    }

    if (isLoading) { LoadingOverlay(); return }
    if (ride == null) {
        Box(
            Modifier.fillMaxSize().background(Background),
            contentAlignment = Alignment.Center,
        ) {
            Text("Could not load results", style = MaterialTheme.typography.titleMedium, color = TextMuted)
        }
        return
    }

    val r = ride!!
    val overallScore = (r.overallScore ?: 0.0).toFloat()
    val passed = r.passed ?: (overallScore >= 70f)

    // Parse all JSON fields once
    val routeCoords = remember(r.routeCoords) { parseRouteCoords(r.routeCoords) }
    val speedData = remember(r.speedData) { parseSpeedData(r.speedData) }
    val speedLimitData = remember(r.speedLimitData) { parseSpeedLimitData(r.speedLimitData) }
    val accelData = remember(r.accelerationData) { parseAccelData(r.accelerationData) }
    val rotationData = remember(r.rotationData) { parseRotationData(r.rotationData) }
    val humanFeedbackItems = remember(r.humanFeedback) { parseHumanFeedback(r.humanFeedback) }
    val evalResult = remember(r.evaluationResult) { parseEvaluationResult(r.evaluationResult) }
    val allEvents = remember(evalResult) { extractEvents(evalResult) }
    val routeEvents = remember(evalResult) { extractRouteEvents(evalResult) }

    val brakingFeedback: ResultCategoryFeedback = remember(evalResult) { extractResultCategoryFeedback(evalResult, "braking") }
    val speedFeedback: ResultCategoryFeedback = remember(evalResult) { extractResultCategoryFeedback(evalResult, "speed") }
    val corneringFeedback: ResultCategoryFeedback = remember(evalResult) { extractResultCategoryFeedback(evalResult, "cornering") }
    val erraticFeedback: ResultCategoryFeedback = remember(evalResult) { extractResultCategoryFeedback(evalResult, "erratic_driving") }
    val laneFeedback: ResultCategoryFeedback = remember(evalResult) { extractResultCategoryFeedback(evalResult, "lane_discipline") }

    val brakingExtraInfo = remember(evalResult) { extractCategoryExtraInfo(evalResult, "braking") }
    val speedExtraInfo = remember(evalResult) { extractCategoryExtraInfo(evalResult, "speed") }
    val corneringExtraInfo = remember(evalResult) { extractCategoryExtraInfo(evalResult, "cornering") }
    val smoothnessExtraInfo = remember(evalResult) { extractSmoothnessExtraInfo(evalResult) }

    val speedAnalysis = remember(evalResult) { extractSpeedAnalysis(evalResult) }
    val summary = remember(evalResult) { extractSummary(evalResult) }

    val startTimestamp = speedData.firstOrNull()?.timestamp ?: 0L
    val timelineEvents = remember(allEvents, humanFeedbackItems, r.evaluatorNotes, startTimestamp) {
        buildTimelineEvents(allEvents, humanFeedbackItems, r.evaluatorNotes, startTimestamp)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 1. Header ───────────────────────────────────────────
        Spacer(Modifier.height(24.dp))
        ResultHeader(passed)

        Spacer(Modifier.height(20.dp))

        // ── 2. Score Gauges ─────────────────────────────────────
        ScoreGaugesSection(r, overallScore)

        Spacer(Modifier.height(16.dp))

        // ── 3. Ride Stats Row ───────────────────────────────────
        RideStatsRow(r, allEvents.size)

        Spacer(Modifier.height(16.dp))

        // ── 4. School Zone Violation Alert ──────────────────────
        speedAnalysis?.takeIf { it.schoolZoneViolations > 0 }?.let { sa ->
            SchoolZoneAlert(sa.schoolZoneViolations)
            Spacer(Modifier.height(12.dp))
        }

        // ── 5. Speed Analysis Card ──────────────────────────────
        speedAnalysis?.let { sa ->
            SpeedAnalysisCard(sa)
            Spacer(Modifier.height(16.dp))
        }

        // ── 6. Route Map ────────────────────────────────────────
        if (routeCoords.isNotEmpty()) {
            SectionLabel("Route Map")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    RouteReplayMap(
                        routeCoordinates = routeCoords,
                        events = routeEvents,
                        height = 280.dp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── 7. Speed vs Limit Graph ─────────────────────────────
        if (speedData.isNotEmpty()) {
            SectionLabel("Speed vs. Limit")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SpeedGraph(
                        speedData = speedData,
                        speedLimitData = speedLimitData,
                        height = 200.dp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── 8. Force Analysis Charts ────────────────────────────
        if (accelData.isNotEmpty() || rotationData.isNotEmpty()) {
            SectionLabel("Force Analysis")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SeparateForceCharts(
                        accelData = accelData,
                        rotationData = rotationData,
                        chartHeight = 140.dp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── 9. Detailed Breakdown Cards ─────────────────────────
        SectionLabel("Detailed Breakdown")

        CategoryBreakdownCard(
            symbol = "🛑",
            title = "Braking",
            score = r.brakingScore,
            feedback = brakingFeedback,
            extraInfo = brakingExtraInfo,
            eventCount = countEventsByType(allEvents, "harsh_braking", "sudden_stop"),
        )

        CategoryBreakdownCard(
            symbol = "⚡",
            title = "Speed Control",
            score = r.speedScore,
            feedback = speedFeedback,
            extraInfo = speedExtraInfo,
            eventCount = countEventsByType(allEvents, "speeding", "erratic_speed"),
        )

        CategoryBreakdownCard(
            symbol = "↻",
            title = "Cornering",
            score = r.corneringScore,
            feedback = corneringFeedback,
            extraInfo = corneringExtraInfo,
            eventCount = countEventsByType(allEvents, "sharp_turn", "hard_cornering"),
        )

        // Smoothness card (conditional)
        val hasSmoothnessData = r.smoothnessScore != null ||
            erraticFeedback.events.isNotEmpty() || laneFeedback.events.isNotEmpty()
        if (hasSmoothnessData) {
            val smoothnessCombined = ResultCategoryFeedback(
                notes = erraticFeedback.notes + laneFeedback.notes,
                tips = erraticFeedback.tips + laneFeedback.tips,
                events = erraticFeedback.events + laneFeedback.events,
            )
            CategoryBreakdownCard(
                symbol = "〰",
                title = "Smoothness",
                score = r.smoothnessScore ?: 100.0,
                feedback = smoothnessCombined,
                extraInfo = smoothnessExtraInfo,
                eventCount = countEventsByType(
                    allEvents, "harsh_acceleration", "erratic_speed", "excessive_jerk"
                ),
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── 9. Event Timeline ───────────────────────────────────
        if (timelineEvents.isNotEmpty() || humanFeedbackItems.isNotEmpty()) {
            SectionLabel("Event Timeline")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val feedbackSummary = humanFeedbackItems.map {
                        HumanFeedbackSummary(code = it.code, label = it.label, count = it.count)
                    }
                    EventTimeline(
                        events = timelineEvents,
                        startTime = startTimestamp,
                        humanFeedback = feedbackSummary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── 10. Human Feedback Section ──────────────────────────
        if (humanFeedbackItems.isNotEmpty()) {
            SectionLabel("Supervisor Feedback")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    HumanFeedbackSection(humanFeedback = humanFeedbackItems)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── 11. Summary ─────────────────────────────────────────
        summary?.let { text ->
            SectionLabel("Summary")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📋", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Evaluation Summary",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text,
                        fontSize = 14.sp,
                        color = TextSecondary,
                        lineHeight = 22.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── 12. Raw Sensor Data Table ───────────────────────────
        SectionLabel("Raw Sensor Data")
        val sensorRows = remember(accelData, rotationData, speedData, speedLimitData) {
            buildSensorRows(accelData, rotationData, speedData, speedLimitData)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .padding(horizontal = 16.dp),
        ) {
            RawSensorTable(rows = sensorRows, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(16.dp))

        // ── 13. Action Buttons ──────────────────────────────────
        ActionButtons(passed, rideId, onViewDetail, onDone)

        Spacer(Modifier.height(40.dp))
    }
}

// ── Section label ───────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
    )
}

// ── 1. Result Header ────────────────────────────────────────────────

@Composable
private fun ResultHeader(passed: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (passed) "You Passed!" else "Not Yet",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (passed) "Congratulations on your excellent driving!"
            else "Keep practicing and try again",
            fontSize = 16.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

// ── 2. Score Gauges ─────────────────────────────────────────────────

@Composable
private fun ScoreGaugesSection(r: DiagnosticRide, overallScore: Float) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScoreGauge(
            score = overallScore,
            label = "Overall",
            size = 140.dp,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ScoreGauge(
                score = (r.brakingScore ?: 0.0).toFloat(),
                label = "Braking",
                size = 90.dp,
            )
            ScoreGauge(
                score = (r.speedScore ?: 0.0).toFloat(),
                label = "Speed",
                size = 90.dp,
            )
            ScoreGauge(
                score = (r.corneringScore ?: 0.0).toFloat(),
                label = "Cornering",
                size = 90.dp,
            )
        }
    }
}

// ── 3. Ride Stats Row ───────────────────────────────────────────────

@Composable
private fun RideStatsRow(r: DiagnosticRide, eventCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatItem("⏱", r.durationMinutes?.let { "${it.toInt()} min" } ?: "--")
        StatItem("📍", r.distanceKm?.let { "${fmtDouble(it)} km" } ?: "--")
        StatItem("⚠", "$eventCount events")
    }
}

@Composable
private fun StatItem(icon: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
        )
    }
}

// ── 4. School Zone Alert ────────────────────────────────────────────

@Composable
private fun SchoolZoneAlert(violations: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Warning.copy(alpha = 0.15f))
            .border(1.dp, Warning.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("⚠", fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "School Zone Violations",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Warning,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Speed limit exceeded in a school zone for $violations seconds. " +
                    "School zone violations carry a 2x penalty.",
                fontSize = 13.sp,
                color = Warning.copy(alpha = 0.85f),
                lineHeight = 18.sp,
            )
        }
    }
}

// ── 5. Speed Analysis Card ──────────────────────────────────────────

@Composable
private fun SpeedAnalysisCard(sa: SpeedAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Speed Analysis",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                SpeedAnalysisItem(
                    value = "${sa.speedingPercentage.roundToInt()}%",
                    label = "Time over limit",
                    color = TextPrimary,
                )
                SpeedAnalysisItem(
                    value = "+${sa.maxExcessKmh.roundToInt()} km/h",
                    label = "Max excess",
                    color = Error,
                )
                SpeedAnalysisItem(
                    value = "${sa.violationCount}",
                    label = "Violations",
                    color = TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun SpeedAnalysisItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = TextMuted,
        )
    }
}

// ── 8. Category Breakdown Card ──────────────────────────────────────

@Composable
private fun CategoryBreakdownCard(
    symbol: String,
    title: String,
    score: Double?,
    feedback: ResultCategoryFeedback,
    extraInfo: Map<String, String>,
    eventCount: Int,
) {
    val s = (score ?: 0.0).toFloat().coerceIn(0f, 100f)
    val scoreColor = when {
        s >= 80f -> AccentLight
        s >= 60f -> Warning
        else -> Error
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: icon + title + score badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(symbol, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(scoreColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "${s.toInt()}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }

            // Event count
            if (eventCount > 0) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠", fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$eventCount event${if (eventCount != 1) "s" else ""} detected",
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
            }

            // Extra info (stats row)
            if (extraInfo.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = SurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    for ((label, value) in extraInfo) {
                        Column {
                            Text(
                                label.uppercase(),
                                fontSize = 10.sp,
                                color = TextMuted,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                value,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = SurfaceVariant)
            }

            // Notes
            if (feedback.notes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                for (note in feedback.notes) {
                    Text(
                        note,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            // Tips
            if (feedback.tips.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Tips to Improve",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Warning,
                )
                Spacer(Modifier.height(6.dp))
                for (tip in feedback.tips) {
                    Row(modifier = Modifier.padding(bottom = 4.dp)) {
                        Text("💡", fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tip,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

// ── 13. Action Buttons ──────────────────────────────────────────────

@Composable
private fun ActionButtons(
    passed: Boolean,
    rideId: Int,
    onViewDetail: (Int) -> Unit,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (passed) {
            PrimaryButton(
                text = "Explore Advanced Module",
                onClick = onDone,
                color = Accent,
            )
        } else {
            PrimaryButton(
                text = "Retry with Instructor",
                onClick = onDone,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = "Start Basics Lessons",
                onClick = onDone,
                color = SurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        PrimaryButton(
            text = "View Full Details",
            onClick = { onViewDetail(rideId) },
            color = Primary.copy(alpha = 0.15f),
        )

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Return to Home", color = Primary, fontSize = 15.sp)
        }
    }
}
