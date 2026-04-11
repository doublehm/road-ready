package com.roadready.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.ui.theme.*
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class DrivingEvent(
    val type: String = "",
    val timestamp: Long = 0L,
    val severity: String = "medium",
    val value: Float = 0f,
    val description: String = "",
    val label: String = "",
    val limit: Float = 0f,
    val zone_type: String = "",
)

private data class EventConfig(
    val symbol: String,
    val label: String,
    val color: Color,
)

private val EVENT_CONFIGS = mapOf(
    "speeding" to EventConfig("⚡", "Speeding", Color(0xFFEF4444)),
    "harsh_braking" to EventConfig("✋", "Harsh Braking", Color(0xFFF59E0B)),
    "sharp_turn" to EventConfig("↻", "Sharp Turn", Color(0xFFF59E0B)),
    "sudden_stop" to EventConfig("⏹", "Sudden Stop", Color(0xFFEF4444)),
    "human_flag" to EventConfig("⚑", "Supervisor Flag", Color(0xFFF59E0B)),
    "coach_note" to EventConfig("💬", "Coach Note", Color(0xFF3B82F6)),
    "harsh_acceleration" to EventConfig("🚀", "Harsh Accel", Color(0xFFF97316)),
    "erratic_speed" to EventConfig("〰", "Erratic Speed", Color(0xFFA855F7)),
    "hard_cornering" to EventConfig("↻", "Hard Cornering", Color(0xFFF59E0B)),
    "excessive_jerk" to EventConfig("⚡", "Excessive Jerk", Color(0xFFF59E0B)),
    "unstable_grip" to EventConfig("⊛", "Unstable Grip", Color(0xFFEF4444)),
)

private val SEVERITY_COLORS = mapOf(
    "high" to Color(0xFFEF4444),
    "medium" to Color(0xFFF59E0B),
    "low" to Color(0xFF15803D),
    "human" to Color(0xFFF59E0B),
    "note" to Color(0xFF3B82F6),
)

@Serializable
data class HumanFeedbackSummary(
    val code: String = "",
    val label: String = "",
    val count: Int = 0,
)

/**
 * Vertical timeline of driving events.
 *
 * Each event shows type, severity badge, timestamp relative to ride start,
 * and a description. An optional aggregated supervisor summary appears at the top.
 */
@Composable
fun EventTimeline(
    events: List<DrivingEvent> = emptyList(),
    startTime: Long = 0L,
    humanFeedback: List<HumanFeedbackSummary> = emptyList(),
    maxEvents: Int = 50,
    modifier: Modifier = Modifier,
) {
    val totalFlags = humanFeedback.sumOf { it.count }

    // Empty state
    if (events.isEmpty() && totalFlags == 0) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("✓", fontSize = 32.sp, color = Accent)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No driving events detected!",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Accent,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Great job maintaining safe driving habits.",
                fontSize = 13.sp,
                color = TextSecondary,
            )
        }
        return
    }

    val displayEvents = remember(events, maxEvents) {
        events.sortedBy { it.timestamp }.take(maxEvents)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(16.dp),
    ) {
        // ── Supervisor summary header ──
        if (totalFlags > 0) {
            SupervisorSummary(humanFeedback, totalFlags)
            Spacer(modifier = Modifier.height(20.dp))
        }

        Text(
            text = "CHRONOLOGICAL TIMELINE",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // ── Timeline items ──
        displayEvents.forEachIndexed { index, event ->
            val isLast = index == displayEvents.lastIndex
            TimelineItem(event, startTime, isLast)
        }

        if (events.size > maxEvents) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "+ ${events.size - maxEvents} more events",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun SupervisorSummary(items: List<HumanFeedbackSummary>, totalFlags: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Warning.copy(alpha = 0.1f))
            .border(1.dp, SurfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("⚑ ", fontSize = 16.sp, color = Warning)
            Text(
                text = "Supervisor Summary",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Warning,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Warning)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "$totalFlags total flags",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Chips grid
        FlowRow(items)
    }
}

@Composable
private fun FlowRow(items: List<HumanFeedbackSummary>) {
    // Simple row-based wrapping
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var remaining = items.toMutableList()
        while (remaining.isNotEmpty()) {
            val rowItems = remaining.take(3)
            remaining = remaining.drop(3).toMutableList()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (item in rowItems) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, SurfaceVariant, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${item.count}x",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Warning,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.label,
                            fontSize = 11.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineItem(event: DrivingEvent, startTime: Long, isLast: Boolean) {
    val config = EVENT_CONFIGS[event.type] ?: EventConfig(
        "⚠",
        event.type.replace("_", " "),
        TextSecondary,
    )
    val sevColor = SEVERITY_COLORS[event.severity] ?: SEVERITY_COLORS["medium"]!!

    Row(modifier = Modifier.fillMaxWidth()) {
        // Left: dot + connector
        Column(
            modifier = Modifier.width(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(sevColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(config.symbol, fontSize = 12.sp, color = Color.White)
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(SurfaceVariant),
                )
            }
        }

        // Right: content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, bottom = if (isLast) 0.dp else 16.dp),
        ) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = config.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = config.color,
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (event.severity == "high") {
                    Badge(text = "HIGH", color = Error)
                }
                if (event.type == "human_flag") {
                    Badge(text = "MANUAL FLAG", color = Warning)
                }
                if (event.type == "coach_note") {
                    Badge(text = "COACH NOTE", color = Primary)
                }
            }

            // Detail
            Text(
                text = getEventDetail(event, config),
                fontSize = 13.sp,
                color = TextPrimary,
                lineHeight = 18.sp,
            )

            // Elapsed time
            if (event.timestamp > 0L && startTime > 0L) {
                val elapsed = ((event.timestamp - startTime) / 1000).coerceAtLeast(0)
                val mins = elapsed / 60
                val secs = elapsed % 60
                Text(
                    text = "at $mins:${if (secs < 10) "0" else ""}$secs into ride",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text = text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

private fun getEventDetail(event: DrivingEvent, config: EventConfig): String {
    if (event.description.isNotEmpty()) return event.description
    return when (event.type) {
        "speeding" -> {
            val zone = if (event.zone_type == "school") " (SCHOOL ZONE)" else ""
            "${event.value.roundToInt()} km/h in a ${event.limit.roundToInt()} km/h zone$zone"
        }
        "harsh_braking" -> "Braking force: ${event.value}g"
        "sharp_turn" -> "Lateral force: ${event.value}g"
        "sudden_stop" -> "Speed drop: ${event.value} km/h"
        "human_flag" -> event.label.ifEmpty { "Criterion flagged" }
        "coach_note" -> event.label.ifEmpty { "Observation recorded" }
        else -> event.type.replace("_", " ")
    }
}
