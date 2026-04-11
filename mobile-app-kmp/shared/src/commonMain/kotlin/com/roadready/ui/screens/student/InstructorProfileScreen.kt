package com.roadready.ui.screens.student

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.roadready.data.model.User
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.theme.*
import com.roadready.ui.util.fmtDouble

@Composable
fun InstructorProfileScreen(
    instructor: User,
    onBookLesson: () -> Unit,
    onBack: () -> Unit,
    forDiagnosticRide: Boolean = false,
) {
    val profile = instructor.instructorProfile

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        // Back button
        TextButton(onClick = onBack) { Text("← Back", color = Primary) }

        // Profile Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.size(80.dp).clip(CircleShape),
                    color = Accent.copy(alpha = 0.2f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(instructor.fullName.take(1).uppercase(),
                            style = MaterialTheme.typography.displayLarge, color = Accent)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(instructor.fullName, style = MaterialTheme.typography.headlineMedium)
                profile?.averageRating?.let {
                    Text("⭐ ${fmtDouble(it)}", style = MaterialTheme.typography.titleMedium, color = Warning)
                }
                Text("${profile?.city ?: ""}, ${profile?.province ?: ""}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Details Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailCard("💰", "Rate", "$${profile?.hourlyRate?.toInt() ?: 0}/hr", Modifier.weight(1f))
            DetailCard("📅", "Experience", "${profile?.yearsExperience ?: 0} yrs", Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailCard("🚗", "Vehicle", "${profile?.carMake ?: ""} ${profile?.carModel ?: ""}", Modifier.weight(1f))
            DetailCard("📜", "License", profile?.licenseClasses ?: "N/A", Modifier.weight(1f))
        }

        // Bio
        profile?.bio?.let { bio ->
            if (bio.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("About", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(bio, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = if (forDiagnosticRide) "Book Diagnostic Ride" else "Book a Lesson",
            onClick = onBookLesson,
            color = if (forDiagnosticRide) Primary else Accent,
        )
    }
}

@Composable
private fun DetailCard(emoji: String, label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
