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

/**
 * G-Force analysis chart showing Lateral (X) and Longitudinal (Y/Z) forces.
 */
@Composable
fun GForceGraph(
    accelData: List<AccelDataPoint> = emptyList(),
    height: Dp = 200.dp,
    modifier: Modifier = Modifier,
) {
    if (accelData.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("No force data available", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    // Downsample data for performance
    val sampledData = remember(accelData) {
        val maxPoints = 100
        val step = max(1, accelData.size / maxPoints)
        accelData.filterIndexed { i, _ -> i % step == 0 }
    }

    val maxG = remember(sampledData) {
        val maxVal = sampledData.map { 
            max(abs(it.x), max(abs(it.y), abs(it.z))) 
        }.maxOrNull() ?: 1.0
        max(maxVal / 9.81, 0.5) // Convert to G and ensure a minimum scale
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
            LegendItem(color = Primary, label = "Lateral (Turns)")
            LegendItem(color = Secondary, label = "Longitudinal (Braking/Accel)")
        }

        val density = LocalDensity.current
        val chartHeightDp = height - 40.dp
        val barWidthDp = 4.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            // Y-axis
            Column(
                modifier = Modifier
                    .width(30.dp)
                    .height(chartHeightDp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "%.1fG".format(maxG), fontSize = 8.sp, color = TextSecondary)
                Text(text = "0G", fontSize = 8.sp, color = TextSecondary)
                Text(text = "-%.1fG".format(maxG), fontSize = 8.sp, color = TextSecondary)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(chartHeightDp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                val barWidthPx = with(density) { barWidthDp.toPx() }
                val chartHeightPx = with(density) { chartHeightDp.toPx() }
                val centerY = chartHeightPx / 2
                val totalWidthDp = barWidthDp * sampledData.size

                Canvas(
                    modifier = Modifier
                        .width(totalWidthDp)
                        .height(chartHeightDp),
                ) {
                    // Zero line
                    drawLine(
                        color = GlassStroke,
                        start = Offset(0f, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = 1f
                    )

                    for (i in sampledData.indices) {
                        val p = sampledData[i]
                        val latG = (p.x / 9.81)
                        val lonG = (max(abs(p.y), abs(p.z)) / 9.81) * (if (p.y > 0) 1 else -1)

                        val latH = (latG / maxG) * (chartHeightPx / 2)
                        val lonH = (lonG / maxG) * (chartHeightPx / 2)

                        // Lateral bar
                        drawRect(
                            color = Primary.copy(alpha = 0.7f),
                            topLeft = Offset(i * barWidthPx, centerY - latH.toFloat().coerceAtLeast(0f)),
                            size = Size(barWidthPx / 2, abs(latH).toFloat().coerceAtLeast(1f)),
                        )

                        // Longitudinal bar
                        drawRect(
                            color = Secondary.copy(alpha = 0.7f),
                            topLeft = Offset(i * barWidthPx + barWidthPx / 2, centerY - lonH.toFloat().coerceAtLeast(0f)),
                            size = Size(barWidthPx / 2, abs(lonH).toFloat().coerceAtLeast(1f)),
                        )
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
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
    }
}
