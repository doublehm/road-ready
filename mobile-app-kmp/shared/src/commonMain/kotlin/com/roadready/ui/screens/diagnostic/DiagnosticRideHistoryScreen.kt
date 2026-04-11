package com.roadready.ui.screens.diagnostic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.data.model.CategoryAverages
import com.roadready.data.model.DiagnosticRide
import com.roadready.data.model.ProgressTrends
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.theme.*
import com.roadready.ui.util.fmtDouble
import kotlinx.coroutines.async
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.compose.koinInject

private enum class RideFilter(val label: String) { All("All"), Passed("Passed"), Failed("Failed") }

@Composable
fun DiagnosticRideHistoryScreen(
    onSelectRide: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    var isLoading by remember { mutableStateOf(true) }
    var rides by remember { mutableStateOf<List<DiagnosticRide>>(emptyList()) }
    var trends by remember { mutableStateOf<ProgressTrends?>(null) }
    var filter by remember { mutableStateOf(RideFilter.All) }

    LaunchedEffect(Unit) {
        val ridesDeferred = async {
            apiClient.getDiagnosticRides().getOrNull()
                ?.sortedByDescending { it.createdAt }
                .orEmpty()
        }
        val trendsDeferred = async {
            apiClient.getProgressTrends().getOrNull()
        }
        rides = ridesDeferred.await()
        trends = trendsDeferred.await()
        isLoading = false
    }

    val filteredRides = remember(rides, filter) {
        when (filter) {
            RideFilter.All -> rides
            RideFilter.Passed -> rides.filter { it.passed == true }
            RideFilter.Failed -> rides.filter { it.passed == false }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Surface,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("←", color = TextPrimary, fontSize = 20.sp) }
                Text(
                    "Ride History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
            }
        }

        if (isLoading) { LoadingOverlay(); return }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Summary stats card
            trends?.let { t ->
                if (t.totalRides > 0) {
                    item(key = "summary") { SummaryCard(t) }
                }
            }

            // Filter bar
            item(key = "filter") { FilterBar(filter) { filter = it } }

            // Ride cards
            if (filteredRides.isEmpty()) {
                item(key = "empty") { EmptyState(filter) }
            } else {
                items(filteredRides, key = { it.id }) { ride ->
                    RideCard(ride) { onSelectRide(ride.id) }
                }
            }
        }
    }
}

// ─── Summary Card ────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(trends: ProgressTrends) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Total Rides | Pass Rate | Recent Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SummaryItem(value = "${trends.totalRides}", label = "Total Rides")
                SummaryItem(value = "${fmtDouble(trends.passRate, 0)}%", label = "Pass Rate")
                SummaryItem(
                    value = fmtDouble(trends.recentScore, 0),
                    label = "Recent Score",
                    valueColor = AccentLight,
                )
            }

            Spacer(Modifier.height(20.dp))

            // Row 2: Avg Duration | Total Distance | Best Overall
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SummaryItem(value = "${fmtDouble(trends.avgDurationMinutes, 0)}m", label = "Avg Duration")
                SummaryItem(value = "${fmtDouble(trends.totalDistanceKm, 1)}km", label = "Total Distance")
                SummaryItem(value = fmtDouble(trends.bestOverall, 0), label = "Best Overall")
            }

            // Category averages
            trends.categoryAverages?.let { cats ->
                Spacer(Modifier.height(24.dp))
                CategoryProgressBar("Braking", cats.braking)
                Spacer(Modifier.height(12.dp))
                CategoryProgressBar("Speed", cats.speed)
                Spacer(Modifier.height(12.dp))
                CategoryProgressBar("Cornering", cats.cornering)
            }

            // Improvement areas
            if (trends.improvementAreas.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = SurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📈 ", fontSize = 14.sp)
                    Text(
                        "Focus area: ${trends.improvementAreas.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(
    value: String,
    label: String,
    valueColor: androidx.compose.ui.graphics.Color = TextPrimary,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun CategoryProgressBar(label: String, score: Double) {
    val color = when {
        score >= 75 -> AccentLight
        score >= 60 -> Warning
        else -> Error
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text("${score.toInt()}", style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(SurfaceVariant),
        ) {
            val fraction = (score / 100.0).coerceIn(0.0, 1.0).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

// ─── Filter Bar ──────────────────────────────────────────────────────────────

@Composable
private fun FilterBar(current: RideFilter, onChange: (RideFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RideFilter.entries.forEach { f ->
            val isActive = f == current
            Surface(
                modifier = Modifier.clickable { onChange(f) },
                color = if (isActive) Primary else Surface,
                shape = RoundedCornerShape(20.dp),
                border = if (isActive) null else BorderStroke(1.dp, SurfaceVariant),
            ) {
                Text(
                    f.label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) TextPrimary else TextSecondary,
                )
            }
        }
    }
}

// ─── Ride Card ───────────────────────────────────────────────────────────────

@Composable
private fun RideCard(ride: DiagnosticRide, onClick: () -> Unit) {
    val score = ride.overallScore ?: 0.0
    val scoreColor = when {
        score >= 80 -> AccentLight
        score >= 60 -> Warning
        else -> Error
    }
    val flagCount = remember(ride.humanFeedback) { parseFlagCount(ride.humanFeedback) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Score badge (circle)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(scoreColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${score.toInt()}",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.width(12.dp))

            // Center content
            Column(modifier = Modifier.weight(1f)) {
                // Date + Pass/Fail badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        ride.createdAt?.take(10) ?: "",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    ride.passed?.let { passed ->
                        Surface(
                            color = if (passed) AccentLight else Error,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                if (passed) "PASSED" else "FAILED",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Stats row: duration · distance · ride type
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        ride.durationMinutes?.let { "⏱ ${it.toInt()} min" } ?: "⏱ --",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                    Text(
                        ride.distanceKm?.let { "📍 ${fmtDouble(it, 1)} km" } ?: "📍 --",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                    Text(
                        if (ride.rideType == "parent_supervised") "Parent" else "Instructor",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontStyle = FontStyle.Italic,
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Mini score bars + flag badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiniScoreBar("B", ride.brakingScore, Modifier.weight(1f))
                    MiniScoreBar("S", ride.speedScore, Modifier.weight(1f))
                    MiniScoreBar("C", ride.corneringScore, Modifier.weight(1f))
                    if (flagCount > 0) {
                        Surface(
                            color = Warning.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text("⚑", fontSize = 10.sp, color = Warning)
                                Text(
                                    "$flagCount",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Warning,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            Text("›", fontSize = 20.sp, color = TextMuted)
        }
    }
}

@Composable
private fun MiniScoreBar(label: String, score: Double?, modifier: Modifier = Modifier) {
    val value = score ?: 0.0
    val color = when {
        value >= 70 -> AccentLight
        value >= 50 -> Warning
        else -> Error
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SurfaceVariant),
        ) {
            val fraction = (value / 100.0).coerceIn(0.0, 1.0).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
        Text(
            "${value.toInt()}",
            fontSize = 9.sp,
            color = TextSecondary,
        )
    }
}

// ─── Empty State ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(filter: RideFilter) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🚗", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                if (filter == RideFilter.All) "No diagnostic rides yet"
                else "No ${filter.label.lowercase()} rides",
                fontSize = 16.sp,
                color = TextMuted,
            )
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun parseFlagCount(humanFeedback: String?): Int {
    if (humanFeedback.isNullOrBlank()) return 0
    return try {
        val array = lenientJson.parseToJsonElement(humanFeedback).jsonArray
        array.sumOf { element ->
            element.jsonObject["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        }
    } catch (_: Exception) {
        0
    }
}
