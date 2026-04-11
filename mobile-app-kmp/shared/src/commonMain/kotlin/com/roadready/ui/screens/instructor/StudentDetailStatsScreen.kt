package com.roadready.ui.screens.instructor

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
import org.koin.compose.koinInject

@Composable
fun StudentDetailStatsScreen(
    studentId: Int,
    studentName: String,
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    var isLoading by remember { mutableStateOf(true) }
    var rides by remember { mutableStateOf<List<DiagnosticRide>>(emptyList()) }

    LaunchedEffect(studentId) {
        apiClient.getDiagnosticRides(studentId = studentId).onSuccess {
            rides = it.sortedByDescending { r -> r.createdAt }
        }
        isLoading = false
    }

    if (isLoading) { LoadingOverlay(); return }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Primary) }
            Text(studentName, style = MaterialTheme.typography.headlineMedium)
        }

        if (rides.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) {
                Text("No ride data yet", style = MaterialTheme.typography.titleMedium, color = TextMuted)
            }
            return@Column
        }

        // Overview stats
        val avgScore = rides.mapNotNull { it.overallScore }.average()
        val bestScore = rides.mapNotNull { it.overallScore }.maxOrNull() ?: 0.0
        val latestScore = rides.first().overallScore ?: 0.0
        val totalDistance = rides.mapNotNull { it.distanceKm }.sum()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard("📊", "Avg Score", "${avgScore.toInt()}", Modifier.weight(1f))
            StatCard("🏆", "Best", "${bestScore.toInt()}", Modifier.weight(1f))
            StatCard("🕐", "Latest", "${latestScore.toInt()}", Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard("🔢", "Rides", "${rides.size}", Modifier.weight(1f))
            StatCard("📏", "Distance", "${totalDistance.toInt()} km", Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        // Category performance
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Performance Dashboard", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp))

                val avgBraking = rides.mapNotNull { it.brakingScore }.average()
                val avgSpeed = rides.mapNotNull { it.speedScore }.average()
                val avgCornering = rides.mapNotNull { it.corneringScore }.average()

                ProficiencyBar("Braking", avgBraking)
                ProficiencyBar("Speed Control", avgSpeed)
                ProficiencyBar("Cornering", avgCornering)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Recent rides list
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Recent Rides", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp))
                rides.take(5).forEach { ride ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(ride.createdAt?.take(10) ?: "—", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        val score = ride.overallScore ?: 0.0
                        Text("${score.toInt()}/100", style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                score >= 80 -> Accent; score >= 60 -> Warning; else -> Error
                            })
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatCard(emoji: String, label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
private fun ProficiencyBar(label: String, score: Double) {
    val color = when {
        score >= 80 -> Accent
        score >= 60 -> Warning
        else -> Error
    }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${score.toInt()}%", style = MaterialTheme.typography.bodyMedium, color = color)
        }
        LinearProgressIndicator(
            progress = { (score / 100f).toFloat() },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            color = color,
            trackColor = SurfaceVariant,
        )
    }
}
