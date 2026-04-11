package com.roadready.ui.screens.education

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadready.ui.theme.*

data class ProvinceInfo(
    val code: String,
    val name: String,
    val emoji: String,
)

private val PROVINCES = listOf(
    ProvinceInfo("AB", "Alberta", "🏔️"),
    ProvinceInfo("BC", "British Columbia", "🌲"),
    ProvinceInfo("MB", "Manitoba", "🌾"),
    ProvinceInfo("NB", "New Brunswick", "🦞"),
    ProvinceInfo("NL", "Newfoundland and Labrador", "🐳"),
    ProvinceInfo("NS", "Nova Scotia", "⚓"),
    ProvinceInfo("ON", "Ontario", "🍁"),
    ProvinceInfo("PE", "Prince Edward Island", "🥔"),
    ProvinceInfo("QC", "Quebec", "⚜️"),
    ProvinceInfo("SK", "Saskatchewan", "🌻"),
    ProvinceInfo("NT", "Northwest Territories", "❄️"),
    ProvinceInfo("NU", "Nunavut", "🐻‍❄️"),
    ProvinceInfo("YT", "Yukon", "🏔️"),
)

@Composable
fun EducationHomeScreen(
    onSelectProvince: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Primary) }
            Text("Driver's Handbook", style = MaterialTheme.typography.headlineMedium)
        }

        Text(
            "Select your province to view the official driver's handbook",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = TextMuted,
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(PROVINCES) { province ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectProvince(province.code) },
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(province.emoji, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(province.name, style = MaterialTheme.typography.titleMedium)
                            Text(province.code, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        Text("→", style = MaterialTheme.typography.titleLarge, color = Primary)
                    }
                }
            }
        }
    }
}
