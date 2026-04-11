package com.roadready.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.roadready.data.FaultCategories
import com.roadready.ui.theme.*
import kotlinx.serialization.Serializable

@Serializable
data class HumanFeedbackItem(
    val code: String = "",
    val label: String = "",
    val category: String = "",
    val count: Int = 0,
    val timestamps: List<FeedbackTimestamp> = emptyList(),
)

/** Colors for category headers. */
private val CATEGORY_HEADER_COLORS = mapOf(
    "A" to Color(0xFF1E3A5F),
    "B" to Color(0xFF2D1B69),
    "C" to Color(0xFF5C1A1A),
    "D" to Color(0xFF5C3A0A),
    "E" to Color(0xFF1A3D2E),
    "F" to Color(0xFF1E293B),
)

/**
 * Post-ride summary of human-flagged and device-detected criteria.
 *
 * Groups feedback items by category (A–F) with count badges.
 * Human-observed categories (A–E) are shown first, followed by
 * device-detected (F).
 */
@Composable
fun HumanFeedbackSection(
    humanFeedback: List<HumanFeedbackItem> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (humanFeedback.isEmpty()) return

    val grouped = remember(humanFeedback) {
        humanFeedback.groupBy { item ->
            item.category.ifEmpty { item.code.firstOrNull()?.toString() ?: "?" }
        }
    }

    val totalFlags = humanFeedback.sumOf { it.count }
    val deviceItems = grouped["F"] ?: emptyList()
    val humanEntries = grouped.entries.filter { it.key != "F" }
    val hasDevice = deviceItems.isNotEmpty()
    val hasHuman = humanEntries.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Header ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("⚑", fontSize = 18.sp, color = Warning)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Supervisor Feedback",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Warning)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "$totalFlags flags",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Source indicators ──
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasHuman) {
                SourceTag(symbol = "👤", text = "Human Observed", color = Primary)
            }
            if (hasDevice) {
                SourceTag(symbol = "📱", text = "Device Detected", color = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Human-observed categories first ──
        for ((catId, items) in humanEntries) {
            CategoryCard(catId = catId, items = items)
            Spacer(modifier = Modifier.height(10.dp))
        }

        // ── Device-detected last ──
        if (hasDevice) {
            CategoryCard(catId = "F", items = deviceItems)
        }
    }
}

@Composable
private fun SourceTag(symbol: String, text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(symbol, fontSize = 12.sp, color = color)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun CategoryCard(catId: String, items: List<HumanFeedbackItem>) {
    val catDef = FaultCategories.categories.find { it.id == catId }
    val catTitle = catDef?.title ?: "Category $catId"
    val headerColor = CATEGORY_HEADER_COLORS[catId] ?: SurfaceVariant
    val catTotal = items.sumOf { it.count }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, SurfaceVariant, RoundedCornerShape(10.dp))
            .background(Surface),
    ) {
        // Category header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor)
                .padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = catTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = catTotal.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextSecondary,
            )
        }

        // Fault items
        Column(modifier = Modifier.padding(12.dp)) {
            for (item in items) {
                FaultItemRow(item)
                if (item != items.last()) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun FaultItemRow(item: HumanFeedbackItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 0.dp,
                color = Color.Transparent,
                shape = RoundedCornerShape(0.dp),
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Count badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Error),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.count.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = item.code,
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            }
        }

        // Timestamp tags
        if (item.timestamps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.padding(start = 38.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (t in item.timestamps) {
                    val mins = (t.elapsed / 60).toInt()
                    val secs = (t.elapsed % 60).toInt()
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceVariant)
                            .border(1.dp, SurfaceVariant, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "$mins:${if (secs < 10) "0" else ""}$secs",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                    }
                }
            }
        }
    }
}
