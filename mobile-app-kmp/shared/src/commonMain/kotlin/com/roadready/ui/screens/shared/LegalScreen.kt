package com.roadready.ui.screens.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadready.ui.theme.*

@Composable
fun LegalScreen(
    type: String, // "terms" or "privacy"
    onBack: () -> Unit,
) {
    val title = if (type == "terms") "Terms & Conditions" else "Privacy Policy"
    val url = if (type == "terms") {
        "https://roadready.app/terms"
    } else {
        "https://roadready.app/privacy"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = Surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("← Back", color = Primary) }
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (type == "terms") "📜" else "🔒", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(12.dp))
                    Text(title, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("WebView content will load from:", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    Spacer(Modifier.height(4.dp))
                    Text(url, style = MaterialTheme.typography.bodySmall, color = Primary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Platform-specific WebView integration required.\nUse Android WebView / iOS WKWebView via expect/actual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
        }
    }
}
