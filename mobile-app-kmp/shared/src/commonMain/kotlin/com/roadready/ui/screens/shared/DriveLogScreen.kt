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
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.ErrorBanner
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.components.RoadReadyTextField
import com.roadready.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DriveLogScreen(
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var supervisorName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var startLocation by remember { mutableStateOf("") }
    var endLocation by remember { mutableStateOf("") }
    var weather by remember { mutableStateOf("Clear") }
    var timeOfDay by remember { mutableStateOf("Day") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = Primary) }

        Text("Log a Drive", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Manually record a practice drive for your logbook",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp),
            color = TextMuted,
        )

        error?.let { ErrorBanner(it, Modifier.padding(bottom = 16.dp)) }
        if (success) {
            Card(colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.15f)),
                modifier = Modifier.padding(bottom = 16.dp)) {
                Text("✅ Drive logged successfully!", modifier = Modifier.padding(12.dp), color = Accent)
            }
        }

        RoadReadyTextField(value = date, onValueChange = { date = it },
            label = "Date (YYYY-MM-DD)", modifier = Modifier.padding(bottom = 12.dp))
        RoadReadyTextField(value = duration, onValueChange = { duration = it.filter { c -> c.isDigit() } },
            label = "Duration (minutes)", modifier = Modifier.padding(bottom = 12.dp))
        RoadReadyTextField(value = supervisorName, onValueChange = { supervisorName = it },
            label = "Supervisor Name", modifier = Modifier.padding(bottom = 12.dp))

        Spacer(Modifier.height(8.dp))

        RoadReadyTextField(value = startLocation, onValueChange = { startLocation = it },
            label = "Start Location", modifier = Modifier.padding(bottom = 12.dp))
        RoadReadyTextField(value = endLocation, onValueChange = { endLocation = it },
            label = "End Location", modifier = Modifier.padding(bottom = 16.dp))

        // Conditions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Conditions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

                Text("Weather", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    listOf("Clear", "Rain", "Snow", "Fog").forEach { w ->
                        FilterChip(
                            selected = weather == w,
                            onClick = { weather = w },
                            label = { Text(w) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }

                Text("Time of Day", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Row {
                    listOf("Day", "Night", "Dawn", "Dusk").forEach { t ->
                        FilterChip(
                            selected = timeOfDay == t,
                            onClick = { timeOfDay = t },
                            label = { Text(t) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        RoadReadyTextField(value = notes, onValueChange = { notes = it },
            label = "Notes (optional)", modifier = Modifier.padding(bottom = 24.dp))

        PrimaryButton(
            text = "Log Drive",
            isLoading = isSubmitting,
            onClick = {
                if (date.isBlank() || duration.isBlank()) {
                    error = "Date and duration are required"; return@PrimaryButton
                }
                isSubmitting = true; error = null; success = false
                scope.launch {
                    apiClient.createDriveLog(mapOf(
                        "date" to date, "duration_minutes" to duration,
                        "supervisor_name" to supervisorName, "notes" to notes,
                        "start_location" to startLocation, "end_location" to endLocation,
                        "weather" to weather, "time_of_day" to timeOfDay,
                    )).onSuccess { success = true; date = ""; duration = ""; notes = "" }
                        .onFailure { error = it.message ?: "Failed to log drive" }
                    isSubmitting = false
                }
            },
            color = Accent,
        )

        Spacer(Modifier.height(32.dp))
    }
}
