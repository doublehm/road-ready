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
import com.roadready.data.model.InstructorLicenseClass
import com.roadready.data.model.InstructorProfileCreateRequest
import com.roadready.data.remote.ApiClient
import com.roadready.data.repository.AuthRepository
import com.roadready.ui.components.ErrorBanner
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.components.RoadReadyTextField
import com.roadready.ui.components.SectionHeader
import com.roadready.ui.theme.*
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
    var insurancePolicy by remember { mutableStateOf("") }
    var certificationId by remember { mutableStateOf("") }
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
            label = "License Classes (e.g., Class 5)",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        RoadReadyTextField(
            value = yearsExperience,
            onValueChange = { yearsExperience = it.filter { c -> c.isDigit() } },
            label = "Years of Experience",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        SectionHeader("Compliance")

        RoadReadyTextField(
            value = insurancePolicy,
            onValueChange = { insurancePolicy = it },
            label = "Insurance Policy Number",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        RoadReadyTextField(
            value = certificationId,
            onValueChange = { certificationId = it },
            label = "Instructor Certification ID",
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
                    val rate = hourlyRate.toDoubleOrNull() ?: 0.0
                    val setupData = InstructorProfileCreateRequest(
                        city = city,
                        province = province,
                        licenseNumber = licenseNumber,
                        licenseClasses = listOf(
                            InstructorLicenseClass(licenseClass = licenseClasses, price = rate)
                        ),
                        yearsExperience = yearsExperience.toIntOrNull() ?: 0,
                        insurancePolicy = insurancePolicy,
                        certificationId = certificationId,
                        hourlyRate = rate,
                        bio = bio,
                        carMake = carMake,
                        carModel = carModel,
                        carYear = carYear.toIntOrNull() ?: 0,
                    )
                    apiClient.setupInstructorProfile(setupData).onSuccess {
                        authRepository.initialize()
                    }.onFailure { e ->
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
