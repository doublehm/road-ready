package com.roadready.ui.screens.diagnostic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roadready.data.model.DiagnosticRide
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun DiagnosticRideResultsScreen(
    rideId: Int,
    onViewDetail: (Int) -> Unit,
    onDone: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    var isLoading by remember { mutableStateOf(true) }
    var ride by remember { mutableStateOf<DiagnosticRide?>(null) }

    LaunchedEffect(rideId) {
        apiClient.getDiagnosticRide(rideId).onSuccess { ride = it }
        isLoading = false
    }

    if (isLoading) { LoadingOverlay(); return }
    if (ride == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Could not load results", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    val r = ride!!
    val score = r.overallScore ?: 0.0

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        // Score display
        Text(
            when {
                score >= 80 -> "🌟"
                score >= 60 -> "👍"
                score >= 40 -> "📈"
                else -> "💪"
            },
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "${score.toInt()}",
            style = MaterialTheme.typography.displayLarge,
            color = when {
                score >= 80 -> Accent
                score >= 60 -> Warning
                else -> Error
            },
        )
        Text("/ 100", style = MaterialTheme.typography.titleMedium, color = TextMuted)
        Text(
            when {
                score >= 80 -> "Excellent Drive!"
                score >= 60 -> "Good Effort!"
                score >= 40 -> "Room for Improvement"
                else -> "Keep Practicing"
            },
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(24.dp))

        // Category breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Category Scores", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp))
                CategoryBar("Braking", r.brakingScore ?: 0.0)
                CategoryBar("Speed Control", r.speedScore ?: 0.0)
                CategoryBar("Cornering", r.corneringScore ?: 0.0)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Ride info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ride Summary", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                r.durationSeconds?.let { InfoRow("Duration", "${it / 60}m ${it % 60}s") }
                r.distanceKm?.let { InfoRow("Distance", String.format("%.1f km", it)) }
                r.maxSpeedKmh?.let { InfoRow("Max Speed", "${it.toInt()} km/h") }
                r.averageSpeedKmh?.let { InfoRow("Avg Speed", "${it.toInt()} km/h") }
                r.eventCount?.let { InfoRow("Events", "$it flagged") }
            }
        }

        Spacer(Modifier.height(24.dp))

        PrimaryButton(text = "View Full Details", onClick = { onViewDetail(rideId) })
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDone) { Text("Return Home", color = Primary) }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CategoryBar(label: String, score: Double) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${score.toInt()}%", style = MaterialTheme.typography.bodyMedium,
                color = when {
                    score >= 80 -> Accent
                    score >= 60 -> Warning
                    else -> Error
                })
        }
        LinearProgressIndicator(
            progress = { (score / 100f).toFloat() },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            color = when {
                score >= 80 -> Accent
                score >= 60 -> Warning
                else -> Error
            },
            trackColor = SurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
