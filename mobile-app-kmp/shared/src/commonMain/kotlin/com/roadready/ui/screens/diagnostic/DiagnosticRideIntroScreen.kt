package com.roadready.ui.screens.diagnostic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.theme.*

@Composable
fun DiagnosticRideIntroScreen(
    onStartParentSupervised: () -> Unit,
    onStartInstructorSupervised: () -> Unit,
    onBack: () -> Unit,
) {
    var showDisclaimer by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf<String?>(null) }

    if (showDisclaimer) {
        DisclaimerSheet(
            onAccept = {
                showDisclaimer = false
                when (selectedType) {
                    "parent" -> onStartParentSupervised()
                    "instructor" -> onStartInstructorSupervised()
                }
            },
            onDecline = { showDisclaimer = false },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = Primary) }

        Spacer(Modifier.height(16.dp))
        Text("🚗", style = MaterialTheme.typography.displayLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Text(
            "Diagnostic Ride",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Assess your driving skills with a comprehensive sensor-tracked evaluation",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            color = TextMuted,
        )

        Spacer(Modifier.height(24.dp))

        // Ride type cards
        RideTypeCard(
            emoji = "👨‍👩‍👧",
            title = "Parent-Supervised",
            description = "A parent or guardian supervises your ride. Great for practicing before lessons.",
            onClick = { selectedType = "parent"; showDisclaimer = true },
        )

        Spacer(Modifier.height(12.dp))

        RideTypeCard(
            emoji = "🎓",
            title = "Instructor-Supervised",
            description = "Your driving instructor supervises and evaluates the ride via a booking.",
            onClick = { selectedType = "instructor"; showDisclaimer = true },
        )

        Spacer(Modifier.height(24.dp))

        // Features
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("What's Measured", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                FeatureItem("📍", "GPS tracking & route mapping")
                FeatureItem("⚡", "Acceleration & braking patterns")
                FeatureItem("🔄", "Cornering G-forces")
                FeatureItem("🚦", "Speed limit compliance")
                FeatureItem("📊", "Overall driving score (0-100)")
            }
        }
    }
}

@Composable
private fun RideTypeCard(emoji: String, title: String, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Text("→", style = MaterialTheme.typography.titleLarge, color = Primary)
        }
    }
}

@Composable
private fun FeatureItem(emoji: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DisclaimerSheet(onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("⚠️", style = MaterialTheme.typography.displayMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(12.dp))
                Text("Safety Disclaimer", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text(
                    "This diagnostic ride uses your phone's sensors to evaluate driving patterns. " +
                    "Please ensure:\n\n" +
                    "• Your phone is securely mounted\n" +
                    "• A licensed supervisor is present\n" +
                    "• You drive safely at all times\n" +
                    "• Road safety takes priority over the app\n\n" +
                    "The supervisor should handle any in-app interactions while the vehicle is in motion.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                PrimaryButton(text = "I Understand & Accept", onClick = onAccept)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", color = TextMuted)
                }
            }
        }
    }
}
