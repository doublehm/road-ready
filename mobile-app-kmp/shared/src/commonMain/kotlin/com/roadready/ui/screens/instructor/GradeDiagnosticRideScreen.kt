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
import com.roadready.ui.components.ErrorBanner
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.components.RoadReadyTextField
import com.roadready.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun GradeDiagnosticRideScreen(
    rideId: Int,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var ride by remember { mutableStateOf<DiagnosticRide?>(null) }
    var notes by remember { mutableStateOf("") }
    var overallAdjustment by remember { mutableIntStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rideId) {
        apiClient.getDiagnosticRide(rideId).onSuccess { ride = it }
        isLoading = false
    }

    if (isLoading) {
        com.roadready.ui.components.LoadingOverlay(); return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = Primary) }

        Text("Grade Diagnostic Ride", style = MaterialTheme.typography.headlineLarge)

        ride?.let { r ->
            Text("Student: ${r.student?.fullName ?: "Student #${r.studentId}"}",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted)

            Spacer(Modifier.height(16.dp))

            // Current sensor scores
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sensor Scores", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    ScoreDisplay("Overall", r.overallScore)
                    ScoreDisplay("Braking", r.brakingScore)
                    ScoreDisplay("Speed", r.speedScore)
                    ScoreDisplay("Cornering", r.corneringScore)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Adjustment
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Score Adjustment", style = MaterialTheme.typography.titleMedium)
                    Text("Adjust the overall score based on your observation",
                        style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { if (overallAdjustment > -20) overallAdjustment-- }) {
                            Text("-", style = MaterialTheme.typography.headlineLarge, color = Error)
                        }
                        Text(
                            "${if (overallAdjustment >= 0) "+" else ""}$overallAdjustment",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = when {
                                overallAdjustment > 0 -> Accent
                                overallAdjustment < 0 -> Error
                                else -> TextPrimary
                            },
                        )
                        TextButton(onClick = { if (overallAdjustment < 20) overallAdjustment++ }) {
                            Text("+", style = MaterialTheme.typography.headlineLarge, color = Accent)
                        }
                    }
                    val adjusted = ((r.overallScore ?: 0.0) + overallAdjustment).coerceIn(0.0, 100.0)
                    Text("Adjusted score: ${adjusted.toInt()}/100",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        error?.let { ErrorBanner(it, Modifier.padding(bottom = 16.dp)) }

        RoadReadyTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Instructor Notes",
            modifier = Modifier.padding(bottom = 24.dp),
        )

        PrimaryButton(
            text = "Submit Grade",
            isLoading = isSubmitting,
            onClick = {
                isSubmitting = true; error = null
                scope.launch {
                    apiClient.gradeRide(rideId, mapOf(
                        "adjustment" to overallAdjustment,
                        "notes" to notes,
                    )).onSuccess { onSuccess() }
                        .onFailure { error = it.message ?: "Failed to submit" }
                    isSubmitting = false
                }
            },
            color = Accent,
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ScoreDisplay(label: String, score: Double?) {
    val s = score ?: 0.0
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("${s.toInt()}%", style = MaterialTheme.typography.bodyMedium,
            color = when { s >= 80 -> Accent; s >= 60 -> Warning; else -> Error })
    }
}
