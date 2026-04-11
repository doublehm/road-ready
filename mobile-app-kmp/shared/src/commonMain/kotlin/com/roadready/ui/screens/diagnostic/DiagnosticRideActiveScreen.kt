package com.roadready.ui.screens.diagnostic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DiagnosticRideActiveScreen(
    rideType: String,
    supervisorName: String?,
    bookingId: Int?,
    onRideComplete: (rideId: Int?) -> Unit,
    onCancel: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val scope = rememberCoroutineScope()

    var isActive by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var showEndConfirm by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Event tracking
    var events by remember { mutableStateOf<List<String>>(emptyList()) }
    var speed by remember { mutableFloatStateOf(0f) }
    var speedLimit by remember { mutableIntStateOf(0) }

    // Timer
    LaunchedEffect(isActive) {
        while (isActive) {
            delay(1000)
            elapsedSeconds++
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60

    if (showEndConfirm) {
        EndRideConfirmation(
            onConfirm = {
                showEndConfirm = false
                isActive = false
                isSubmitting = true
                scope.launch {
                    // Submit ride data
                    val payload = mapOf(
                        "ride_type" to rideType,
                        "duration_seconds" to elapsedSeconds.toString(),
                        "supervisor_name" to (supervisorName ?: ""),
                        "booking_id" to (bookingId?.toString() ?: ""),
                        "events" to events.joinToString(","),
                    )
                    apiClient.completeRide(payload)
                        .onSuccess { onRideComplete(it.id) }
                        .onFailure { onRideComplete(null) }
                }
            },
            onCancel = { showEndConfirm = false },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Status bar
        Surface(color = if (isActive) Accent else Surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (isActive) "🔴 RECORDING" else "⏸ PAUSED",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Text(
                    String.format("%02d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                )
            }
        }

        // Speed display
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${speed.toInt()}",
                    style = MaterialTheme.typography.displayLarge,
                    color = if (speedLimit > 0 && speed > speedLimit) Error else Accent,
                )
                Text("km/h", style = MaterialTheme.typography.titleMedium, color = TextMuted)
                if (speedLimit > 0) {
                    Text("Limit: $speedLimit km/h", style = MaterialTheme.typography.bodySmall,
                        color = if (speed > speedLimit) Error else TextMuted)
                }
            }
        }

        // Map placeholder
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗺️", style = MaterialTheme.typography.displayLarge)
                    Text("Live Map View", style = MaterialTheme.typography.titleMedium)
                    Text("GPS tracking active", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text("${events.size} events recorded", style = MaterialTheme.typography.bodySmall, color = Primary)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Supervisor feedback buttons
        if (supervisorName != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Quick Events", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickEventButton("🛑 Hard Brake", Modifier.weight(1f)) {
                            events = events + "hard_brake"
                        }
                        QuickEventButton("↩️ Sharp Turn", Modifier.weight(1f)) {
                            events = events + "sharp_turn"
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickEventButton("🚦 Speed", Modifier.weight(1f)) {
                            events = events + "speeding"
                        }
                        QuickEventButton("⚠️ Other", Modifier.weight(1f)) {
                            events = events + "other_event"
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // End ride button
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
            ) { Text("Cancel") }
            Button(
                onClick = { showEndConfirm = true },
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(containerColor = Error),
                shape = RoundedCornerShape(12.dp),
                enabled = elapsedSeconds > 30,
            ) {
                Text("End Ride", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun QuickEventButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EndRideConfirmation(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏁", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(12.dp))
                Text("End Diagnostic Ride?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("This will stop recording and process your results.",
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = TextMuted)
                Spacer(Modifier.height(24.dp))
                PrimaryButton(text = "End & See Results", onClick = onConfirm, color = Accent)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel) { Text("Keep Riding", color = Primary) }
            }
        }
    }
}
