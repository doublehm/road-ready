package com.roadready.ui.screens.diagnostic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun DiagnosticRideHistoryScreen(
    onSelectRide: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    var isLoading by remember { mutableStateOf(true) }
    var rides by remember { mutableStateOf<List<DiagnosticRide>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        apiClient.getDiagnosticRides().onSuccess { rides = it.sortedByDescending { r -> r.createdAt } }
        isLoading = false
    }

    val tabs = listOf("All Rides", "Trends")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Primary) }
            Text("Ride History", style = MaterialTheme.typography.headlineMedium)
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Background,
            contentColor = Primary,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                    Text(title, modifier = Modifier.padding(12.dp))
                }
            }
        }

        if (isLoading) { LoadingOverlay(); return }

        when (selectedTab) {
            0 -> RideList(rides, onSelectRide)
            1 -> TrendsView(rides)
        }
    }
}

@Composable
private fun RideList(rides: List<DiagnosticRide>, onSelectRide: (Int) -> Unit) {
    if (rides.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🚗", style = MaterialTheme.typography.displayLarge)
                Text("No rides yet", style = MaterialTheme.typography.titleMedium)
                Text("Complete a diagnostic ride to see it here", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rides, key = { it.id }) { ride ->
                RideCard(ride) { onSelectRide(ride.id) }
            }
        }
    }
}

@Composable
private fun RideCard(ride: DiagnosticRide, onClick: () -> Unit) {
    val score = ride.overallScore ?: 0.0
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Score circle
            Surface(
                modifier = Modifier.size(48.dp),
                color = when {
                    score >= 80 -> Accent.copy(alpha = 0.2f)
                    score >= 60 -> Warning.copy(alpha = 0.2f)
                    else -> Error.copy(alpha = 0.2f)
                },
                shape = RoundedCornerShape(24.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${score.toInt()}", style = MaterialTheme.typography.titleMedium,
                        color = when {
                            score >= 80 -> Accent
                            score >= 60 -> Warning
                            else -> Error
                        })
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(ride.rideType ?: "Diagnostic Ride", style = MaterialTheme.typography.titleMedium)
                ride.createdAt?.let {
                    Text(it.take(10), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                ride.distanceKm?.let {
                    Text(String.format("%.1f km", it), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            Text("→", style = MaterialTheme.typography.titleLarge, color = Primary)
        }
    }
}

@Composable
private fun TrendsView(rides: List<DiagnosticRide>) {
    if (rides.size < 2) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📊", style = MaterialTheme.typography.displayLarge)
                Text("Need 2+ rides for trends", style = MaterialTheme.typography.titleMedium)
            }
        }
        return
    }

    val avgScore = rides.mapNotNull { it.overallScore }.average()
    val bestScore = rides.mapNotNull { it.overallScore }.maxOrNull() ?: 0.0
    val latestScore = rides.firstOrNull()?.overallScore ?: 0.0
    val totalRides = rides.size

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TrendCard("📊", "Average", "${avgScore.toInt()}", Modifier.weight(1f))
            TrendCard("🏆", "Best", "${bestScore.toInt()}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TrendCard("🕐", "Latest", "${latestScore.toInt()}", Modifier.weight(1f))
            TrendCard("🔢", "Total Rides", "$totalRides", Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        // Category averages
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Category Averages", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                val avgBraking = rides.mapNotNull { it.brakingScore }.average()
                val avgSpeed = rides.mapNotNull { it.speedScore }.average()
                val avgCornering = rides.mapNotNull { it.corneringScore }.average()
                CategoryRow("🛑 Braking", avgBraking)
                CategoryRow("🚦 Speed Control", avgSpeed)
                CategoryRow("↩️ Cornering", avgCornering)
            }
        }
    }
}

@Composable
private fun TrendCard(emoji: String, label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
private fun CategoryRow(label: String, score: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("${score.toInt()}%", style = MaterialTheme.typography.bodyMedium,
            color = when {
                score >= 80 -> Accent
                score >= 60 -> Warning
                else -> Error
            })
    }
}
