package com.roadready.ui.screens.education

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadready.ui.theme.*

@Composable
fun EducationBookScreen(
    provinceCode: String,
    onBack: () -> Unit,
) {
    // Province handbook URLs (official government resources)
    val handbookUrl = when (provinceCode) {
        "ON" -> "https://www.ontario.ca/document/official-mto-drivers-handbook"
        "BC" -> "https://www.icbc.com/driver-licensing/documents/drivers-guide.pdf"
        "AB" -> "https://www.alberta.ca/drivers-guide"
        "QC" -> "https://saaq.gouv.qc.ca/en/driving-course/driving-guide"
        "MB" -> "https://www.mpi.mb.ca/Pages/drivers-handbook.aspx"
        "SK" -> "https://www.sgi.sk.ca/handbook/-/sgi/DriverHandbook"
        "NS" -> "https://novascotia.ca/sns/rmv/handbook/"
        "NB" -> "https://www2.gnb.ca/content/gnb/en/departments/dti/road_safety.html"
        "NL" -> "https://www.gov.nl.ca/snl/drivers/"
        "PE" -> "https://www.princeedwardisland.ca/en/information/transportation-and-infrastructure/drivers-handbook"
        else -> "https://www.canada.ca/en/services/transport/road.html"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = Surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("← Back", color = Primary) }
                Spacer(Modifier.width(8.dp))
                Text("$provinceCode Handbook", style = MaterialTheme.typography.titleLarge)
            }
        }

        // WebView placeholder — actual WebView requires platform-specific impl
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("📖", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(12.dp))
                    Text("Driver's Handbook", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "WebView content will load from:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        handbookUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = Primary,
                    )
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
