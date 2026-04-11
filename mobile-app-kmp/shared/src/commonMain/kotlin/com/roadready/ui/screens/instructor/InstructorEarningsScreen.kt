package com.roadready.ui.screens.instructor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roadready.data.model.Booking
import com.roadready.data.model.DiagnosticRide
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.theme.*
import com.roadready.ui.util.fmtDollar
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun InstructorEarningsScreen() {
    val apiClient: ApiClient = koinInject()
    var isLoading by remember { mutableStateOf(true) }
    var bookings by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var rides by remember { mutableStateOf<List<DiagnosticRide>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Lessons", "Diagnostic")

    LaunchedEffect(Unit) {
        apiClient.getBookings().onSuccess { bookings = it }
        apiClient.getDiagnosticRides().onSuccess { rides = it }
        isLoading = false
    }

    if (isLoading) { LoadingOverlay(); return }

    val completedBookings = bookings.filter { it.status == "completed" }
    val totalEarnings = completedBookings.mapNotNull { it.totalPrice }.sum()
    val completedRides = rides.filter { it.status == "completed" || it.status == "graded" }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Earnings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(16.dp))

        TabRow(selectedTabIndex = selectedTab, containerColor = Surface, contentColor = Primary) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) },
                    selectedContentColor = Primary, unselectedContentColor = TextMuted)
            }
        }

        when (selectedTab) {
            0 -> EarningsOverview(totalEarnings, completedBookings.size, completedRides.size)
            1 -> LessonEarnings(completedBookings)
            2 -> DiagnosticEarnings(completedRides)
        }
    }
}

@Composable
private fun EarningsOverview(total: Double, lessons: Int, rides: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Total Earnings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Total Earnings", style = MaterialTheme.typography.bodyMedium)
                Text(fmtDollar(total), style = MaterialTheme.typography.displayLarge, color = AccentLight)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📚", style = MaterialTheme.typography.headlineMedium)
                    Text("$lessons", style = MaterialTheme.typography.titleLarge, color = Primary)
                    Text("Lessons", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛞", style = MaterialTheme.typography.headlineMedium)
                    Text("$rides", style = MaterialTheme.typography.titleLarge, color = Accent)
                    Text("Rides Graded", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun LessonEarnings(bookings: List<Booking>) {
    if (bookings.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No completed lessons yet", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(bookings) { booking ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(booking.student?.fullName ?: "Student", style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(booking.scheduledDate ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(booking.totalPrice?.let { fmtDollar(it) } ?: "$0.00",
                        style = MaterialTheme.typography.titleMedium, color = AccentLight)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticEarnings(rides: List<DiagnosticRide>) {
    if (rides.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No graded diagnostic rides yet", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rides) { ride ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ride.student?.fullName ?: "Student #${ride.studentId}", style = MaterialTheme.typography.titleMedium)
                        Text(ride.createdAt ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                    ride.overallScore?.let { score ->
                        val color = when { score >= 80 -> AccentLight; score >= 60 -> Warning; else -> Error }
                        Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                            Text("${score.toInt()}%", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge, color = color)
                        }
                    }
                }
            }
        }
    }
}
