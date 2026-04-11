package com.roadready.ui.screens.diagnostic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadready.ui.components.ErrorBanner
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.components.RoadReadyTextField
import com.roadready.ui.theme.*

@Composable
fun DiagnosticRideSetupScreen(
    onContinue: (parentName: String, parentPhone: String, parentRelation: String) -> Unit,
    onBack: () -> Unit,
) {
    var parentName by remember { mutableStateOf("") }
    var parentPhone by remember { mutableStateOf("") }
    var parentRelation by remember { mutableStateOf("Parent") }
    var error by remember { mutableStateOf<String?>(null) }

    val relations = listOf("Parent", "Guardian", "Sibling (25+)", "Other Licensed Adult")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = Primary) }

        Text("Parent-Supervised Ride", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Enter the details of the supervising adult",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp),
            color = TextMuted,
        )

        error?.let { ErrorBanner(it, Modifier.padding(bottom = 16.dp)) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Supervisor Information", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp))

                RoadReadyTextField(
                    value = parentName,
                    onValueChange = { parentName = it },
                    label = "Full Name",
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                RoadReadyTextField(
                    value = parentPhone,
                    onValueChange = { parentPhone = it },
                    label = "Phone Number",
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                Text("Relationship", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp))

                relations.forEach { relation ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = parentRelation == relation,
                            onClick = { parentRelation = relation },
                            colors = RadioButtonDefaults.colors(selectedColor = Primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(relation, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Requirements reminder
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📋 Requirements", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("• Supervisor must have a valid driver's license", style = MaterialTheme.typography.bodySmall)
                Text("• Supervisor must be 25 years or older", style = MaterialTheme.typography.bodySmall)
                Text("• Supervisor must be seated in the front passenger seat", style = MaterialTheme.typography.bodySmall)
                Text("• Vehicle must have valid insurance", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = "Continue to Handoff",
            onClick = {
                if (parentName.isBlank()) {
                    error = "Please enter the supervisor's name"; return@PrimaryButton
                }
                if (parentPhone.isBlank()) {
                    error = "Please enter a phone number"; return@PrimaryButton
                }
                error = null
                onContinue(parentName, parentPhone, parentRelation)
            },
        )

        Spacer(Modifier.height(32.dp))
    }
}
