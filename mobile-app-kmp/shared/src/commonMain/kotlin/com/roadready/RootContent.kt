package com.roadready

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import com.roadready.data.model.Booking
import com.roadready.data.model.Module
import com.roadready.data.model.User
import com.roadready.data.repository.AuthRepository
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.screens.auth.*
import com.roadready.ui.screens.diagnostic.*
import com.roadready.ui.screens.education.*
import com.roadready.ui.screens.instructor.*
import com.roadready.ui.screens.shared.*
import com.roadready.ui.screens.student.*
import org.koin.compose.koinInject

// ---------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------

sealed class Screen {
    // Loading / Auth
    data object Loading : Screen()
    data object Login : Screen()
    data object Register : Screen()
    data object SetupStudent : Screen()
    data object SetupInstructor : Screen()

    // Main (tabbed)
    data object StudentMain : Screen()
    data object InstructorMain : Screen()

    // Student
    data class FindInstructor(val forDiagnosticRide: Boolean = false) : Screen()
    data class InstructorProfile(val instructor: User, val forDiagnosticRide: Boolean = false) : Screen()
    data class BookingFlow(val instructorId: Int, val instructorName: String, val forDiagnosticRide: Boolean = false) : Screen()
    data class Checkout(val bookingId: Int, val amount: Double) : Screen()
    data object StudentEditProfile : Screen()

    // Instructor
    data object BookingRequests : Screen()
    data object EditInstructorProfile : Screen()
    data class GradeStudent(val bookingId: Int, val studentName: String) : Screen()
    data class GradeDiagnosticRide(val rideId: Int) : Screen()
    data object StudentProgress : Screen()
    data class StudentDetailStats(val studentId: Int, val studentName: String) : Screen()

    // Diagnostic Ride flow
    data object DiagnosticRideIntro : Screen()
    data object DiagnosticRideSetup : Screen()
    data class SupervisorHandoff(val supervisorName: String) : Screen()
    data class DiagnosticRideActive(val rideType: String, val supervisorName: String?, val bookingId: Int?) : Screen()
    data class DiagnosticRideResults(val rideId: Int) : Screen()
    data object DiagnosticRideHistory : Screen()
    data class DiagnosticRideDetail(val rideId: Int) : Screen()

    // Education
    data object EducationHome : Screen()
    data class EducationBook(val provinceCode: String) : Screen()
    data object Modules : Screen()
    data class Quiz(val moduleId: Int) : Screen()

    // Shared
    data object Conversations : Screen()
    data class Chat(val recipientId: Int, val recipientName: String) : Screen()
    data object Notifications : Screen()
    data class SessionDetail(val bookingId: Int) : Screen()
    data object DriveLog : Screen()
    data class Legal(val type: String) : Screen()
}

class Navigator {
    private val _backStack = mutableStateListOf<Screen>()
    val currentScreen: Screen get() = _backStack.lastOrNull() ?: Screen.Loading
    val backStackSize: Int get() = _backStack.size

    fun push(screen: Screen) { _backStack.add(screen) }
    fun pop(): Boolean {
        if (_backStack.size > 1) { _backStack.removeLast(); return true }
        return false
    }
    fun replaceAll(screen: Screen) { _backStack.clear(); _backStack.add(screen) }
    fun popToRoot() {
        val root = _backStack.first()
        _backStack.clear()
        _backStack.add(root)
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator") }

// ---------------------------------------------------------------------------
// Root
// ---------------------------------------------------------------------------

@Composable
fun RootContent() {
    val authRepository: AuthRepository = koinInject()
    val authState by authRepository.authState.collectAsState()
    val navigator = remember { Navigator() }

    LaunchedEffect(Unit) {
        authRepository.initialize()
    }

    LaunchedEffect(authState) {
        when {
            authState.isLoading -> navigator.replaceAll(Screen.Loading)
            !authState.isLoggedIn -> navigator.replaceAll(Screen.Login)
            authState.needsSetup -> {
                if (authState.role == "student") navigator.replaceAll(Screen.SetupStudent)
                else navigator.replaceAll(Screen.SetupInstructor)
            }
            authState.role == "student" -> navigator.replaceAll(Screen.StudentMain)
            authState.role == "instructor" -> navigator.replaceAll(Screen.InstructorMain)
            else -> navigator.replaceAll(Screen.Login)
        }
    }

    BackHandler(enabled = navigator.backStackSize > 1) {
        navigator.pop()
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        when (val screen = navigator.currentScreen) {
            // Loading / Auth ------------------------------------------------
            Screen.Loading -> LoadingOverlay()

            Screen.Login -> LoginScreen(
                onNavigateToRegister = { navigator.push(Screen.Register) },
            )

            Screen.Register -> RegisterScreen(
                onNavigateToLogin = { navigator.pop() },
                onRegisterSuccess = { /* authState will navigate to setup */ },
            )

            Screen.SetupStudent -> SetupStudentScreen()
            Screen.SetupInstructor -> SetupInstructorScreen()

            // Main (tabbed) -------------------------------------------------
            Screen.StudentMain -> StudentMainScreen()
            Screen.InstructorMain -> InstructorMainScreen()

            // Student -------------------------------------------------------
            is Screen.FindInstructor -> FindInstructorScreen(
                onSelectInstructor = { user ->
                    navigator.push(
                        Screen.InstructorProfile(
                            instructor = user,
                            forDiagnosticRide = screen.forDiagnosticRide,
                        )
                    )
                },
                forDiagnosticRide = screen.forDiagnosticRide,
            )

            is Screen.InstructorProfile -> InstructorProfileScreen(
                instructor = screen.instructor,
                onBookLesson = {
                    navigator.push(
                        Screen.BookingFlow(
                            instructorId = screen.instructor.id,
                            instructorName = screen.instructor.fullName,
                            forDiagnosticRide = screen.forDiagnosticRide,
                        )
                    )
                },
                onBack = { navigator.pop() },
                forDiagnosticRide = screen.forDiagnosticRide,
            )

            is Screen.BookingFlow -> BookingFlowScreen(
                instructorId = screen.instructorId,
                instructorName = screen.instructorName,
                forDiagnosticRide = screen.forDiagnosticRide,
                onSuccess = { navigator.popToRoot() },
                onBack = { navigator.pop() },
            )

            is Screen.Checkout -> CheckoutScreen(
                bookingId = screen.bookingId,
                amount = screen.amount,
                onSuccess = { navigator.popToRoot() },
                onBack = { navigator.pop() },
            )

            Screen.StudentEditProfile -> StudentEditProfileScreen(
                onBack = { navigator.pop() },
            )

            // Instructor ----------------------------------------------------
            Screen.BookingRequests -> BookingRequestsScreen(
                onBack = { navigator.pop() },
            )

            Screen.EditInstructorProfile -> EditProfileScreen(
                onBack = { navigator.pop() },
            )

            is Screen.GradeStudent -> GradeStudentScreen(
                bookingId = screen.bookingId,
                studentName = screen.studentName,
                onSuccess = { navigator.pop() },
                onBack = { navigator.pop() },
            )

            is Screen.GradeDiagnosticRide -> GradeDiagnosticRideScreen(
                rideId = screen.rideId,
                onSuccess = { navigator.pop() },
                onBack = { navigator.pop() },
            )

            Screen.StudentProgress -> StudentProgressScreen(
                onSelectStudent = { studentId, studentName ->
                    navigator.push(Screen.StudentDetailStats(studentId, studentName))
                },
                onBack = { navigator.pop() },
            )

            is Screen.StudentDetailStats -> StudentDetailStatsScreen(
                studentId = screen.studentId,
                studentName = screen.studentName,
                onBack = { navigator.pop() },
            )

            // Diagnostic Ride flow ------------------------------------------
            Screen.DiagnosticRideIntro -> DiagnosticRideIntroScreen(
                onStartParentSupervised = { navigator.push(Screen.DiagnosticRideSetup) },
                onStartInstructorSupervised = {
                    navigator.push(Screen.FindInstructor(forDiagnosticRide = true))
                },
                onBack = { navigator.pop() },
            )

            Screen.DiagnosticRideSetup -> DiagnosticRideSetupScreen(
                onContinue = { parentName, _, _ ->
                    navigator.push(Screen.SupervisorHandoff(supervisorName = parentName))
                },
                onBack = { navigator.pop() },
            )

            is Screen.SupervisorHandoff -> SupervisorHandoffScreen(
                supervisorName = screen.supervisorName,
                onReady = {
                    navigator.push(
                        Screen.DiagnosticRideActive(
                            rideType = "parent_supervised",
                            supervisorName = screen.supervisorName,
                            bookingId = null,
                        )
                    )
                },
                onBack = { navigator.pop() },
            )

            is Screen.DiagnosticRideActive -> DiagnosticRideActiveScreen(
                rideType = screen.rideType,
                supervisorName = screen.supervisorName,
                bookingId = screen.bookingId,
                onRideComplete = { rideId ->
                    if (rideId != null) {
                        navigator.push(Screen.DiagnosticRideResults(rideId))
                    } else {
                        navigator.popToRoot()
                    }
                },
                onCancel = { navigator.popToRoot() },
            )

            is Screen.DiagnosticRideResults -> DiagnosticRideResultsScreen(
                rideId = screen.rideId,
                onViewDetail = { id -> navigator.push(Screen.DiagnosticRideDetail(id)) },
                onDone = { navigator.popToRoot() },
            )

            Screen.DiagnosticRideHistory -> DiagnosticRideHistoryScreen(
                onSelectRide = { id -> navigator.push(Screen.DiagnosticRideDetail(id)) },
                onBack = { navigator.pop() },
            )

            is Screen.DiagnosticRideDetail -> DiagnosticRideDetailScreen(
                rideId = screen.rideId,
                onBack = { navigator.pop() },
            )

            // Education -----------------------------------------------------
            Screen.EducationHome -> EducationHomeScreen(
                onSelectProvince = { code -> navigator.push(Screen.EducationBook(code)) },
                onBack = { navigator.pop() },
            )

            is Screen.EducationBook -> EducationBookScreen(
                provinceCode = screen.provinceCode,
                onBack = { navigator.pop() },
            )

            Screen.Modules -> ModulesScreen(
                onSelectModule = { module -> navigator.push(Screen.Quiz(module.id)) },
                onBack = { navigator.pop() },
            )

            is Screen.Quiz -> QuizScreen(
                moduleId = screen.moduleId,
                onComplete = { navigator.pop() },
                onBack = { navigator.pop() },
            )

            // Shared --------------------------------------------------------
            Screen.Conversations -> ConversationsScreen(
                onOpenChat = { recipientId, recipientName ->
                    navigator.push(Screen.Chat(recipientId, recipientName))
                },
            )

            is Screen.Chat -> ChatScreen(
                recipientId = screen.recipientId,
                recipientName = screen.recipientName,
                onBack = { navigator.pop() },
            )

            Screen.Notifications -> NotificationsScreen(
                onBack = { navigator.pop() },
            )

            is Screen.SessionDetail -> SessionDetailScreen(
                bookingId = screen.bookingId,
                onBack = { navigator.pop() },
            )

            Screen.DriveLog -> DriveLogScreen(
                onBack = { navigator.pop() },
            )

            is Screen.Legal -> LegalScreen(
                type = screen.type,
                onBack = { navigator.pop() },
            )
        }
    }
}
