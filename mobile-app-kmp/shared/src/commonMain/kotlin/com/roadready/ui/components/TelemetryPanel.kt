package com.roadready.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.roadready.data.repository.Vec3
import com.roadready.ui.theme.*
import com.roadready.ui.util.fmtDouble
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

data class Threshold(val green: Double, val yellow: Double, val red: Double)

val THRESHOLDS = mapOf(
    "lateral"  to Threshold(0.10, 0.25, 0.35),
    "braking"  to Threshold(0.08, 0.30, 0.40),
    "throttle" to Threshold(0.05, 0.20, 0.30),
    "vertical" to Threshold(0.05, 0.20, 0.30),
    "grip"     to Threshold(0.15, 0.35, 0.50),
    "steering" to Threshold(0.20, 0.50, 0.80),
    // Green/yellow bands kept low so the gauge shows activity during normal driving.
    // Red threshold raised so only genuinely rough inputs raise a D1 flag.
    "jerk"     to Threshold(2.5, 7.0, 12.0),
)

fun thresholdColor(value: Double, thresh: Threshold): Color = when {
    value >= thresh.red    -> Color(0xFFEF4444)
    value >= thresh.yellow -> Color(0xFFF59E0B)
    value >= thresh.green  -> Color(0xFF22C55E)
    else                   -> Color(0xFF475569)
}

fun barFraction(value: Double, thresh: Threshold): Float {
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

data class GaugeData(
    val id: String,
    val label: String,
    val icon: String,
    val value: Double,
    val unit: String,
    val thresh: Threshold,
    val faultType: String = "",
)

// ── Gauge display smoother (attack-decay EMA) ──────────────────────────────────

private class GaugeSmoother(
    private val attackAlpha: Double = 0.5,
    private val decayAlpha: Double = 0.18,
    private val snapToZeroThreshold: Double = 0.003,
) {
    private val values = mutableMapOf<String, Double>()

    fun smooth(gauges: List<GaugeData>): List<GaugeData> = gauges.map { g ->
        val prev = values[g.id] ?: 0.0
        val alpha = if (g.value > prev) attackAlpha else decayAlpha
        var smoothed = prev + alpha * (g.value - prev)
        if (smoothed < snapToZeroThreshold) smoothed = 0.0
        values[g.id] = smoothed
        g.copy(value = smoothed)
    }
}

// ── Main composable ─────────────────────────────────────────────────────────────

/**
 * HUD overlay that positions circular arc gauges around the screen edges:
 * - LEFT TURN → left-center
 * - RIGHT TURN → right-center
 * - BRAKE / ACCEL / GRIP / SMOOTH / VERT → bottom row
 *
 * Must be called inside a Box(fillMaxSize) so the alignment modifiers work.
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
    val smoother = remember { GaugeSmoother() }

    val rawGauges = computeGauges(acceleration, rotation, prevAcceleration, sampleIntervalMs, speed, prevSpeed)
    val gauges = smoother.smooth(rawGauges)

    LaunchedEffect(rawGauges) {
        if (onThresholdExceeded != null && isActive) {
            for (g in rawGauges) {
                if (g.value >= g.thresh.red) {
                    onThresholdExceeded(GaugeEvent(g.id, g.label, g.value, g.unit, g.faultType))
                }
            }
        }
    }

    val leftTurn  = gauges.firstOrNull { it.id == "left-turn"  } ?: return
    val rightTurn = gauges.firstOrNull { it.id == "right-turn" } ?: return
    val turnRate  = gauges.firstOrNull { it.id == "turn-rate"  } ?: return
    val braking   = gauges.firstOrNull { it.id == "stop-force" } ?: return
    val accel     = gauges.firstOrNull { it.id == "accel"      } ?: return
    val grip      = gauges.firstOrNull { it.id == "grip"       } ?: return
    val smooth    = gauges.firstOrNull { it.id == "smoothness" } ?: return
    val vertical  = gauges.firstOrNull { it.id == "vertical"   } ?: return

    Box(modifier = modifier.padding(horizontal = 16.dp)) {

        // ── LEFT RAIL: LEFT TURN · BRAKE · SMOOTH · TURN RATE ────────────────
        Column(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ArcGauge(leftTurn,  sizeDp = 72.dp)
            ArcGauge(braking,   sizeDp = 66.dp)
            ArcGauge(smooth,    sizeDp = 58.dp)
            ArcGauge(turnRate,  sizeDp = 54.dp)
        }

        // ── RIGHT RAIL: RIGHT TURN · ACCEL · GRIP · VERT ─────────────────────
        Column(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ArcGauge(rightTurn, sizeDp = 72.dp)
            ArcGauge(accel,     sizeDp = 58.dp)
            ArcGauge(grip,      sizeDp = 58.dp)
            ArcGauge(vertical,  sizeDp = 54.dp)
        }
    }
}

// ── Circular arc gauge widget ───────────────────────────────────────────────────

@Composable
private fun ArcGauge(
    gauge: GaugeData,
    sizeDp: Dp = 68.dp,
    modifier: Modifier = Modifier,
) {
    val animColor by animateColorAsState(
        targetValue = thresholdColor(gauge.value, gauge.thresh),
        animationSpec = tween(300),
    )
    val animFraction by animateFloatAsState(
        targetValue = barFraction(gauge.value, gauge.thresh),
        animationSpec = tween(250, easing = FastOutSlowInEasing),
    )

    val glowing = gauge.value >= gauge.thresh.yellow

    Box(
        modifier = modifier.size(sizeDp),
        contentAlignment = Alignment.Center,
    ) {
        // Glow halo when force is significant
        if (glowing) {
            Box(
                modifier = Modifier
                    .size(sizeDp)
                    .clip(CircleShape)
                    .background(animColor.copy(alpha = 0.15f)),
            )
        }

        // Dark background circle
        Box(
            modifier = Modifier
                .size(sizeDp)
                .clip(CircleShape)
                .background(Color(0xCC0F172A)), // near-black, 80% opaque
        )

        // Arc drawn over the background
        Canvas(modifier = Modifier.size(sizeDp)) {
            val stroke = size.width * 0.13f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)

            // Background track (dark grey)
            drawArc(
                color = Color(0xFF1E293B),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )

            // Value arc
            val sweep = 270f * animFraction
            if (sweep > 0.5f) {
                drawArc(
                    color = animColor,
                    startAngle = 135f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }

        // Label + value text + mini bar
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = gauge.icon,
                fontSize = (sizeDp.value * 0.26f).sp,
            )
            Text(
                text = gauge.label,
                fontSize = (sizeDp.value * 0.115f).sp,
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                lineHeight = (sizeDp.value * 0.115f).sp,
            )
            if (gauge.value >= 0.005) {
                Text(
                    text = if (gauge.value < 10.0) fmtDouble(gauge.value, 2)
                           else fmtDouble(gauge.value, 1),
                    fontSize = (sizeDp.value * 0.16f).sp,
                    color = animColor,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

// ── Gauge computation ───────────────────────────────────────────────────────────

private const val G = 9.81

// Dead-zone: reject sensor noise below this threshold (m/s²).
// TYPE_LINEAR_ACCELERATION already removes gravity; 0.08 handles residual noise.
private const val DEAD_ZONE = 0.08

fun computeGauges(
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

    val dax = if (abs(ax) < DEAD_ZONE) 0.0 else ax
    val day = if (abs(ay) < DEAD_ZONE) 0.0 else ay
    val daz = if (abs(az) < DEAD_ZONE) 0.0 else az

    val lateralG = abs(dax) / G

    // Portrait phone (screen facing driver):
    //   X axis → lateral  (left/right)   ✓
    //   Z axis → longitudinal (forward/back) — out of screen = car forward
    //   Y axis → vertical (up/down) — top of phone points up
    val lonG = abs(daz) / G

    // Sign of Z: positive = braking (decelerating), negative = accelerating.
    val primaryAxisSign = if (daz >= 0) 1.0 else -1.0

    val speedDrop = prevSpeed - speed
    val brakingG: Double
    val accelG: Double
    if (lonG > 0.15 || speedDrop > 5.0) {
        if (primaryAxisSign > 0 || speedDrop > 5.0) {
            brakingG = lonG; accelG = 0.0
        } else {
            brakingG = 0.0; accelG = lonG
        }
    } else {
        brakingG = 0.0; accelG = 0.0
    }

    // Y axis exclusively for vertical (road roughness / bumps).
    val verticalG = abs(day) / G

    // Grip uses lateral + longitudinal only — vertical excluded so bumps don't
    // inflate the grip reading.
    val gripG = sqrt(lateralG * lateralG + lonG * lonG)
    val turnRate = sqrt(rot.x * rot.x + rot.y * rot.y + rot.z * rot.z)

    // Jerk: rate-of-change of acceleration (m/s³).
    // Uses dead-zoned values, speed gate (>= 3 km/h), and noise floor (0.4 m/s³).
    val jerk = if (prevAcc != null && sampleIntervalMs > 0 && speed >= 3.0) {
        val dt = sampleIntervalMs / 1000.0
        val prevDax = if (abs(prevAcc.x) < DEAD_ZONE) 0.0 else prevAcc.x
        val prevDay = if (abs(prevAcc.y) < DEAD_ZONE) 0.0 else prevAcc.y
        val prevDaz = if (abs(prevAcc.z) < DEAD_ZONE) 0.0 else prevAcc.z
        val dx = dax - prevDax
        val dy = day - prevDay
        val dz = daz - prevDaz
        val raw = sqrt(dx * dx + dy * dy + dz * dz) / dt
        if (raw < 0.4) 0.0 else raw
    } else {
        0.0
    }

    return listOf(
        GaugeData("left-turn",  "LEFT",  "↰", if (dax > 0) lateralG else 0.0,  "G",      THRESHOLDS["lateral"]!!,  faultType = "C1"),
        GaugeData("right-turn", "RIGHT", "↱", if (dax < 0) lateralG else 0.0,  "G",      THRESHOLDS["lateral"]!!,  faultType = "C2"),
        GaugeData("turn-rate",  "TURN",  "↻", turnRate,                          "rad/s", THRESHOLDS["steering"]!!, faultType = "C4"),
        GaugeData("stop-force", "BRAKE", "🛑", brakingG,                          "G",     THRESHOLDS["braking"]!!,  faultType = "A1"),
        GaugeData("accel",      "ACCEL", "⚡", accelG,                            "G",     THRESHOLDS["throttle"]!!, faultType = "A2"),
        GaugeData("grip",       "GRIP",  "⊗", gripG,                             "G",     THRESHOLDS["grip"]!!,     faultType = "C3"),
        GaugeData("smoothness", "SMOOTH","〰", jerk,                              "m/s³",  THRESHOLDS["jerk"]!!,     faultType = "D1"),
        GaugeData("vertical",   "VERT",  "↕", verticalG,                         "G",     THRESHOLDS["vertical"]!!, faultType = "E1"),
    )
}
