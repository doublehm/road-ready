package com.roadready.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.data.repository.Vec3
import com.roadready.ui.theme.*
import com.roadready.ui.util.fmtDouble
import com.roadready.ui.util.pad2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

// ── Public event model ──────────────────────────────────────────────────────────

data class GaugeEvent(
    val id: String,
    val label: String,
    val value: Double,
    val unit: String,
    val faultType: String,
)

// ── Physics thresholds — must mirror diagnostic_evaluator.py ────────────────────

private data class Threshold(val green: Double, val yellow: Double, val red: Double)

private val THRESHOLDS = mapOf(
    "lateral"  to Threshold(0.10, 0.25, 0.35),
    "braking"  to Threshold(0.08, 0.30, 0.40),
    "throttle" to Threshold(0.05, 0.20, 0.30),
    "vertical" to Threshold(0.05, 0.20, 0.30),
    "grip"     to Threshold(0.15, 0.35, 0.50),
    "steering" to Threshold(0.20, 0.50, 0.80),
    "jerk"     to Threshold(1.5,  3.0,  5.0),
)

private fun thresholdColor(value: Double, thresh: Threshold): Color = when {
    value >= thresh.red    -> Color(0xFFEF4444)
    value >= thresh.yellow -> Color(0xFFF59E0B)
    value >= thresh.green  -> Color(0xFF22C55E)
    else                   -> Color(0xFF64748B)
}

private fun barFraction(value: Double, thresh: Threshold): Float {
    val max = thresh.red * 1.5
    return (value / max).coerceIn(0.0, 1.0).toFloat()
}

private fun speedColor(speed: Double, limit: Int?): Color {
    if (limit == null || limit <= 0) return TextSecondary
    val excess = speed - limit
    return when {
        excess > 10 -> Color(0xFFEF4444)
        excess > 0  -> Color(0xFFF59E0B)
        else        -> Color(0xFF22C55E)
    }
}

// ── Gauge model ─────────────────────────────────────────────────────────────────

private data class GaugeData(
    val id: String,
    val label: String,
    val value: Double,
    val unit: String,
    val thresh: Threshold,
    val wide: Boolean = false,
    val faultType: String = "",
)

// ── Main composable ─────────────────────────────────────────────────────────────

/**
 * Real-time telemetry dashboard shown during an active diagnostic ride.
 *
 * Displays:
 * 1. Stats row — TIME, DIST, SPEED, LIMIT (with dividers)
 * 2. Info row — road name chip, zone type chip
 * 3. Speed analysis row — excess speed, over-limit %, school zone violations
 * 4. Force gauge grid — 8 gauges: LEFT TURN, RIGHT TURN, STOP FORCE (full width),
 *    ACCEL, GRIP, TURN RATE, SMOOTHNESS, VERTICAL
 */
@Composable
fun TelemetryPanel(
    acceleration: Vec3,
    rotation: Vec3,
    prevAcceleration: Vec3?,
    sampleIntervalMs: Long = 100,
    speed: Double,
    prevSpeed: Double,
    isActive: Boolean,
    duration: Int,
    distance: Double,
    speedLimit: Int?,
    zoneType: String?,
    roadName: String?,
    modifier: Modifier = Modifier,
    onThresholdExceeded: ((GaugeEvent) -> Unit)? = null,
) {
    val gauges = remember(
        acceleration.x, acceleration.y, acceleration.z,
        rotation.x, rotation.y, rotation.z,
        prevAcceleration?.x, prevAcceleration?.y, prevAcceleration?.z,
        sampleIntervalMs, speed, prevSpeed,
    ) {
        computeGauges(acceleration, rotation, prevAcceleration, sampleIntervalMs, speed, prevSpeed)
    }

    // Fire threshold events for any gauge that crosses into red
    LaunchedEffect(gauges) {
        if (onThresholdExceeded != null && isActive) {
            for (g in gauges) {
                if (g.value >= g.thresh.red) {
                    onThresholdExceeded(
                        GaugeEvent(
                            id = g.id,
                            label = g.label,
                            value = g.value,
                            unit = g.unit,
                            faultType = g.faultType,
                        )
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Background.copy(alpha = 0.92f))
            .border(1.dp, SurfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(10.dp),
    ) {
        // ── Stats row ──
        StatsRow(duration, distance, speed, speedLimit)

        Spacer(Modifier.height(6.dp))

        // ── Info row ──
        InfoRow(roadName, zoneType, speedLimit, speed)

        Spacer(Modifier.height(6.dp))

        // ── Speed analysis row ──
        if (speedLimit != null && speedLimit > 0) {
            SpeedAnalysisRow(speed, speedLimit, zoneType)
            Spacer(Modifier.height(6.dp))
        }

        // ── Force gauges grid ──
        GaugeGrid(gauges)
    }
}

// ── Stats row ───────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(duration: Int, distance: Double, speed: Double, speedLimit: Int?) {
    val spdColor = speedColor(speed, speedLimit)
    val timeStr = "${pad2(duration / 60)}:${pad2(duration % 60)}"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell("TIME", timeStr)
        StatDivider()
        StatCell("DIST", "${fmtDouble(distance, 2)} km")
        StatDivider()
        StatCell("SPEED", "${speed.toInt()}", spdColor, large = true)
        StatDivider()
        StatCell("LIMIT", speedLimit?.toString() ?: "--")
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    valueColor: Color = Color(0xFFE2E8F0),
    large: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF64748B), letterSpacing = 1.sp)
        Text(value, fontSize = if (large) 22.sp else 14.sp,
            fontWeight = FontWeight.Black, color = valueColor)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(SurfaceVariant.copy(alpha = 0.25f)))
}

// ── Info row ────────────────────────────────────────────────────────────────────

@Composable
private fun InfoRow(roadName: String?, zoneType: String?, speedLimit: Int?, speed: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Road name chip
        val displayRoad = if (!roadName.isNullOrBlank()) roadName else "Unknown road"
        InfoChip(text = "📍 $displayRoad", modifier = Modifier.weight(1f, fill = false))

        // Zone type chip
        if (!zoneType.isNullOrBlank() && zoneType != "regular") {
            val zoneColor = when (zoneType) {
                "school"      -> Color(0xFFF59E0B)
                "residential" -> Color(0xFF3B82F6)
                else          -> Color(0xFF64748B)
            }
            InfoChip(
                text = "⚠ ${zoneType.replaceFirstChar { it.uppercase() }}",
                bgColor = zoneColor.copy(alpha = 0.2f),
                textColor = zoneColor,
            )
        }
    }
}

@Composable
private fun InfoChip(
    text: String,
    modifier: Modifier = Modifier,
    bgColor: Color = SurfaceVariant.copy(alpha = 0.5f),
    textColor: Color = Color(0xFFE2E8F0),
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = textColor,
            maxLines = 1)
    }
}

// ── Speed analysis row ──────────────────────────────────────────────────────────

@Composable
private fun SpeedAnalysisRow(speed: Double, speedLimit: Int, zoneType: String?) {
    val excess = (speed - speedLimit).coerceAtLeast(0.0)
    val overPct = if (speedLimit > 0) ((excess / speedLimit) * 100).toInt() else 0
    val isSchoolZone = zoneType == "school"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (excess > 0) Color(0xFFEF4444).copy(alpha = 0.12f)
                else SurfaceVariant.copy(alpha = 0.3f)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniStat("EXCESS", if (excess > 0) "+${excess.toInt()} km/h" else "—",
            if (excess > 0) Color(0xFFEF4444) else Color(0xFF64748B))
        MiniStat("OVER", if (overPct > 0) "$overPct%" else "—",
            if (overPct > 10) Color(0xFFEF4444) else Color(0xFF64748B))
        if (isSchoolZone) {
            MiniStat("🏫 ZONE", if (excess > 0) "⚠" else "✓",
                if (excess > 0) Color(0xFFEF4444) else Color(0xFF22C55E))
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF64748B), letterSpacing = 0.8.sp)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Black, color = color)
    }
}

// ── Gauge grid ──────────────────────────────────────────────────────────────────

@Composable
private fun GaugeGrid(gauges: List<GaugeData>) {
    val rows = mutableListOf<List<GaugeData>>()
    var currentRow = mutableListOf<GaugeData>()

    for (gauge in gauges) {
        if (gauge.wide) {
            if (currentRow.isNotEmpty()) { rows.add(currentRow.toList()); currentRow = mutableListOf() }
            rows.add(listOf(gauge))
        } else {
            currentRow.add(gauge)
            if (currentRow.size == 2) { rows.add(currentRow.toList()); currentRow = mutableListOf() }
        }
    }
    if (currentRow.isNotEmpty()) rows.add(currentRow.toList())

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (gauge in row) {
                    GaugeItem(gauge, if (gauge.wide) Modifier.fillMaxWidth() else Modifier.weight(1f))
                }
                if (row.size == 1 && !row[0].wide) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GaugeItem(gauge: GaugeData, modifier: Modifier = Modifier) {
    val color = thresholdColor(gauge.value, gauge.thresh)
    val fraction = barFraction(gauge.value, gauge.thresh)
    val valueText = if (gauge.value < 10.0) fmtDouble(gauge.value, 2) else fmtDouble(gauge.value, 1)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariant.copy(alpha = 0.6f))
            .padding(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(gauge.label, fontSize = 10.sp, fontWeight = FontWeight.Black,
                color = TextSecondary, letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 14.sp, fontWeight = FontWeight.Black, color = color)
            Text(" ${gauge.unit}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(SurfaceVariant.copy(alpha = 0.6f)),
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

// ── Gauge computation ───────────────────────────────────────────────────────────

private const val G = 9.81

private fun computeGauges(
    acc: Vec3,
    rot: Vec3,
    prevAcc: Vec3?,
    sampleIntervalMs: Long,
    speed: Double,
    prevSpeed: Double,
): List<GaugeData> {
    val ax = acc.x
    val ay = acc.y
    val az = acc.z

    // Dead-zone: ignore noise below 0.15 m/s² (~0.015 G)
    val dax = if (abs(ax) < 0.15) 0.0 else ax
    val day = if (abs(ay) < 0.15) 0.0 else ay
    val daz = if (abs(az) < 0.15) 0.0 else az

    val lateralG = abs(dax) / G
    val lonG = max(abs(day), abs(daz)) / G

    // Braking vs acceleration from dominant longitudinal axis sign
    val primaryAxisSign = if (abs(day) >= abs(daz)) {
        if (day >= 0) 1.0 else -1.0
    } else {
        if (daz >= 0) 1.0 else -1.0
    }

    val brakingG: Double
    val accelG: Double
    // Also detect braking from GPS speed decrease
    val speedDrop = prevSpeed - speed
    if (lonG > 0.15 || speedDrop > 5.0) {
        if (primaryAxisSign > 0 || speedDrop > 5.0) {
            brakingG = lonG; accelG = 0.0
        } else {
            brakingG = 0.0; accelG = lonG
        }
    } else {
        brakingG = 0.0; accelG = 0.0
    }

    val verticalG = abs(daz) / G
    val gripG = sqrt(lateralG * lateralG + lonG * lonG)
    val turnRate = sqrt(rot.x * rot.x + rot.y * rot.y + rot.z * rot.z)

    // Jerk: rate of acceleration change (m/s³)
    val jerk = if (prevAcc != null && sampleIntervalMs > 0) {
        val dt = sampleIntervalMs / 1000.0
        val dx = ax - prevAcc.x
        val dy = ay - prevAcc.y
        val dz = az - prevAcc.z
        sqrt(dx * dx + dy * dy + dz * dz) / dt
    } else {
        0.0
    }

    return listOf(
        GaugeData("left-turn",  "LEFT TURN",  if (dax > 0) lateralG else 0.0,  "G",     THRESHOLDS["lateral"]!!,  faultType = "C1"),
        GaugeData("right-turn", "RIGHT TURN", if (dax < 0) lateralG else 0.0,  "G",    THRESHOLDS["lateral"]!!,  faultType = "C2"),
        GaugeData("stop-force", "STOP FORCE", brakingG,                          "G",    THRESHOLDS["braking"]!!,  wide = true, faultType = "A1"),
        GaugeData("accel",      "ACCEL",      accelG,                            "G",    THRESHOLDS["throttle"]!!, faultType = "A2"),
        GaugeData("grip",       "GRIP",       gripG,                             "G",    THRESHOLDS["grip"]!!,     faultType = "C3"),
        GaugeData("turn-rate",  "TURN RATE",  turnRate,                          "rad/s", THRESHOLDS["steering"]!!, faultType = "C4"),
        GaugeData("smoothness", "SMOOTHNESS", jerk,                              "m/s³", THRESHOLDS["jerk"]!!,     faultType = "D1"),
        GaugeData("vertical",   "VERTICAL",   verticalG,                         "G",    THRESHOLDS["vertical"]!!, faultType = "E1"),
    )
}
