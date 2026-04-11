package com.roadready.ui.screens.diagnostic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.theme.*

@Composable
fun SupervisorHandoffScreen(
    supervisorName: String,
    onReady: () -> Unit,
    onBack: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (step) {
            0 -> HandoffStep(
                emoji = "📱",
                title = "Hand Phone to $supervisorName",
                message = "The supervisor will hold the phone during the ride to mark events and provide feedback.\n\n" +
                    "The student should focus entirely on driving.",
                buttonText = "Phone Handed Over",
                onNext = { step = 1 },
                onBack = onBack,
            )
            1 -> HandoffStep(
                emoji = "🔒",
                title = "Mount the Phone",
                message = "Securely mount the phone in a dashboard holder or have the supervisor hold it steady.\n\n" +
                    "The phone needs to remain stable for accurate sensor readings.",
                buttonText = "Phone is Secured",
                onNext = { step = 2 },
                onBack = { step = 0 },
            )
            2 -> HandoffStep(
                emoji = "✅",
                title = "Safety Check",
                message = "Before starting:\n\n" +
                    "• Mirrors adjusted ✓\n" +
                    "• Seatbelts on ✓\n" +
                    "• GPS signal available ✓\n" +
                    "• Engine running ✓\n\n" +
                    "The supervisor ($supervisorName) will tap events during the ride.",
                buttonText = "Start Diagnostic Ride",
                onNext = onReady,
                onBack = { step = 1 },
                isLast = true,
            )
        }
    }
}

@Composable
private fun HandoffStep(
    emoji: String,
    title: String,
    message: String,
    buttonText: String,
    onNext: () -> Unit,
    onBack: () -> Unit,
    isLast: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = TextMuted)
            Spacer(Modifier.height(32.dp))
            PrimaryButton(
                text = buttonText,
                onClick = onNext,
                color = if (isLast) Accent else Primary,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text("← Back", color = TextMuted) }
        }
    }
}
