package com.roadready.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.ui.theme.*
import kotlin.math.roundToInt

/**
 * Circular score gauge for displaying scores 0–100.
 *
 * Color-coded: green ≥ 80, yellow ≥ 60, red < 60.
 * Draws a 270° arc (from 135° to 405°) that fills proportionally to [score].
 */
@Composable
fun ScoreGauge(
    score: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    label: String = "",
) {
    val clampedScore = score.coerceIn(0f, 100f)
    val color = when {
        clampedScore >= 80f -> Success
        clampedScore >= 60f -> Warning
        else -> Error
    }
    val backgroundArcColor = SurfaceVariant.copy(alpha = 0.4f)
    val sweepAngle = (clampedScore / 100f) * 270f
    val strokeWidthFraction = 0.08f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size),
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val strokeWidth = this.size.minDimension * strokeWidthFraction
                val arcSize = Size(
                    this.size.width - strokeWidth,
                    this.size.height - strokeWidth,
                )
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                // Background track (full 270°)
                drawArc(
                    color = backgroundArcColor,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                // Filled arc
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            // Score text in center
            Text(
                text = clampedScore.roundToInt().toString(),
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.3f).sp,
            )
        }

        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
