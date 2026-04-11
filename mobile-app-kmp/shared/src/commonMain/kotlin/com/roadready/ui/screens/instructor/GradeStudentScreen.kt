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
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.ErrorBanner
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.components.RoadReadyTextField
import com.roadready.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun GradeStudentScreen(
    bookingId: Int,
    studentName: String,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val scope = rememberCoroutineScope()

    var overallRating by remember { mutableIntStateOf(3) }
    var notes by remember { mutableStateOf("") }
    var strengthNotes by remember { mutableStateOf("") }
    var improvementNotes by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Skill ratings
    var braking by remember { mutableIntStateOf(3) }
    var steering by remember { mutableIntStateOf(3) }
    var awareness by remember { mutableIntStateOf(3) }
    var signalUse by remember { mutableIntStateOf(3) }
    var lanePosition by remember { mutableIntStateOf(3) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = Primary) }

        Text("Grade Session", style = MaterialTheme.typography.headlineLarge)
        Text("Student: $studentName", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp), color = TextMuted)

        error?.let { ErrorBanner(it, Modifier.padding(bottom = 16.dp)) }

        // Overall Rating
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Overall Rating", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { star ->
                        TextButton(onClick = { overallRating = star }) {
                            Text(
                                if (star <= overallRating) "⭐" else "☆",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Skill breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Skill Assessment", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
                SkillSlider("🛑 Braking", braking) { braking = it }
                SkillSlider("🔄 Steering", steering) { steering = it }
                SkillSlider("👁️ Awareness", awareness) { awareness = it }
                SkillSlider("🔔 Signal Use", signalUse) { signalUse = it }
                SkillSlider("🛣️ Lane Position", lanePosition) { lanePosition = it }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Notes
        RoadReadyTextField(value = strengthNotes, onValueChange = { strengthNotes = it },
            label = "Strengths", modifier = Modifier.padding(bottom = 12.dp))
        RoadReadyTextField(value = improvementNotes, onValueChange = { improvementNotes = it },
            label = "Areas for Improvement", modifier = Modifier.padding(bottom = 12.dp))
        RoadReadyTextField(value = notes, onValueChange = { notes = it },
            label = "Additional Notes", modifier = Modifier.padding(bottom = 24.dp))

        PrimaryButton(
            text = "Submit Grade",
            isLoading = isLoading,
            onClick = {
                isLoading = true; error = null
                scope.launch {
                    apiClient.gradeSession(bookingId, mapOf(
                        "overall_rating" to overallRating,
                        "braking" to braking, "steering" to steering,
                        "awareness" to awareness, "signal_use" to signalUse,
                        "lane_position" to lanePosition,
                        "strengths" to strengthNotes, "improvements" to improvementNotes,
                        "notes" to notes,
                    )).onSuccess { onSuccess() }
                        .onFailure { error = it.message ?: "Failed to submit" }
                    isLoading = false
                }
            },
            color = Accent,
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SkillSlider(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$value/5", style = MaterialTheme.typography.bodyMedium, color = when {
                value >= 4 -> Accent; value >= 3 -> Warning; else -> Error
            })
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 1f..5f,
            steps = 3,
            colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary),
        )
    }
}
