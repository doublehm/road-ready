package com.roadready.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.ui.theme.*
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
data class SpeedDataPoint(
    val timestamp: Long = 0L,
    val speed: Float = 0f,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
)

@Serializable
data class SpeedLimitPoint(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val speed_limit: Float = 0f,
)

/**
 * Speed vs speed-limit bar chart with horizontal scrolling.
 *
 * Downsamples to ~80 data points for performance.
 * Speed bars turn red when exceeding the limit; limit is shown as a red overlay line.
 */
@Composable
fun SpeedGraph(
    speedData: List<SpeedDataPoint> = emptyList(),
    speedLimitData: List<SpeedLimitPoint> = emptyList(),
    height: Dp = 200.dp,
    modifier: Modifier = Modifier,
) {
    if (speedData.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("No speed data available", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    // Build and downsample data
    val (speeds, limits, hasLimits) = remember(speedData, speedLimitData) {
        buildChartData(speedData, speedLimitData)
    }

    val maxSpeed = remember(speeds, limits) {
        max(
            max(speeds.maxOrNull() ?: 0f, limits.filter { it > 0f }.maxOrNull() ?: 0f),
            60f
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface),
    ) {
        // Legend
        Row(
            modifier = Modifier.padding(12.dp, 12.dp, 12.dp, 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LegendItem(color = Primary, label = "Your Speed")
            if (hasLimits) {
                LegendItem(color = Error, label = "Speed Limit")
            }
        }

        // Chart area
        val density = LocalDensity.current
        val barWidthDp = 6.dp
        val chartWidthDp = barWidthDp * speeds.size + 40.dp // 40dp for y-axis
        val chartHeightDp = height - 40.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            // Y-axis labels
            Column(
                modifier = Modifier
                    .width(30.dp)
                    .height(chartHeightDp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = maxSpeed.roundToInt().toString(),
                    fontSize = 9.sp,
                    color = TextSecondary,
                )
                Text(
                    text = (maxSpeed / 2f).roundToInt().toString(),
                    fontSize = 9.sp,
                    color = TextSecondary,
                )
                Text(
                    text = "0",
                    fontSize = 9.sp,
                    color = TextSecondary,
                )
            }

            // Scrollable bar area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(chartHeightDp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                val barWidthPx = with(density) { barWidthDp.toPx() }
                val chartHeightPx = with(density) { chartHeightDp.toPx() }
                val totalWidthDp = barWidthDp * speeds.size

                Canvas(
                    modifier = Modifier
                        .width(totalWidthDp)
                        .height(chartHeightDp),
                ) {
                    for (i in speeds.indices) {
                        val spd = speeds[i]
                        val lim = limits[i]
                        val isOver = lim > 0f && spd > lim
                        val barH = max(1f, (spd / maxSpeed) * chartHeightPx)

                        // Speed bar
                        drawRect(
                            color = if (isOver) Error else Primary,
                            topLeft = Offset(i * barWidthPx, chartHeightPx - barH),
                            size = Size(barWidthPx - 1f, barH),
                        )

                        // Speed limit marker line
                        if (lim > 0f) {
                            val limY = chartHeightPx - (lim / maxSpeed) * chartHeightPx
                            drawRect(
                                color = Error.copy(alpha = 0.6f),
                                topLeft = Offset(i * barWidthPx, limY),
                                size = Size(barWidthPx, 2f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
    }
}

private data class ChartData(
    val speeds: List<Float>,
    val limits: List<Float>,
    val hasLimits: Boolean,
)

private fun buildChartData(
    speedData: List<SpeedDataPoint>,
    speedLimitData: List<SpeedLimitPoint>,
): ChartData {
    val dataSource = speedData.map { p ->
        val limit = if (speedLimitData.isNotEmpty()) {
            speedLimitData.minByOrNull { sl ->
                val dLat = sl.lat - p.lat
                val dLon = sl.lon - p.lon
                dLat * dLat + dLon * dLon
            }?.speed_limit ?: 0f
        } else {
            0f
        }
        p.speed to limit
    }

    // Downsample to ~80 points
    val maxPoints = 80
    val step = max(1, dataSource.size / maxPoints)
    val sampled = dataSource.filterIndexed { i, _ -> i % step == 0 }

    return ChartData(
        speeds = sampled.map { it.first.roundToInt().toFloat() },
        limits = sampled.map { it.second },
        hasLimits = sampled.any { it.second > 0f },
    )
}
