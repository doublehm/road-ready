package com.roadready.ui.screens.instructor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.roadready.data.model.DiagnosticRide
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun StudentProgressScreen(
    onSelectStudent: (studentId: Int, studentName: String) -> Unit,
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    var isLoading by remember { mutableStateOf(true) }
    var studentRides by remember { mutableStateOf<Map<Int, List<DiagnosticRide>>>(emptyMap()) }

    LaunchedEffect(Unit) {
        // Load all rides and group by student
        apiClient.getDiagnosticRides().onSuccess { rides ->
            studentRides = rides.groupBy { it.studentId }
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Primary) }
            Text("Student Progress", style = MaterialTheme.typography.headlineMedium)
        }

        if (isLoading) { LoadingOverlay(); return }

        if (studentRides.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📊", style = MaterialTheme.typography.displayLarge)
                    Text("No student data yet", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(studentRides.entries.toList()) { (studentId, rides) ->
                    val studentName = rides.firstOrNull()?.student?.fullName ?: "Student #$studentId"
                    val avgScore = rides.mapNotNull { it.overallScore }.average()
                    val latestScore = rides.maxByOrNull { it.createdAt ?: "" }?.overallScore ?: 0.0
                    val rideCount = rides.size

                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectStudent(studentId, studentName) },
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(modifier = Modifier.size(48.dp).clip(CircleShape), color = Primary.copy(alpha = 0.2f)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(studentName.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = Primary)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(studentName, style = MaterialTheme.typography.titleMedium)
                                Text("$rideCount ride${if (rideCount != 1) "s" else ""} • Avg: ${avgScore.toInt()}",
                                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${latestScore.toInt()}", style = MaterialTheme.typography.headlineMedium,
                                    color = when {
                                        latestScore >= 80 -> Accent
                                        latestScore >= 60 -> Warning
                                        else -> Error
                                    })
                                Text("latest", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
