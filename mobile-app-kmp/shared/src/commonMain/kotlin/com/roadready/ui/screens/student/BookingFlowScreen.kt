package com.roadready.ui.screens.student

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.ErrorBanner
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.components.RoadReadyTextField
import com.roadready.ui.components.SectionHeader
import com.roadready.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun BookingFlowScreen(
    instructorId: Int,
    instructorName: String,
    forDiagnosticRide: Boolean = false,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("60") }
    var pickupAddress by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = Primary) }

        Text(
            if (forDiagnosticRide) "Book Diagnostic Ride" else "Book a Lesson",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text("with $instructorName", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp))

        error?.let { ErrorBanner(it, Modifier.padding(bottom = 16.dp)) }

        SectionHeader("Schedule")

        RoadReadyTextField(
            value = date,
            onValueChange = { date = it },
            label = "Date (YYYY-MM-DD)",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        RoadReadyTextField(
            value = time,
            onValueChange = { time = it },
            label = "Time (HH:MM)",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        RoadReadyTextField(
            value = duration,
            onValueChange = { duration = it.filter { c -> c.isDigit() } },
            label = "Duration (minutes)",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        SectionHeader("Location")

        RoadReadyTextField(
            value = pickupAddress,
            onValueChange = { pickupAddress = it },
            label = "Pickup Address",
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Map placeholder
        Card(
            modifier = Modifier.fillMaxWidth().height(150.dp).padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗺️", style = MaterialTheme.typography.headlineLarge)
                    Text("Map picker available on device", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        }

        PrimaryButton(
            text = "Confirm Booking",
            onClick = {
                if (date.isBlank() || time.isBlank()) {
                    error = "Please select date and time"; return@PrimaryButton
                }
                isLoading = true; error = null
                scope.launch {
                    apiClient.createBooking(mapOf(
                        "instructor_id" to instructorId.toString(),
                        "scheduled_date" to date,
                        "scheduled_time" to time,
                        "duration" to duration,
                        "pickup_address" to pickupAddress,
                        "for_diagnostic_ride" to forDiagnosticRide.toString(),
                    )).onSuccess { onSuccess() }
                        .onFailure { error = it.message ?: "Booking failed" }
                    isLoading = false
                }
            },
            isLoading = isLoading,
            color = if (forDiagnosticRide) Primary else Accent,
        )

        Spacer(Modifier.height(32.dp))
    }
}
