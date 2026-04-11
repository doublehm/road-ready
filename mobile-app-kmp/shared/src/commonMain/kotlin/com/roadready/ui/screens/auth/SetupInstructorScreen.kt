package com.roadready.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roadready.data.remote.ApiClient
import com.roadready.data.repository.AuthRepository
import com.roadready.ui.components.ErrorBanner
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.components.RoadReadyTextField
import com.roadready.ui.components.SectionHeader
import com.roadready.ui.theme.*
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupInstructorScreen() {
    val authRepository: AuthRepository = koinInject()
    val apiClient: ApiClient = koinInject()
    val scope = rememberCoroutineScope()

    var city by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("British Columbia") }
    var licenseNumber by remember { mutableStateOf("") }
    var licenseClasses by remember { mutableStateOf("Class 5") }
    var yearsExperience by remember { mutableStateOf("") }
    var hourlyRate by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var carMake by remember { mutableStateOf("") }
    var carModel by remember { mutableStateOf("") }
    var carYear by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val provinces = listOf(
        "British Columbia", "Alberta", "Saskatchewan", "Manitoba",
        "Ontario", "Quebec", "Nova Scotia", "New Brunswick",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text("🏫", style = MaterialTheme.typography.displayLarge)
        Text(
            text = "Instructor Setup",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = "Set up your instructor profile",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        error?.let {
            ErrorBanner(message = it, modifier = Modifier.padding(bottom = 16.dp))
        }

        SectionHeader("Location")

        RoadReadyTextField(
            value = city,
            onValueChange = { city = it },
            label = "City",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        var provinceExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = provinceExpanded,
            onExpandedChange = { provinceExpanded = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            OutlinedTextField(
                value = province,
                onValueChange = {},
                readOnly = true,
                label = { Text("Province") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = provinceExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = SurfaceVariant),
            )
            ExposedDropdownMenu(expanded = provinceExpanded, onDismissRequest = { provinceExpanded = false }) {
                provinces.forEach { p ->
                    DropdownMenuItem(text = { Text(p) }, onClick = { province = p; provinceExpanded = false })
                }
            }
        }

        SectionHeader("Credentials")

        RoadReadyTextField(
            value = licenseNumber,
            onValueChange = { licenseNumber = it },
            label = "Instructor License Number",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        RoadReadyTextField(
            value = licenseClasses,
            onValueChange = { licenseClasses = it },
            label = "License Classes (e.g., Class 5, Class 7)",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        RoadReadyTextField(
            value = yearsExperience,
            onValueChange = { yearsExperience = it.filter { c -> c.isDigit() } },
            label = "Years of Experience",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        SectionHeader("Pricing & Bio")

        RoadReadyTextField(
            value = hourlyRate,
            onValueChange = { hourlyRate = it.filter { c -> c.isDigit() || c == '.' } },
            label = "Hourly Rate ($)",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text("Bio") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).heightIn(min = 100.dp),
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = SurfaceVariant,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
            ),
        )

        SectionHeader("Vehicle")

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoadReadyTextField(
                value = carMake, onValueChange = { carMake = it },
                label = "Make", modifier = Modifier.weight(1f),
            )
            RoadReadyTextField(
                value = carModel, onValueChange = { carModel = it },
                label = "Model", modifier = Modifier.weight(1f),
            )
        }

        RoadReadyTextField(
            value = carYear,
            onValueChange = { carYear = it.filter { c -> c.isDigit() } },
            label = "Year",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        // Image placeholders
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("📸", style = MaterialTheme.typography.headlineLarge)
                Text("Insurance & Certification Photos", style = MaterialTheme.typography.titleMedium)
                Text("Image upload coming soon", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }

        PrimaryButton(
            text = "Complete Setup",
            onClick = {
                if (city.isBlank() || hourlyRate.isBlank()) {
                    error = "Please fill in city and hourly rate"
                    return@PrimaryButton
                }
                isLoading = true
                error = null
                scope.launch {
                    try {
                        apiClient.httpClient.put("instructor-profile/") {
                            contentType(ContentType.Application.Json)
                            setBody(mapOf(
                                "city" to city,
                                "province" to province,
                                "license_number" to licenseNumber,
                                "license_classes" to licenseClasses,
                                "years_experience" to yearsExperience,
                                "hourly_rate" to hourlyRate,
                                "bio" to bio,
                                "car_make" to carMake,
                                "car_model" to carModel,
                                "car_year" to carYear,
                            ))
                        }
                        authRepository.initialize()
                    } catch (e: Exception) {
                        error = e.message ?: "Setup failed"
                    }
                    isLoading = false
                }
            },
            isLoading = isLoading,
            color = Accent,
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}
