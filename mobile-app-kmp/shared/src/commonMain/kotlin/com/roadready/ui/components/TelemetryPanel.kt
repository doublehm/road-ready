package com.roadready.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.ui.theme.*
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Format a float to [decimals] places without String.format (unavailable in common). */
private fun formatFloat(value: Float, decimals: Int): String {
    val factor = 10f.pow(decimals)
    val rounded = (value * factor).roundToInt() / factor
    val parts = rounded.toString().split(".")
    val intPart = parts[0]
    val fracPart = (parts.getOrElse(1) { "" }).take(decimals).padEnd(decimals, '0')
    return "$intPart.$fracPart"
}

private const val G = 9.81f

/** Physics-based thresholds — must mirror diagnostic_evaluator.py */
private data class Threshold(val green: Float, val yellow: Float, val red: Float)

private val THRESHOLDS = mapOf(
    "lateral" to Threshold(0.10f, 0.25f, 0.35f),
    "braking" to Threshold(0.08f, 0.30f, 0.40f),
    "throttle" to Threshold(0.05f, 0.20f, 0.30f),
    "vertical" to Threshold(0.05f, 0.20f, 0.30f),
    "grip" to Threshold(0.15f, 0.35f, 0.50f),
    "steering" to Threshold(0.20f, 0.50f, 0.80f),
    "jerk" to Threshold(1.5f, 3.0f, 5.0f),
)

private fun thresholdColor(value: Float, thresh: Threshold): Color = when {
    value >= thresh.red -> Error
    value >= thresh.yellow -> Warning
    value >= thresh.green -> Success
    else -> TextMuted
}

private fun barFraction(value: Float, thresh: Threshold): Float {
    val max = thresh.red * 1.5f
    return (value / max).coerceIn(0f, 1f)
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "$mins:${if (secs < 10) "0" else ""}$secs"
}

private fun speedColor(speed: Float, limit: Float?): Color {
    if (limit == null || limit <= 0f) return TextSecondary
    val excess = speed - limit
    return when {
        excess > 10f -> Error
        excess > 0f -> Warning
        else -> Success
    }
}

@Serializable
data class AccelerationReading(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
)

@Serializable
data class TelemetryEvent(
    val type: String = "",
    val timestamp: Long = 0L,
    val severity: String = "medium",
    val value: Float = 0f,
    val description: String = "",
)

private data class GaugeData(
    val id: String,
    val label: String,
    val value: Float,
    val unit: String,
    val thresh: Threshold,
    val wide: Boolean = false,
)

/**
 * Real-time telemetry dashboard shown during an active ride.
 *
 * Displays ride stats (time, distance, speed, limit) and 8 force gauges
 * (left turn, right turn, stop force, accel, grip, turn rate, smoothness, vertical).
 */
@Composable
fun TelemetryPanel(
    elapsedSeconds: Int,
    speed: Float,
    speedLimit: Float? = null,
    acceleration: AccelerationReading = AccelerationReading(),
    prevAcceleration: AccelerationReading? = null,
    sampleIntervalMs: Int = 100,
    distance: Float = 0f,
    events: List<TelemetryEvent> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val gauges = remember(
        acceleration.x, acceleration.y, acceleration.z,
        prevAcceleration?.x, prevAcceleration?.y, prevAcceleration?.z,
        sampleIntervalMs,
    ) {
        computeGauges(acceleration, prevAcceleration, sampleIntervalMs)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Background.copy(alpha = 0.92f))
            .border(1.dp, SurfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(10.dp),
    ) {
        // ── Ride Stats Row ──
        StatsRow(elapsedSeconds, distance, speed, speedLimit)

        Spacer(modifier = Modifier.height(8.dp))

        // ── Force Gauges Grid ──
        GaugeGrid(gauges)
    }
}

@Composable
private fun StatsRow(
    elapsedSeconds: Int,
    distance: Float,
    speed: Float,
    speedLimit: Float?,
) {
    val spdColor = speedColor(speed, speedLimit)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(label = "TIME", value = formatTime(elapsedSeconds))
        StatDivider()
        StatCell(label = "DIST", value = "${formatFloat(distance, 2)} km")
        StatDivider()
        StatCell(label = "SPEED", value = speed.roundToInt().toString(), valueColor = spdColor, large = true)
        StatDivider()
        StatCell(label = "LIMIT", value = speedLimit?.roundToInt()?.toString() ?: "--")
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    large: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextMuted,
            letterSpacing = 1.sp,
        )
        Text(
            text = value,
            fontSize = if (large) 22.sp else 14.sp,
            fontWeight = FontWeight.Black,
            color = valueColor,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(SurfaceVariant.copy(alpha = 0.25f)),
    )
}

@Composable
private fun GaugeGrid(gauges: List<GaugeData>) {
    // Layout: wrap gauges in rows of 2, except "wide" gauges take full width
    val rows = mutableListOf<List<GaugeData>>()
    var currentRow = mutableListOf<GaugeData>()

    for (gauge in gauges) {
        if (gauge.wide) {
            if (currentRow.isNotEmpty()) {
                rows.add(currentRow.toList())
                currentRow = mutableListOf()
            }
            rows.add(listOf(gauge))
        } else {
            currentRow.add(gauge)
            if (currentRow.size == 2) {
                rows.add(currentRow.toList())
                currentRow = mutableListOf()
            }
        }
    }
    if (currentRow.isNotEmpty()) rows.add(currentRow.toList())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (gauge in row) {
                    GaugeItem(
                        gauge = gauge,
                        modifier = if (gauge.wide) Modifier.fillMaxWidth()
                        else Modifier.weight(1f),
                    )
                }
                // Pad single-item rows that aren't wide
                if (row.size == 1 && !row[0].wide) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GaugeItem(gauge: GaugeData, modifier: Modifier = Modifier) {
    val color = thresholdColor(gauge.value, gauge.thresh)
    val fraction = barFraction(gauge.value, gauge.thresh)
    val valueText = if (gauge.value < 10f) formatFloat(gauge.value, 2) else formatFloat(gauge.value, 1)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariant.copy(alpha = 0.6f))
            .padding(7.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = gauge.label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = TextSecondary,
                letterSpacing = 0.5.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = color,
            )
            Text(
                text = " ${gauge.unit}",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Threshold bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(SurfaceVariant.copy(alpha = 0.6f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

private fun computeGauges(
    acc: AccelerationReading,
    prevAcc: AccelerationReading?,
    sampleIntervalMs: Int,
): List<GaugeData> {
    val ax = acc.x
    val ay = acc.y
    val az = acc.z

    val lateralG = abs(ax) / G
    val lonG = max(abs(ay), abs(az)) / G

    // Determine braking vs acceleration from dominant-axis sign
    val primaryAxisSign = if (abs(ay) >= abs(az)) {
        if (ay >= 0f) 1f else -1f
    } else {
        if (az >= 0f) 1f else -1f
    }

    val brakingG: Float
    val accelG: Float
    if (lonG > 0.15f) {
        if (primaryAxisSign > 0f) {
            brakingG = lonG; accelG = 0f
        } else {
            brakingG = 0f; accelG = lonG
        }
    } else {
        brakingG = 0f; accelG = 0f
    }

    val verticalG = abs(az) / G
    val gripG = sqrt(lateralG * lateralG + lonG * lonG)
    val turnRate = 0f // gyroscope not available in common data class

    // Jerk (rate of acceleration change)
    val jerk = if (prevAcc != null && sampleIntervalMs > 0) {
        val dt = sampleIntervalMs / 1000f
        val dx = ax - prevAcc.x
        val dy = ay - prevAcc.y
        val dz = az - prevAcc.z
        sqrt(dx * dx + dy * dy + dz * dz) / dt
    } else {
        0f
    }

    return listOf(
        GaugeData("left-turn", "LEFT TURN", if (ax > 0.1f) lateralG else 0f, "G", THRESHOLDS["lateral"]!!),
        GaugeData("right-turn", "RIGHT TURN", if (ax < -0.1f) lateralG else 0f, "G", THRESHOLDS["lateral"]!!),
        GaugeData("stop-force", "STOP FORCE", brakingG, "G", THRESHOLDS["braking"]!!, wide = true),
        GaugeData("accel", "ACCEL", accelG, "G", THRESHOLDS["throttle"]!!),
        GaugeData("grip", "GRIP", gripG, "G", THRESHOLDS["grip"]!!),
        GaugeData("turn-rate", "TURN RATE", turnRate, "rad/s", THRESHOLDS["steering"]!!),
        GaugeData("smoothness", "SMOOTHNESS", jerk, "m/s³", THRESHOLDS["jerk"]!!),
        GaugeData("vertical", "VERTICAL", verticalG, "G", THRESHOLDS["vertical"]!!),
    )
}
