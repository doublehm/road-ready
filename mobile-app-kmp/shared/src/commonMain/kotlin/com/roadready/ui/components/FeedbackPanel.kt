package com.roadready.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.data.FaultCategories
import com.roadready.ui.theme.*
import kotlinx.serialization.Serializable

@Serializable
data class FeedbackEntry(
    val code: String = "",
    val label: String = "",
    val category: String = "",
    val count: Int = 0,
    val timestamps: List<FeedbackTimestamp> = emptyList(),
)

@Serializable
data class FeedbackTimestamp(
    val timestamp: Long = 0L,
    val elapsed: Float = 0f,
)

/** Category colors for the dot indicators. */
private val CATEGORY_COLORS = mapOf(
    "A" to Color(0xFF3B82F6),
    "B" to Color(0xFF8B5CF6),
    "C" to Color(0xFFEF4444),
    "D" to Color(0xFFF59E0B),
    "E" to Color(0xFF22C55E),
    "F" to Color(0xFF64748B),
)

private data class CriterionItem(
    val code: String,
    val label: String,
    val categoryId: String,
    val categoryTitle: String,
    val color: Color,
)

private val ALL_CRITERIA: List<CriterionItem> by lazy {
    FaultCategories.categories.flatMap { cat ->
        cat.items.map { item ->
            CriterionItem(
                code = item.code,
                label = item.label,
                categoryId = cat.id,
                categoryTitle = cat.title,
                color = CATEGORY_COLORS[cat.id] ?: TextMuted,
            )
        }
    }
}

/**
 * Bottom-sheet style panel for flagging driving criteria during an active ride.
 *
 * Features:
 * - Searchable fault code list from [FaultCategories]
 * - Recently used items appear first when not searching
 * - +/– counter controls for each flagged criterion
 * - Records timestamp when flagged
 *
 * Since Compose Multiplatform doesn't have a native `Modal` like RN,
 * this is a full-height composable meant to be placed inside a platform
 * `ModalBottomSheet`, dialog, or overlay.
 */
@Composable
fun FeedbackPanel(
    visible: Boolean,
    onClose: () -> Unit,
    feedbackCounts: Map<String, FeedbackEntry>,
    onUpdateCount: (code: String, delta: Int, metadata: FeedbackEntry?) -> Unit,
    elapsedSeconds: Int,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    var searchText by remember { mutableStateOf("") }
    var recentCodes by remember { mutableStateOf(listOf<String>()) }

    val filteredCriteria = remember(searchText, recentCodes) {
        if (searchText.isBlank()) {
            val recentItems = recentCodes.mapNotNull { code ->
                ALL_CRITERIA.find { it.code == code }
            }
            val recentSet = recentCodes.toSet()
            val rest = ALL_CRITERIA.filter { it.code !in recentSet }
            recentItems + rest
        } else {
            val query = searchText.lowercase()
            ALL_CRITERIA.filter {
                it.code.lowercase().contains(query) ||
                        it.label.lowercase().contains(query) ||
                        it.categoryTitle.lowercase().contains(query)
            }
        }
    }

    val totalFlags = feedbackCounts.values.sumOf { it.count }
    val flaggedItems = feedbackCounts.values.filter { it.count > 0 }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Surface)
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.dp, color = Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⚑", fontSize = 20.sp, color = Warning)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Flag Criteria",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            if (totalFlags > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Warning)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = totalFlags.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Text("✕", fontSize = 22.sp, color = TextSecondary)
            }
        }

        Divider(color = SurfaceVariant, thickness = 1.dp)

        // ── Search ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceVariant)
                .border(1.dp, SurfaceVariant, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔍", fontSize = 16.sp, color = TextMuted)
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = TextPrimary),
                cursorBrush = SolidColor(Primary),
                decorationBox = { innerTextField ->
                    if (searchText.isEmpty()) {
                        Text(
                            "Search code or name (e.g. A1, Mirror, Speed)...",
                            fontSize = 15.sp,
                            color = TextMuted,
                        )
                    }
                    innerTextField()
                },
            )
            if (searchText.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { searchText = "" }
                        .padding(4.dp),
                ) {
                    Text("✕", fontSize = 14.sp, color = TextMuted)
                }
            }
        }

        // ── Flagged items summary ──
        if (flaggedItems.isNotEmpty() && searchText.isBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Warning.copy(alpha = 0.1f))
                    .border(1.dp, Warning, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = "FLAGGED ($totalFlags)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Warning,
                )
                Spacer(modifier = Modifier.height(8.dp))

                for (item in flaggedItems) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.code,
                            fontWeight = FontWeight.Bold,
                            color = Warning,
                            fontSize = 13.sp,
                            modifier = Modifier.width(30.dp),
                        )
                        Text(
                            text = item.label,
                            fontSize = 14.sp,
                            color = Warning,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        // Counter controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onUpdateCount(item.code, -1, null) }
                                    .padding(4.dp),
                            ) {
                                Text("➖", fontSize = 18.sp, color = Error)
                            }
                            Text(
                                text = item.count.toString(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        onUpdateCount(item.code, 1, FeedbackEntry(
                                            code = item.code,
                                            label = item.label,
                                            category = item.category,
                                        ))
                                    }
                                    .padding(4.dp),
                            ) {
                                Text("➕", fontSize = 18.sp, color = AccentLight)
                            }
                        }
                    }
                    Divider(color = Color.White.copy(alpha = 0.05f))
                }
            }
        }

        // ── Criteria list ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (filteredCriteria.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No matching criteria", fontSize = 15.sp, color = TextSecondary)
                }
            } else {
                for (item in filteredCriteria) {
                    CriterionRow(
                        item = item,
                        count = feedbackCounts[item.code]?.count ?: 0,
                        onTap = {
                            onUpdateCount(item.code, 1, FeedbackEntry(
                                code = item.code,
                                label = item.label,
                                category = item.categoryId,
                            ))
                            recentCodes = (listOf(item.code) +
                                    recentCodes.filter { it != item.code }).take(8)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CriterionRow(
    item: CriterionItem,
    count: Int,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Category color dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(item.color),
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                fontSize = 15.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${item.code} - ${item.categoryTitle}",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 1.dp),
            )
        }

        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Error)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text("➕", fontSize = 24.sp, color = Accent)
    }

    Divider(color = SurfaceVariant, thickness = 1.dp)
}
