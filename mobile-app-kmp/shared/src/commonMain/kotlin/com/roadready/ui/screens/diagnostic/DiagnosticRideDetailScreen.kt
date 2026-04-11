package com.roadready.ui.screens.diagnostic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadready.data.model.DiagnosticRide
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.theme.*
import com.roadready.ui.util.fmtDouble
import org.koin.compose.koinInject

@Composable
fun DiagnosticRideDetailScreen(
    rideId: Int,
    onBack: () -> Unit,
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
            Text("Ride not found", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    val r = ride!!

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Primary) }
            Text("Ride Detail", style = MaterialTheme.typography.headlineMedium)
        }

        // Score header
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val score = r.overallScore ?: 0.0
                Text("${score.toInt()}", style = MaterialTheme.typography.displayLarge,
                    color = when {
                        score >= 80 -> Accent
                        score >= 60 -> Warning
                        else -> Error
                    })
                Text("Overall Score", style = MaterialTheme.typography.titleMedium, color = TextMuted)
                r.createdAt?.let {
                    Text(it.take(16).replace("T", " "), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Category scores
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Category Breakdown", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
                ScoreRow("🛑 Braking", r.brakingScore)
                ScoreRow("🚦 Speed Control", r.speedScore)
                ScoreRow("↩️ Cornering", r.corneringScore)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Trip details
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Trip Details", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                r.durationMinutes?.let { DetailRow("Duration", "${it.toInt()} min") }
                r.distanceKm?.let { DetailRow("Distance", "${fmtDouble(it, 2)} km") }
                r.rideType?.let { DetailRow("Type", it.replace("_", " ").replaceFirstChar { c -> c.uppercase() }) }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Route replay placeholder
        Card(
            modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗺️", style = MaterialTheme.typography.displayMedium)
                    Text("Route Replay", style = MaterialTheme.typography.titleMedium)
                    Text("Map visualization available on device", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        }

        // Evaluator notes
        r.evaluatorNotes?.let { notes ->
            if (notes.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Evaluator Notes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                        Text(notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Human feedback
        r.humanFeedback?.let { feedback ->
            if (feedback.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Supervisor Feedback", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                        Text(feedback, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ScoreRow(label: String, score: Double?) {
    val s = score ?: 0.0
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { (s / 100f).toFloat() },
                modifier = Modifier.width(100.dp),
                color = when {
                    s >= 80 -> Accent
                    s >= 60 -> Warning
                    else -> Error
                },
                trackColor = SurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text("${s.toInt()}%", style = MaterialTheme.typography.bodyMedium,
                color = when { s >= 80 -> Accent; s >= 60 -> Warning; else -> Error })
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
