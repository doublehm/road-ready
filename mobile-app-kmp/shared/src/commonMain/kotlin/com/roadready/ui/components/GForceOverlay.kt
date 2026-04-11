package com.roadready.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.roadready.ui.theme.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private fun formatGForce(value: Float): String {
    val rounded = (value * 10f).roundToInt() / 10f
    val parts = rounded.toString().split(".")
    return "${parts[0]}.${(parts.getOrElse(1) { "0" }).take(1).padEnd(1, '0')}"
}

private const val G = 9.81f

// Force thresholds (in G)
private const val DEAD_ZONE_G = 0.10f
private const val YELLOW_CEIL_G = 0.25f
private const val RED_FLOOR_G = 0.40f
private const val MAX_DISPLAY_G = 0.80f

private const val GLOW_LAYERS = 5
private const val LAYER_FRACTION = 0.025f // each layer = 2.5% of dimension

/**
 * RGB color interpolated from amber → red as G-force increases.
 */
private fun forceColor(g: Float): Color {
    if (g <= YELLOW_CEIL_G) return Color(0xFFF59E0B) // amber
    if (g >= RED_FLOOR_G) return Color(0xFFEF4444)   // red
    val t = (g - YELLOW_CEIL_G) / (RED_FLOOR_G - YELLOW_CEIL_G)
    val r = (245f + (239f - 245f) * t).roundToInt().coerceIn(0, 255)
    val gVal = (158f + (68f - 158f) * t).roundToInt().coerceIn(0, 255)
    val b = (11f + (68f - 11f) * t).roundToInt().coerceIn(0, 255)
    return Color(r, gVal, b)
}

/**
 * Base opacity for the innermost (brightest) glow layer.
 */
private fun baseOpacity(g: Float): Float {
    if (g <= DEAD_ZONE_G) return 0f
    val t = min((g - DEAD_ZONE_G) / (MAX_DISPLAY_G - DEAD_ZONE_G), 1f)
    return 0.2f + t * 0.6f
}

private enum class TurnDirection { LEFT, RIGHT }

/**
 * Full-screen overlay that lights up edges based on real-time accelerometer data.
 *
 * - Cornering → left or right edge glows (amber → red).
 * - Braking / longitudinal force → top edge glows (amber → red).
 * - Center displays the total G-force magnitude.
 *
 * The overlay does not consume touch events; layer it above content that should
 * remain interactive.
 */
@Composable
fun GForceOverlay(
    accelerationX: Float,
    accelerationY: Float,
    accelerationZ: Float,
    modifier: Modifier = Modifier,
) {
    val lateralG = abs(accelerationX) / G
    val brakingG = max(abs(accelerationY), abs(accelerationZ)) / G
    val totalG = sqrt(
        (accelerationX * accelerationX +
                accelerationY * accelerationY +
                accelerationZ * accelerationZ)
    ) / G

    val turnDir: TurnDirection? = remember(accelerationX, lateralG) {
        when {
            lateralG <= DEAD_ZONE_G -> null
            accelerationX > 0 -> TurnDirection.LEFT
            else -> TurnDirection.RIGHT
        }
    }

    val latOpacity = baseOpacity(lateralG)
    val brkOpacity = baseOpacity(brakingG)
    val latColor = forceColor(lateralG)
    val brkColor = forceColor(brakingG)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val layerW = w * LAYER_FRACTION
            val layerH = h * LAYER_FRACTION

            // Left edge glow (turning left)
            if (turnDir == TurnDirection.LEFT) {
                for (i in 0 until GLOW_LAYERS) {
                    val alpha = latOpacity * (1f - i.toFloat() / GLOW_LAYERS)
                    drawRect(
                        color = latColor.copy(alpha = alpha),
                        topLeft = Offset(i * layerW, 0f),
                        size = Size(layerW, h),
                    )
                }
            }

            // Right edge glow (turning right)
            if (turnDir == TurnDirection.RIGHT) {
                for (i in 0 until GLOW_LAYERS) {
                    val alpha = latOpacity * (1f - i.toFloat() / GLOW_LAYERS)
                    drawRect(
                        color = latColor.copy(alpha = alpha),
                        topLeft = Offset(w - (i + 1) * layerW, 0f),
                        size = Size(layerW, h),
                    )
                }
            }

            // Top edge glow (braking / longitudinal)
            if (brkOpacity > 0f) {
                for (i in 0 until GLOW_LAYERS) {
                    val alpha = brkOpacity * (1f - i.toFloat() / GLOW_LAYERS)
                    drawRect(
                        color = brkColor.copy(alpha = alpha),
                        topLeft = Offset(0f, i * layerH),
                        size = Size(w, layerH),
                    )
                }
            }
        }

        // G-force magnitude in center
        if (totalG > DEAD_ZONE_G) {
            val magColor = forceColor(totalG)
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatGForce(totalG),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = magColor,
                )
                Text(
                    text = "G",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = magColor.copy(alpha = 0.7f),
                )
            }
        }
    }
}
