package com.roadready.ui.screens.diagnostic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

private val jsonParser = Json { ignoreUnknownKeys = true }

// ── JSON parsing helpers ────────────────────────────────────────────

private fun parseRouteCoords(raw: String?): List<RouteCoordinate> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        jsonParser.decodeFromString<List<RouteCoordinate>>(raw)
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseSpeedData(raw: String?): List<SpeedDataPoint> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        jsonParser.decodeFromString<List<SpeedDataPoint>>(raw)
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseSpeedLimitData(raw: String?): List<SpeedLimitPoint> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        jsonParser.decodeFromString<List<SpeedLimitPoint>>(raw)
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseHumanFeedback(raw: String?): List<HumanFeedbackItem> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        jsonParser.decodeFromString<List<HumanFeedbackItem>>(raw)
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseEvaluationResult(raw: String?): JsonObject? {
    if (raw.isNullOrBlank()) return null
    return try {
        jsonParser.parseToJsonElement(raw).jsonObject
    } catch (_: Exception) {
        null
    }
}

/** Extract DrivingEvent list from evaluation_result.events */
private fun extractEvents(evalObj: JsonObject?): List<DrivingEvent> {
    if (evalObj == null) return emptyList()
    return try {
        val arr = evalObj["events"]?.jsonArray ?: return emptyList()
        jsonParser.decodeFromString<List<DrivingEvent>>(arr.toString())
    } catch (_: Exception) {
        emptyList()
    }
}

/** Extract RouteEvent list (for the map) from evaluation_result.events */
private fun extractRouteEvents(evalObj: JsonObject?): List<RouteEvent> {
    if (evalObj == null) return emptyList()
    return try {
        val arr = evalObj["events"]?.jsonArray ?: return emptyList()
        jsonParser.decodeFromString<List<RouteEvent>>(arr.toString())
    } catch (_: Exception) {
        emptyList()
    }
}

private data class CategoryFeedback(
    val notes: List<String>,
    val tips: List<String>,
)

/** Pull notes & tips for a given key (speed, braking, cornering, smoothness). */
private fun extractCategoryFeedback(evalObj: JsonObject?, key: String): CategoryFeedback {
    if (evalObj == null) return CategoryFeedback(emptyList(), emptyList())
    return try {
        val cat = evalObj[key]?.jsonObject ?: return CategoryFeedback(emptyList(), emptyList())
        val notes = cat["notes"]?.jsonArray?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        } ?: emptyList()
        val tips = cat["tips"]?.jsonArray?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        } ?: emptyList()
        CategoryFeedback(notes, tips)
    } catch (_: Exception) {
        CategoryFeedback(emptyList(), emptyList())
    }
}

/** Count events of specific types from the events list. */
private fun countEventsByType(events: List<DrivingEvent>, vararg types: String): Int =
    events.count { it.type in types }

// ── Main screen ─────────────────────────────────────────────────────

@Composable
fun DiagnosticRideDetailScreen(
    rideId: Int,
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    var isLoading by remember { mutableStateOf(true) }
    var ride by remember { mutableStateOf<DiagnosticRide?>(null) }

    LaunchedEffect(rideId) {
        apiClient.getDiagnosticRide(rideId).onSuccess { ride = it }
        isLoading = false
    }

    if (isLoading) { LoadingOverlay(); return }
    if (ride == null) {
        Box(
            Modifier.fillMaxSize().background(Background),
            contentAlignment = Alignment.Center,
        ) {
            Text("Ride not found", style = MaterialTheme.typography.titleMedium, color = TextMuted)
        }
        return
    }

    val r = ride!!

    // Parse all JSON fields once
    val routeCoords = remember(r.routeCoords) { parseRouteCoords(r.routeCoords) }
    val speedData = remember(r.speedData) { parseSpeedData(r.speedData) }
    val speedLimitData = remember(r.speedLimitData) { parseSpeedLimitData(r.speedLimitData) }
    val humanFeedbackItems = remember(r.humanFeedback) { parseHumanFeedback(r.humanFeedback) }
    val evalResult = remember(r.evaluationResult) { parseEvaluationResult(r.evaluationResult) }
    val allEvents = remember(evalResult) { extractEvents(evalResult) }
    val routeEvents = remember(evalResult) { extractRouteEvents(evalResult) }

    val brakingFeedback = remember(evalResult) { extractCategoryFeedback(evalResult, "braking") }
    val speedFeedback = remember(evalResult) { extractCategoryFeedback(evalResult, "speed") }
    val corneringFeedback = remember(evalResult) { extractCategoryFeedback(evalResult, "cornering") }
    val smoothnessFeedback = remember(evalResult) { extractCategoryFeedback(evalResult, "smoothness") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Text("‹", fontSize = 22.sp, color = TextPrimary)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "DIAGNOSTIC REPORT",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = TextPrimary,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(44.dp))
        }

        // ── 1. Hero Score Card ──────────────────────────────────
        HeroScoreCard(r)

        Spacer(Modifier.height(24.dp))

        // ── 2. Trip Details ─────────────────────────────────────
        if (r.durationMinutes != null || r.distanceKm != null || r.rideType != null) {
            SectionLabel("TRIP DETAILS")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    r.durationMinutes?.let { TripStat("⏱", "${it.toInt()}", "MIN", Primary) }
                    r.distanceKm?.let { TripStat("📍", fmtDouble(it, 1), "KM", AccentLight) }
                    r.rideType?.let {
                        val label = it.replace("_", " ").replaceFirstChar { c -> c.uppercase() }
                        TripStat("🚗", label, "TYPE", Warning)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── 3. Route Map ────────────────────────────────────────
        if (routeCoords.isNotEmpty()) {
            SectionLabel("ROUTE ANALYSIS")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    RouteReplayMap(
                        routeCoordinates = routeCoords,
                        events = routeEvents,
                        height = 280.dp,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── 4. Speed Graph ──────────────────────────────────────
        if (speedData.isNotEmpty()) {
            SectionLabel("VELOCITY TELEMETRY")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SpeedGraph(
                        speedData = speedData,
                        speedLimitData = speedLimitData,
                        height = 200.dp,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── 5. Evaluation Breakdown Cards ───────────────────────
        val hasAnyFeedback = listOf(brakingFeedback, speedFeedback, corneringFeedback, smoothnessFeedback)
            .any { it.notes.isNotEmpty() || it.tips.isNotEmpty() }

        if (hasAnyFeedback || allEvents.isNotEmpty()) {
            SectionLabel("SYSTEM FEEDBACK")

            EvaluationBreakdownCard(
                symbol = "🛑",
                title = "Braking Precision",
                score = r.brakingScore,
                feedback = brakingFeedback,
                eventCount = countEventsByType(allEvents, "harsh_braking", "sudden_stop"),
                eventLabel = "braking events",
            )

            EvaluationBreakdownCard(
                symbol = "⚡",
                title = "Speed Control",
                score = r.speedScore,
                feedback = speedFeedback,
                eventCount = countEventsByType(allEvents, "speeding", "erratic_speed"),
                eventLabel = "speed events",
            )

            EvaluationBreakdownCard(
                symbol = "↻",
                title = "Cornering",
                score = r.corneringScore,
                feedback = corneringFeedback,
                eventCount = countEventsByType(allEvents, "sharp_turn", "hard_cornering"),
                eventLabel = "turn events",
            )

            EvaluationBreakdownCard(
                symbol = "〰",
                title = "Smoothness",
                score = r.smoothnessScore,
                feedback = smoothnessFeedback,
                eventCount = countEventsByType(allEvents, "harsh_acceleration", "excessive_jerk"),
                eventLabel = "smoothness events",
            )

            Spacer(Modifier.height(24.dp))
        }

        // ── 6. Event Timeline ───────────────────────────────────
        if (allEvents.isNotEmpty()) {
            SectionLabel("EVENT LOG")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val feedbackSummary = humanFeedbackItems.map {
                        HumanFeedbackSummary(code = it.code, label = it.label, count = it.count)
                    }
                    val startTs = speedData.firstOrNull()?.timestamp ?: 0L
                    EventTimeline(
                        events = allEvents.sortedBy { it.timestamp },
                        startTime = startTs,
                        humanFeedback = feedbackSummary,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── 7. Human Feedback Section ───────────────────────────
        if (humanFeedbackItems.isNotEmpty()) {
            SectionLabel("SUPERVISOR FEEDBACK")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    HumanFeedbackSection(humanFeedback = humanFeedbackItems)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── 8. Evaluator Notes ──────────────────────────────────
        r.evaluatorNotes?.takeIf { it.isNotBlank() }?.let { notes ->
            SectionLabel("EVALUATOR NOTES")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💬", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Coach Notes",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        notes,
                        fontSize = 14.sp,
                        color = TextSecondary,
                        lineHeight = 22.sp,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Spacer(Modifier.height(60.dp))
    }
}

// ── Section label ───────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        color = TextSecondary,
        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
    )
}

// ── Hero Score Card ─────────────────────────────────────────────────

@Composable
private fun HeroScoreCard(r: DiagnosticRide) {
    val overallScore = (r.overallScore ?: 0.0).toFloat()

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            // Main score + pass/fail badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScoreGauge(
                    score = overallScore,
                    label = "Safety Score",
                    size = 130.dp,
                )

                Column(horizontalAlignment = Alignment.End) {
                    val passed = r.passed ?: (overallScore >= 70f)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (passed) Accent else Error)
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = if (passed) "PASS" else "FAIL",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    r.createdAt?.let {
                        Text(
                            text = it.take(10),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted,
                        )
                    }
                    r.status.let {
                        Spacer(Modifier.height(4.dp))
                        val statusColor = when (it) {
                            "completed" -> AccentLight
                            "in_progress" -> Warning
                            else -> TextMuted
                        }
                        Text(
                            text = it.replace("_", " ").uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = statusColor,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(Modifier.height(20.dp))

            // Sub-gauges: Braking, Speed, Cornering
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ScoreGauge(
                    score = (r.brakingScore ?: 0.0).toFloat(),
                    label = "Braking",
                    size = 80.dp,
                )
                ScoreGauge(
                    score = (r.speedScore ?: 0.0).toFloat(),
                    label = "Speed",
                    size = 80.dp,
                )
                ScoreGauge(
                    score = (r.corneringScore ?: 0.0).toFloat(),
                    label = "Cornering",
                    size = 80.dp,
                )
            }

            // Smoothness bar (if available)
            r.smoothnessScore?.let { smooth ->
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(Modifier.height(12.dp))
                CategoryProgressRow("〰  Smoothness", smooth)
            }
        }
    }
}

// ── Category progress bar row ───────────────────────────────────────

@Composable
private fun CategoryProgressRow(label: String, score: Double) {
    val s = score.toFloat().coerceIn(0f, 100f)
    val color = when {
        s >= 80f -> AccentLight
        s >= 60f -> Warning
        else -> Error
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        LinearProgressIndicator(
            progress = { s / 100f },
            modifier = Modifier.width(100.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = SurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${s.toInt()}%",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
        )
    }
}

// ── Trip Stat item ──────────────────────────────────────────────────

@Composable
private fun TripStat(symbol: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(symbol, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = color,
        )
        Text(
            text = unit,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = TextMuted,
        )
    }
}

// ── Evaluation Breakdown Card ───────────────────────────────────────

@Composable
private fun EvaluationBreakdownCard(
    symbol: String,
    title: String,
    score: Double?,
    feedback: CategoryFeedback,
    eventCount: Int,
    eventLabel: String,
) {
    val hasContent = feedback.notes.isNotEmpty() || feedback.tips.isNotEmpty() || eventCount > 0
    if (!hasContent && score == null) return

    val s = (score ?: 0.0).toFloat().coerceIn(0f, 100f)
    val scoreColor = when {
        s >= 80f -> AccentLight
        s >= 60f -> Warning
        else -> Error
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header with symbol, title, score badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(symbol, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(scoreColor.copy(alpha = 0.15f))
                        .border(1.dp, scoreColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${s.toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = scoreColor,
                    )
                }
            }

            // Event count badge
            if (eventCount > 0) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Warning.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⚠", fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "$eventCount $eventLabel detected",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Warning,
                    )
                }
            }

            // Notes
            if (feedback.notes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                for (note in feedback.notes) {
                    Text(
                        text = note,
                        fontSize = 14.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            // Tips
            if (feedback.tips.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "TIPS TO IMPROVE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = Primary,
                )
                Spacer(Modifier.height(6.dp))
                for (tip in feedback.tips) {
                    Row(modifier = Modifier.padding(bottom = 4.dp)) {
                        Text("💡", fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tip,
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
