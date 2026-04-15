package com.roadready.ui.screens.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roadready.data.model.InstructorLicenseClass
import com.roadready.data.repository.AuthRepository
import com.roadready.ui.components.ErrorBanner
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.components.RoadReadyTextField
import com.roadready.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

class RegisterViewModel(
    private val authRepository: AuthRepository,
) : androidx.lifecycle.ViewModel() {
    var fullName by mutableStateOf("")
    var email by mutableStateOf("")
    var phoneNumber by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var selectedRole by mutableStateOf("student")
    
    // Additional Fields
    var city by mutableStateOf("")
    var province by mutableStateOf("British Columbia")
    var bio by mutableStateOf("")
    var hourlyRate by mutableStateOf("")
    var carMake by mutableStateOf("")
    var carModel by mutableStateOf("")
    var carYear by mutableStateOf("")
    var insurancePolicy by mutableStateOf("")
    var certificationId by mutableStateOf("")
    var licenseClasses by mutableStateOf("Class 5")
    var age by mutableStateOf("")
    var licenseNumber by mutableStateOf("")

    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun register(onSuccess: () -> Unit) {
        when {
            fullName.isBlank() -> { error = "Please enter your full name"; return }
            email.isBlank() -> { error = "Please enter your email"; return }
            phoneNumber.isBlank() -> { error = "Please enter your phone number"; return }
            password.length < 8 -> { error = "Password must be at least 8 characters"; return }
            password != confirmPassword -> { error = "Passwords don't match"; return }
            city.isBlank() -> { error = "Please enter your city"; return }
        }
        
        if (selectedRole == "instructor") {
            if (hourlyRate.isBlank()) { error = "Please enter your hourly rate"; return }
            if (insurancePolicy.isBlank()) { error = "Please enter your insurance policy"; return }
            if (certificationId.isBlank()) { error = "Please enter your certification ID"; return }
        } else {
            if (age.isBlank()) { error = "Please enter your age"; return }
            if (licenseNumber.isBlank()) { error = "Please enter your license number"; return }
        }

        isLoading = true
        error = null
        kotlinx.coroutines.MainScope().launch {
            val result = authRepository.register(
                fullName = fullName.trim(),
                email = email.trim(),
                password = password,
                phoneNumber = phoneNumber.trim(),
                role = selectedRole,
                city = city.trim(),
                province = province.trim(),
                bio = bio.trim().takeIf { it.isNotBlank() },
                hourlyRate = hourlyRate.toDoubleOrNull(),
                carMake = carMake.trim().takeIf { it.isNotBlank() },
                carModel = carModel.trim().takeIf { it.isNotBlank() },
                carYear = carYear.toIntOrNull(),
                insurancePolicy = insurancePolicy.trim(),
                certificationId = certificationId.trim(),
                licenseClasses = if (selectedRole == "instructor") {
                    listOf(InstructorLicenseClass(licenseClass = licenseClasses.trim(), price = hourlyRate.toDoubleOrNull() ?: 0.0))
                } else null,
                age = age.toIntOrNull(),
                licenseNumber = licenseNumber.trim()
            )
            isLoading = false
            result.onSuccess {
                onSuccess()
            }.onFailure { e ->
                error = e.message ?: "Registration failed"
            }
        }
    }
}

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineLarge,
        )

        Text(
            text = "Join Road Ready today",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        // Error
        viewModel.error?.let { errorMsg ->
            ErrorBanner(
                message = errorMsg,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        // Role Selection
        Text(
            text = "I am a:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoleCard(
                title = "Student",
                emoji = "🎓",
                isSelected = viewModel.selectedRole == "student",
                onClick = { viewModel.selectedRole = "student" },
                modifier = Modifier.weight(1f),
            )
            RoleCard(
                title = "Instructor",
                emoji = "🏫",
                isSelected = viewModel.selectedRole == "instructor",
                onClick = { viewModel.selectedRole = "instructor" },
                modifier = Modifier.weight(1f),
            )
        }

        // Full Name
        RoadReadyTextField(
            value = viewModel.fullName,
            onValueChange = { viewModel.fullName = it },
            label = "Full Name",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Email
        RoadReadyTextField(
            value = viewModel.email,
            onValueChange = { viewModel.email = it },
            label = "Email",
            keyboardType = KeyboardType.Email,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Phone Number
        RoadReadyTextField(
            value = viewModel.phoneNumber,
            onValueChange = { viewModel.phoneNumber = it },
            label = "Phone Number",
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Password
        RoadReadyTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = "Password",
            isPassword = true,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Confirm Password
        RoadReadyTextField(
            value = viewModel.confirmPassword,
            onValueChange = { viewModel.confirmPassword = it },
            label = "Confirm Password",
            isPassword = true,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // City
        RoadReadyTextField(
            value = viewModel.city,
            onValueChange = { viewModel.city = it },
            label = "City",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Province
        RoadReadyTextField(
            value = viewModel.province,
            onValueChange = { viewModel.province = it },
            label = "Province",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Role Specific Fields
        if (viewModel.selectedRole == "student") {
            RoadReadyTextField(
                value = viewModel.age,
                onValueChange = { viewModel.age = it.filter { c -> c.isDigit() } },
                label = "Age",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            RoadReadyTextField(
                value = viewModel.licenseNumber,
                onValueChange = { viewModel.licenseNumber = it },
                label = "BC Driver's License (7 digits)",
                modifier = Modifier.padding(bottom = 12.dp),
            )
        } else {
            RoadReadyTextField(
                value = viewModel.hourlyRate,
                onValueChange = { viewModel.hourlyRate = it.filter { c -> c.isDigit() || c == '.' } },
                label = "Hourly Rate ($)",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            RoadReadyTextField(
                value = viewModel.licenseClasses,
                onValueChange = { viewModel.licenseClasses = it },
                label = "Instructor License Class (e.g. Class 5)",
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoadReadyTextField(
                    value = viewModel.carMake,
                    onValueChange = { viewModel.carMake = it },
                    label = "Car Make",
                    modifier = Modifier.weight(1f)
                )
                RoadReadyTextField(
                    value = viewModel.carModel,
                    onValueChange = { viewModel.carModel = it },
                    label = "Model",
                    modifier = Modifier.weight(1f)
                )
            }
            RoadReadyTextField(
                value = viewModel.carYear,
                onValueChange = { viewModel.carYear = it.filter { c -> c.isDigit() } },
                label = "Car Year",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            RoadReadyTextField(
                value = viewModel.insurancePolicy,
                onValueChange = { viewModel.insurancePolicy = it },
                label = "Insurance Policy Number",
                modifier = Modifier.padding(bottom = 12.dp),
            )
            RoadReadyTextField(
                value = viewModel.certificationId,
                onValueChange = { viewModel.certificationId = it },
                label = "Instructor Certification ID",
                modifier = Modifier.padding(bottom = 12.dp),
            )
            RoadReadyTextField(
                value = viewModel.bio,
                onValueChange = { viewModel.bio = it },
                label = "Professional Bio",
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        PrimaryButton(
            text = "Create Account",
            onClick = { viewModel.register(onRegisterSuccess) },
            isLoading = viewModel.isLoading,
            color = if (viewModel.selectedRole == "instructor") Accent else Primary,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // Login Link
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Already have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            Text(
                text = "Sign In",
                style = MaterialTheme.typography.labelLarge,
                color = Primary,
                modifier = Modifier.clickable { onNavigateToLogin() },
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RoleCard(
    title: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Primary.copy(alpha = 0.15f) else Surface,
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(
                width = 2.dp,
                brush = androidx.compose.ui.graphics.SolidColor(Primary),
            )
        } else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = emoji, style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) Primary else TextSecondary,
            )
        }
    }
}
