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
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun BookingRequestsScreen(
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var pendingBookings by remember { mutableStateOf<List<Booking>>(emptyList()) }

    LaunchedEffect(Unit) {
        apiClient.getBookings(status = "pending").onSuccess { pendingBookings = it }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Primary) }
            Text("Booking Requests", style = MaterialTheme.typography.headlineMedium)
        }

        if (isLoading) { LoadingOverlay(); return }

        if (pendingBookings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", style = MaterialTheme.typography.displayLarge)
                    Text("No pending requests", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(pendingBookings, key = { it.id }) { booking ->
                    var actionLoading by remember { mutableStateOf(false) }
                    RequestCard(
                        booking = booking,
                        isLoading = actionLoading,
                        onAccept = {
                            actionLoading = true
                            scope.launch {
                                apiClient.updateBookingAction(booking.id, "accept")
                                pendingBookings = pendingBookings.filter { it.id != booking.id }
                                actionLoading = false
                            }
                        },
                        onReject = {
                            actionLoading = true
                            scope.launch {
                                apiClient.updateBookingAction(booking.id, "reject")
                                pendingBookings = pendingBookings.filter { it.id != booking.id }
                                actionLoading = false
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestCard(booking: Booking, isLoading: Boolean, onAccept: () -> Unit, onReject: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(booking.student?.fullName ?: "Student #${booking.studentId}",
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            booking.scheduledDate?.let {
                Text("📅 $it • ${booking.scheduledTime ?: ""}", style = MaterialTheme.typography.bodyMedium)
            }
            booking.pickupAddress?.let {
                Text("📍 $it", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (booking.forDiagnosticRide) {
                Surface(
                    color = Primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("Diagnostic Ride", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium, color = Primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onReject, enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                ) { Text("Decline") }
                Button(
                    onClick = onAccept, enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("Accept") }
            }
        }
    }
}
