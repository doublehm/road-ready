package com.roadready.ui.screens.student

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roadready.data.model.User
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.components.RoadReadyTextField
import com.roadready.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun FindInstructorScreen(
    onSelectInstructor: (User) -> Unit,
    forDiagnosticRide: Boolean = false,
) {
    val apiClient: ApiClient = koinInject()
    var isLoading by remember { mutableStateOf(true) }
    var instructors by remember { mutableStateOf<List<User>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apiClient.getInstructors().onSuccess { instructors = it }
        isLoading = false
    }

    val filtered = if (searchQuery.isBlank()) instructors
    else instructors.filter {
        it.fullName.contains(searchQuery, ignoreCase = true) ||
        it.instructorProfile?.city?.contains(searchQuery, ignoreCase = true) == true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (forDiagnosticRide) "Choose an Instructor" else "Find Instructor",
                style = MaterialTheme.typography.headlineLarge,
            )
            if (forDiagnosticRide) {
                Text("Select an instructor for your diagnostic ride",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            RoadReadyTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "Search by name or city",
            )
        }

        if (isLoading) { LoadingOverlay(); return }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", style = MaterialTheme.typography.displayLarge)
                    Text("No instructors found", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered) { instructor ->
                    InstructorCard(instructor) { onSelectInstructor(instructor) }
                }
            }
        }
    }
}

@Composable
private fun InstructorCard(instructor: User, onClick: () -> Unit) {
    val profile = instructor.instructorProfile
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar placeholder
            Surface(
                modifier = Modifier.size(48.dp).clip(CircleShape),
                color = Primary.copy(alpha = 0.2f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        instructor.fullName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Primary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(instructor.fullName, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    profile?.averageRating?.let {
                        Text("⭐ ${String.format("%.1f", it)} • ", style = MaterialTheme.typography.bodySmall, color = Warning)
                    }
                    Text(profile?.city ?: "", style = MaterialTheme.typography.bodySmall)
                }
                profile?.yearsExperience?.let {
                    if (it > 0) Text("$it years experience", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$${profile?.hourlyRate?.toInt() ?: 0}",
                    style = MaterialTheme.typography.titleMedium, color = AccentLight)
                Text("/hr", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}
