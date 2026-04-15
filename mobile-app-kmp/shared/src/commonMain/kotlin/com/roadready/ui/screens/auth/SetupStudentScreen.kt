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
import com.roadready.data.model.StudentProfileCreateRequest
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
fun SetupStudentScreen() {
    val authRepository: AuthRepository = koinInject()
    val apiClient: ApiClient = koinInject()
    val scope = rememberCoroutineScope()

    var age by remember { mutableStateOf("") }
    var licenseNumber by remember { mutableStateOf("") }
    var licenseClass by remember { mutableStateOf("Class 7") }
    var city by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("British Columbia") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val licenseClasses = listOf("Class 7", "Class 7L", "Class 5", "Class 4")
    val provinces = listOf(
        "British Columbia", "Alberta", "Saskatchewan", "Manitoba",
        "Ontario", "Quebec", "Nova Scotia", "New Brunswick",
        "Prince Edward Island", "Newfoundland and Labrador",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text("🎓", style = MaterialTheme.typography.displayLarge)
        Text(
            text = "Complete Your Profile",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = "Tell us about yourself to get started",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        error?.let {
            ErrorBanner(message = it, modifier = Modifier.padding(bottom = 16.dp))
        }

        SectionHeader("Personal Info")

        RoadReadyTextField(
            value = age,
            onValueChange = { age = it.filter { c -> c.isDigit() } },
            label = "Age",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        RoadReadyTextField(
            value = city,
            onValueChange = { city = it },
            label = "City",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Province dropdown
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceVariant,
                ),
            )
            ExposedDropdownMenu(expanded = provinceExpanded, onDismissRequest = { provinceExpanded = false }) {
                provinces.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p) },
                        onClick = { province = p; provinceExpanded = false },
                    )
                }
            }
        }

        SectionHeader("License Info")

        RoadReadyTextField(
            value = licenseNumber,
            onValueChange = { licenseNumber = it },
            label = "License Number",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // License class dropdown
        var classExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = classExpanded,
            onExpandedChange = { classExpanded = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            OutlinedTextField(
                value = licenseClass,
                onValueChange = {},
                readOnly = true,
                label = { Text("License Class") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceVariant,
                ),
            )
            ExposedDropdownMenu(expanded = classExpanded, onDismissRequest = { classExpanded = false }) {
                licenseClasses.forEach { lc ->
                    DropdownMenuItem(
                        text = { Text(lc) },
                        onClick = { licenseClass = lc; classExpanded = false },
                    )
                }
            }
        }

        // TODO: Image picker for license photo (requires platform-specific impl)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("📸", style = MaterialTheme.typography.headlineLarge)
                Text("License Photo", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Image upload coming soon",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }

        PrimaryButton(
            text = "Complete Setup",
            onClick = {
                if (age.isBlank() || city.isBlank()) {
                    error = "Please fill in all required fields"
                    return@PrimaryButton
                }
                isLoading = true
                error = null
                scope.launch {
                    apiClient.setupStudentProfile(StudentProfileCreateRequest(
                        age = age.toIntOrNull() ?: 0,
                        licenseNumber = licenseNumber,
                        licenseClass = licenseClass,
                        city = city,
                        province = province,
                    )).onSuccess {
                        authRepository.initialize()
                    }.onFailure { e ->
                        error = e.message ?: "Setup failed"
                    }
                    isLoading = false
                }
            },
            isLoading = isLoading,
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}
