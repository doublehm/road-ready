package com.roadready.ui.screens.student

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roadready.ui.theme.*

@Composable
fun CheckoutScreen(
    bookingId: Int,
    amount: Double,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Back", color = Primary) }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("💳", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(16.dp))
                    Text("Payment", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$${"%.2f".format(amount)}",
                        style = MaterialTheme.typography.displayMedium,
                        color = Primary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Stripe Checkout WebView will load here.\n\n" +
                        "Platform-specific WebView integration required for\n" +
                        "Stripe Payment Element / Checkout Session.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = TextMuted,
                    )
                    Spacer(Modifier.height(24.dp))
                    // Placeholder button for dev
                    Button(
                        onClick = onSuccess,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Simulate Payment Success")
                    }
                }
            }
        }
    }
}
