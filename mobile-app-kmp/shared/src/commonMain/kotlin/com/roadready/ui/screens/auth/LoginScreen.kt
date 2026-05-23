package com.roadready.ui.screens.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
        RoadReadyLogo(modifier = Modifier.padding(bottom = 16.dp))

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

@Composable
fun RoadReadyLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(100.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF6366F1).copy(alpha = 0.2f), Color.Transparent)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(72.dp)) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val radius = w / 2f
            
            // Outer glowing ring
            drawCircle(
                color = Color(0xFF6366F1).copy(alpha = 0.15f),
                radius = radius,
                style = Stroke(width = 4.dp.toPx())
            )
            
            // Inner neon accent ring
            drawCircle(
                color = Color(0xFF10B981),
                radius = radius - 6.dp.toPx(),
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Road horizon converging lines
            val roadPath = Path().apply {
                moveTo(center.x, center.y - 12.dp.toPx())
                lineTo(center.x - 20.dp.toPx(), center.y + 24.dp.toPx())
                lineTo(center.x + 20.dp.toPx(), center.y + 24.dp.toPx())
                close()
            }
            
            drawPath(
                path = roadPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF6366F1).copy(alpha = 0.8f), Color(0xFF4F46E5).copy(alpha = 0.3f))
                )
            )
            
            // Horizon line
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(center.x - 24.dp.toPx(), center.y - 4.dp.toPx()),
                end = Offset(center.x + 24.dp.toPx(), center.y - 4.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
            
            // Lane divider lines inside the road path
            drawLine(
                color = Color(0xFFF59E0B),
                start = Offset(center.x, center.y - 8.dp.toPx()),
                end = Offset(center.x, center.y + 24.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(8f, 8f),
                    phase = 0f
                )
            )
            
            // Steering wheel outer ring
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = radius - 16.dp.toPx(),
                style = Stroke(width = 3.5.dp.toPx())
            )
            
            // Steering wheel spokes
            // Left spoke
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = center,
                end = Offset(center.x - radius + 16.dp.toPx(), center.y),
                strokeWidth = 3.5.dp.toPx()
            )
            // Right spoke
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = center,
                end = Offset(center.x + radius - 16.dp.toPx(), center.y),
                strokeWidth = 3.5.dp.toPx()
            )
            // Bottom spoke
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = center,
                end = Offset(center.x, center.y + radius - 16.dp.toPx()),
                strokeWidth = 3.5.dp.toPx()
            )
        }
    }
}
