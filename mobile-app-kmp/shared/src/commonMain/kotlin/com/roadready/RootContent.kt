package com.roadready

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadready.data.repository.AuthRepository
import com.roadready.data.repository.AuthState
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.screens.auth.LoginScreen
import com.roadready.ui.screens.auth.RegisterScreen
import com.roadready.ui.screens.instructor.InstructorHomeScreen
import com.roadready.ui.screens.student.StudentHomeScreen
import com.roadready.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

enum class Screen {
    Loading,
    Login,
    Register,
    StudentHome,
    InstructorHome,
}

@Composable
fun RootContent() {
    val authRepository: AuthRepository = koinInject()
    val authState by authRepository.authState.collectAsState()
    val scope = rememberCoroutineScope()

    // Initialize auth on first composition
    LaunchedEffect(Unit) {
        authRepository.initialize()
    }

    var currentScreen by remember { mutableStateOf(Screen.Loading) }

    // React to auth state changes
    LaunchedEffect(authState) {
        currentScreen = when {
            authState.isLoading -> Screen.Loading
            !authState.isLoggedIn -> Screen.Login
            authState.role == "student" -> Screen.StudentHome
            authState.role == "instructor" -> Screen.InstructorHome
            else -> Screen.Login
        }
    }

    when (currentScreen) {
        Screen.Loading -> LoadingOverlay()

        Screen.Login -> LoginScreen(
            onNavigateToRegister = { currentScreen = Screen.Register },
        )

        Screen.Register -> RegisterScreen(
            onNavigateToLogin = { currentScreen = Screen.Login },
        )

        Screen.StudentHome -> StudentHomeScreen(
            onStartDiagnostic = { /* TODO: Navigate to DiagnosticRideIntro */ },
            onFindInstructor = { /* TODO: Navigate to FindInstructor */ },
            onViewHistory = { /* TODO: Navigate to DiagnosticRideHistory */ },
            onOpenNotifications = { /* TODO: Navigate to Notifications */ },
        )

        Screen.InstructorHome -> InstructorHomeScreen()
    }
}
