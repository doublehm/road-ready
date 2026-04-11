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
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var selectedRole by mutableStateOf("student")
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun register() {
        when {
            fullName.isBlank() -> { error = "Please enter your full name"; return }
            email.isBlank() -> { error = "Please enter your email"; return }
            password.length < 6 -> { error = "Password must be at least 6 characters"; return }
            password != confirmPassword -> { error = "Passwords don't match"; return }
        }
        isLoading = true
        error = null
        kotlinx.coroutines.MainScope().launch {
            val result = authRepository.register(fullName.trim(), email.trim(), password, selectedRole)
            isLoading = false
            result.onFailure { e ->
                error = e.message ?: "Registration failed"
            }
        }
    }
}

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
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
            imeAction = ImeAction.Done,
            onImeAction = { viewModel.register() },
            modifier = Modifier.padding(bottom = 24.dp),
        )

        // Register Button
        PrimaryButton(
            text = "Create Account",
            onClick = { viewModel.register() },
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
