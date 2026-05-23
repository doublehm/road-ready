package com.roadready.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.ml.CoachingEvent
import com.roadready.ml.CoachingEventType
import com.roadready.ui.theme.*
import kotlinx.coroutines.delay

// ── Data Model ────────────────────────────────────────────────────────────────

sealed class InsightItem {
    abstract val id: String
    abstract val timestamp: Long
    abstract val label: String

    data class Advice(
        override val id: String,
        override val timestamp: Long,
        override val label: String,
        val message: String,
        val type: CoachingEventType,
    ) : InsightItem()

    data class Fault(
        override val id: String,
        override val timestamp: Long,
        override val label: String,
        val type: String,
        val count: Int,
        val severity: String
    ) : InsightItem()
}

// ── Colors ────────────────────────────────────────────────────────────────────

private val Indigo500 = Color(0xFF6366F1)
private val Indigo600 = Color(0xFF4F46E5)
private val Indigo700 = Color(0xFF4338CA)

// ── Main Component ────────────────────────────────────────────────────────────

@Composable
fun InsightRail(
    activeAdvice: CoachingEvent?,
    history: List<InsightItem>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.widthIn(max = 200.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // 1. Prominent Active Advice (The "Attention" piece)
        AnimatedVisibility(
            visible = activeAdvice != null,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            activeAdvice?.let {
                ActiveAdviceCard(it)
            }
        }

        // 2. The Persistent Timeline Stack
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Take the last 5 unique types of items to prevent overflow
            history.takeLast(5).forEach { item ->
                key(item.id) {
                    InsightBadge(item)
                }
            }
        }
    }
}

@Composable
private fun ActiveAdviceCard(event: CoachingEvent) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Indigo500, Indigo600)
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // AI Icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("✨", fontSize = 16.sp)
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column {
                Text(
                    text = event.type.label.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = event.type.message,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun InsightBadge(item: InsightItem) {
    val bgColor = when (item) {
        is InsightItem.Advice -> Indigo700.copy(alpha = 0.85f)
        is InsightItem.Fault -> {
            if (item.severity == "high" || item.severity == "error") 
                Error.copy(alpha = 0.85f)
            else 
                Warning.copy(alpha = 0.85f)
        }
    }

    val icon = when (item) {
        is InsightItem.Advice -> "✨"
        is InsightItem.Fault -> {
            when (item.type) {
                "A1" -> "🛑" // Hard Brake
                "A2" -> "⚡" // Hard Accel
                "C1", "C2", "C4" -> "转向" // Turn
                "D1" -> "〰" // Smoothness
                else -> "⚠️"
            }
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (item is InsightItem.Fault && item.count > 1) 
                    "${item.label} ×${item.count}" 
                else 
                    item.label,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}
