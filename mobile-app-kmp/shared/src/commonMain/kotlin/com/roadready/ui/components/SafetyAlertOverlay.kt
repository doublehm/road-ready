package com.roadready.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ZoneAmber = Color(0xFFFFC107)
private val ZoneBg = Color(0xFF1A1200).copy(alpha = 0.95f)

@Composable
fun SafetyAlertOverlay(
    zoneType: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val isVisible = isActive && (zoneType == "school" || zoneType == "playground")

    val pulse = rememberInfiniteTransition(label = "zonePulse")
    val borderAlpha by pulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderAlpha"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn(tween(200)),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        val (icon, headline, detail) = when (zoneType) {
            "school"     -> Triple(Icons.Default.School,     "School Zone",     "8 AM – 5 PM · Mon–Fri")
            "playground" -> Triple(Icons.Default.ChildCare,  "Playground Zone", "Dawn to Dusk")
            else         -> Triple(Icons.Default.Warning,    "Safety Zone",     "Reduced Speed")
        }

        Row(
            modifier = Modifier
                .wrapContentWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(ZoneBg)
                .border(1.dp, ZoneAmber.copy(alpha = borderAlpha), RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon in frosted amber circle
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(ZoneAmber.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ZoneAmber,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Zone name + active hours
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = headline,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZoneAmber,
                    letterSpacing = 0.3.sp
                )
                Text(
                    text = detail,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = ZoneAmber.copy(alpha = 0.65f)
                )
            }

            // Circular road-sign badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(3.dp, Color(0xFFDC2626), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "30",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    letterSpacing = (-0.5).sp
                )
            }
        }
    }
}
