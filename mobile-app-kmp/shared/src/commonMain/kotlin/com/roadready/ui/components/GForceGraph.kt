package com.roadready.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.ui.theme.*
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max

@Serializable
data class AccelDataPoint(
    val timestamp: Double = 0.0,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
)

@Serializable
data class RotationDataPoint(
    val timestamp: Double = 0.0,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
)

// ── Combined G-Force overview (lateral + longitudinal) ──────────────────────────

/**
 * Legacy overview chart — kept for backward compatibility.
 */
@Composable
fun GForceGraph(
    accelData: List<AccelDataPoint> = emptyList(),
    height: Dp = 160.dp,
    modifier: Modifier = Modifier,
) {
    if (accelData.isEmpty()) {
        EmptyChart("No force data available", height, modifier)
        return
    }

    val sampledData = remember(accelData) {
        val step = max(1, accelData.size / 120)
        accelData.filterIndexed { i, _ -> i % step == 0 }
    }
    val lateral = sampledData.map { (abs(it.x) / 9.81).toFloat() }
    val longit  = sampledData.map { (abs(it.z) / 9.81).toFloat() }

    ForceLineChartInternal(
        title = "G-Force Overview",
        series = listOf(
            LineSeries("Lateral (turns)", Primary, lateral),
            LineSeries("Longitudinal (braking/accel)", Secondary, longit),
        ),
        redThreshold = 0.35f,
        yellowThreshold = 0.25f,
        yLabel = "G",
        height = height,
        modifier = modifier,
    )
}

// ── Separate force charts ───────────────────────────────────────────────────────

/**
 * Renders four individual force charts stacked vertically:
 *   1. Lateral G  (cornering — left/right)
 *   2. Longitudinal G  (braking and acceleration)
 *   3. Vertical G  (road roughness / bumps)
 *   4. Turn Rate (deg/s from gyroscope)
 *
 * Pass [accelData] from the ride's `acceleration_data` field and [rotationData]
 * from `rotation_data`. Either can be empty — that chart is omitted.
 */
@Composable
fun SeparateForceCharts(
    accelData: List<AccelDataPoint>,
    rotationData: List<RotationDataPoint> = emptyList(),
    chartHeight: Dp = 140.dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {

        if (accelData.isNotEmpty()) {
            val sampled = remember(accelData) {
                val step = max(1, accelData.size / 150)
                accelData.filterIndexed { i, _ -> i % step == 0 }
            }

            // 1. Lateral G — cornering forces (X axis)
            ForceLineChartInternal(
                title = "Lateral G — Cornering",
                series = listOf(
                    LineSeries(
                        label = "Left turns",
                        color = Color(0xFF60A5FA),
                        values = sampled.map { (if (it.x > 0) abs(it.x) else 0.0).div(9.81).toFloat() },
                    ),
                    LineSeries(
                        label = "Right turns",
                        color = Color(0xFFA78BFA),
                        values = sampled.map { (if (it.x < 0) abs(it.x) else 0.0).div(9.81).toFloat() },
                    ),
                ),
                redThreshold = 0.35f,
                yellowThreshold = 0.25f,
                yLabel = "G",
                height = chartHeight,
                showThresholdBands = true,
            )

            Spacer(Modifier.height(4.dp))

            // 2. Longitudinal G — braking and acceleration (Z axis = forward/back)
            ForceLineChartInternal(
                title = "Longitudinal G — Braking / Acceleration",
                series = listOf(
                    LineSeries(
                        label = "Braking",
                        color = Color(0xFFF87171),
                        values = sampled.map { p ->
                            val lon = abs(p.z) / 9.81
                            if (p.z >= 0) lon.toFloat() else 0f
                        },
                    ),
                    LineSeries(
                        label = "Acceleration",
                        color = Color(0xFF34D399),
                        values = sampled.map { p ->
                            val lon = abs(p.z) / 9.81
                            if (p.z < 0) lon.toFloat() else 0f
                        },
                    ),
                ),
                redThreshold = 0.40f,
                yellowThreshold = 0.30f,
                yLabel = "G",
                height = chartHeight,
                showThresholdBands = true,
            )

            Spacer(Modifier.height(4.dp))

            // 3. Vertical G — road roughness (Y axis = up/down)
            ForceLineChartInternal(
                title = "Vertical G — Road Quality",
                series = listOf(
                    LineSeries(
                        label = "Vertical",
                        color = Color(0xFFFBBF24),
                        values = sampled.map { (abs(it.y) / 9.81).toFloat() },
                    ),
                ),
                redThreshold = 0.30f,
                yellowThreshold = 0.20f,
                yLabel = "G",
                height = chartHeight,
                showThresholdBands = true,
            )
        }

        // 4. Turn Rate — from gyroscope
        if (rotationData.isNotEmpty()) {
            val sampledRot = remember(rotationData) {
                val step = max(1, rotationData.size / 150)
                rotationData.filterIndexed { i, _ -> i % step == 0 }
            }
            ForceLineChartInternal(
                title = "Turn Rate — Steering Input",
                series = listOf(
                    LineSeries(
                        label = "Rate (rad/s)",
                        color = Color(0xFF38BDF8),
                        values = sampledRot.map {
                            Math.sqrt(it.x * it.x + it.y * it.y + it.z * it.z).toFloat()
                        },
                    ),
                ),
                redThreshold = 0.80f,
                yellowThreshold = 0.50f,
                yLabel = "rad/s",
                height = chartHeight,
                showThresholdBands = true,
            )
        }
    }
}

// ── Internal line chart engine ──────────────────────────────────────────────────

private data class LineSeries(
    val label: String,
    val color: Color,
    val values: List<Float>,
)

@Composable
private fun ForceLineChartInternal(
    title: String,
    series: List<LineSeries>,
    redThreshold: Float,
    yellowThreshold: Float,
    yLabel: String,
    height: Dp,
    modifier: Modifier = Modifier,
    showThresholdBands: Boolean = false,
) {
    val maxVal = remember(series) {
        val dataMax = series.flatMap { it.values }.maxOrNull() ?: 0f
        max(dataMax * 1.1f, redThreshold * 1.3f).coerceAtLeast(0.1f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A)),
    ) {
        // Title + legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                series.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(width = 16.dp, height = 3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(s.color),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(s.label, fontSize = 9.sp, color = TextMuted)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(start = 4.dp, end = 12.dp, bottom = 10.dp),
        ) {
            // Y-axis labels
            Column(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text("%.2f".format(maxVal), fontSize = 8.sp, color = Color(0xFF475569))
                Text("%.2f".format(maxVal / 2f), fontSize = 8.sp, color = Color(0xFF475569))
                Text("0", fontSize = 8.sp, color = Color(0xFF475569))
            }

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                val w = size.width
                val h = size.height

                // Threshold bands (subtle background zones)
                if (showThresholdBands) {
                    val redY = h - (redThreshold / maxVal * h)
                    val yellowY = h - (yellowThreshold / maxVal * h)
                    drawRect(
                        color = Color(0x18EF4444),
                        topLeft = Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(w, redY),
                    )
                    drawRect(
                        color = Color(0x14F59E0B),
                        topLeft = Offset(0f, redY),
                        size = androidx.compose.ui.geometry.Size(w, yellowY - redY),
                    )
                }

                // Grid lines
                repeat(3) { i ->
                    val y = h * (i + 1) / 4f
                    drawLine(
                        color = Color(0x1ACBD5E1),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                    )
                }

                // Threshold lines
                val redY = h - (redThreshold / maxVal * h)
                val yellowY = h - (yellowThreshold / maxVal * h)
                drawLine(Color(0x60EF4444), Offset(0f, redY), Offset(w, redY), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                drawLine(Color(0x60F59E0B), Offset(0f, yellowY), Offset(w, yellowY), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))

                // Draw each series
                series.forEach { s ->
                    drawSeries(s.values, s.color, maxVal, w, h)
                }
            }
        }
    }
}

private fun DrawScope.drawSeries(
    values: List<Float>,
    color: Color,
    maxVal: Float,
    w: Float,
    h: Float,
) {
    if (values.size < 2) return

    val n = values.size
    val step = w / (n - 1).coerceAtLeast(1)

    // Filled gradient area
    val fillPath = Path().apply {
        moveTo(0f, h)
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = h - (v / maxVal * h).coerceIn(0f, h)
            if (i == 0) lineTo(x, y) else lineTo(x, y)
        }
        lineTo((n - 1) * step, h)
        close()
    }
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.0f)),
            startY = 0f,
            endY = h,
        ),
    )

    // Line
    val linePath = Path()
    values.forEachIndexed { i, v ->
        val x = i * step
        val y = h - (v / maxVal * h).coerceIn(0f, h)
        if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
    }
    drawPath(
        path = linePath,
        color = color,
        style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    // Peak dot (max value point)
    val peakIdx = values.indices.maxByOrNull { values[it] } ?: return
    val px = peakIdx * step
    val py = h - (values[peakIdx] / maxVal * h).coerceIn(0f, h)
    drawCircle(color, radius = 4f, center = Offset(px, py))
    drawCircle(Color.White.copy(alpha = 0.9f), radius = 2f, center = Offset(px, py))
}

// ── Helpers ─────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyChart(message: String, height: Dp, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
    }
}
