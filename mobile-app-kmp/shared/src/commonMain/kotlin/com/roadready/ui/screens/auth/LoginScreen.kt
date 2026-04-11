package com.roadready.ui.screens.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roadready.data.repository.AuthRepository
import com.roadready.ui.components.ErrorBanner
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.components.RoadReadyTextField
import com.roadready.ui.theme.Primary
import com.roadready.ui.theme.TextMuted
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

class LoginViewModel(
    private val authRepository: AuthRepository,
) : androidx.lifecycle.ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            error = "Please enter email and password"
            return
        }
        isLoading = true
        error = null
        kotlinx.coroutines.MainScope().launch {
            val result = authRepository.login(email.trim(), password)
            isLoading = false
            result.onFailure { e ->
                error = e.message ?: "Login failed"
            }
        }
    }
}

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Logo / Title
        Text(
            text = "🚗",
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Road Ready",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Your driving journey starts here",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        // Error
        viewModel.error?.let { errorMsg ->
            ErrorBanner(
                message = errorMsg,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

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
            imeAction = ImeAction.Done,
            onImeAction = { viewModel.login() },
            modifier = Modifier.padding(bottom = 24.dp),
        )

        // Login Button
        PrimaryButton(
            text = "Sign In",
            onClick = { viewModel.login() },
            isLoading = viewModel.isLoading,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // Register Link
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Don't have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            Text(
                text = "Sign Up",
                style = MaterialTheme.typography.labelLarge,
                color = Primary,
                modifier = Modifier.clickable { onNavigateToRegister() },
            )
        }
    }
}
