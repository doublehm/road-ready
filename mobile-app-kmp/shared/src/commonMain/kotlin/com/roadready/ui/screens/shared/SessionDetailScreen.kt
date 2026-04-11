package com.roadready.ui.screens.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadready.data.FaultCategories
import com.roadready.data.model.Booking
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun SessionDetailScreen(
    bookingId: Int,
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    var isLoading by remember { mutableStateOf(true) }
    var booking by remember { mutableStateOf<Booking?>(null) }

    LaunchedEffect(bookingId) {
        apiClient.getBooking(bookingId).onSuccess { booking = it }
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
            Text("Session Detail", style = MaterialTheme.typography.headlineMedium)
        }

        if (booking == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) {
                Text("Booking not found", style = MaterialTheme.typography.titleMedium, color = TextMuted)
            }
            return@Column
        }

        val b = booking!!

        // Session info
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Session Information", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                InfoRow("Student", b.student?.fullName ?: "—")
                InfoRow("Instructor", b.instructor?.fullName ?: "—")
                b.scheduledDate?.let { InfoRow("Date", it) }
                b.scheduledTime?.let { InfoRow("Time", it) }
                InfoRow("Status", b.status.replaceFirstChar { it.uppercase() })
                b.duration?.let { InfoRow("Duration", "${it}min") }
                b.pickupAddress?.let { InfoRow("Location", it) }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Grading section if available
        b.instructorRating?.let { rating ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Instructor Evaluation", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    Row {
                        repeat(rating) { Text("⭐", style = MaterialTheme.typography.titleMedium) }
                        repeat(5 - rating) { Text("☆", style = MaterialTheme.typography.titleMedium, color = TextMuted) }
                    }
                    b.instructorNotes?.let {
                        if (it.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Fault codes if any
        b.faultCodes?.let { codes ->
            if (codes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Fault Codes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                        codes.forEach { code ->
                            val faultItem = FaultCategories.categories
                                .flatMap { it.items }
                                .find { it.code == code }
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(code, style = MaterialTheme.typography.titleSmall, color = Warning,
                                    modifier = Modifier.width(40.dp))
                                Text(faultItem?.label ?: "Unknown", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
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
