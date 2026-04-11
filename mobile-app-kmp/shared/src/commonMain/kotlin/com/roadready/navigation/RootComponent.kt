package com.roadready.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.roadready.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable

class RootComponent(
    componentContext: ComponentContext,
    private val authRepository: AuthRepository,
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val navigation = StackNavigation<Config>()

    val childStack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Splash,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    init {
        authRepository.authState.onEach { state ->
            if (!state.isLoading) {
                when {
                    !state.isLoggedIn -> navigation.replaceAll(Config.Login)
                    state.needsSetup -> {
                        if (state.role == "student") {
                            navigation.replaceAll(Config.SetupStudent)
                        } else {
                            navigation.replaceAll(Config.SetupInstructor)
                        }
                    }
                    state.role == "student" -> navigation.replaceAll(Config.StudentMain)
                    state.role == "instructor" -> navigation.replaceAll(Config.InstructorMain)
                }
            }
        }.launchIn(scope)
    }

    private fun createChild(config: Config, componentContext: ComponentContext): Child {
        return when (config) {
            Config.Splash -> Child.Splash
            Config.Login -> Child.Login
            Config.Register -> Child.Register
            Config.SetupStudent -> Child.SetupStudent
            Config.SetupInstructor -> Child.SetupInstructor
            Config.StudentMain -> Child.StudentMain
            Config.InstructorMain -> Child.InstructorMain
            is Config.Chat -> Child.Chat(config.recipientId, config.recipientName)
            is Config.InstructorProfile -> Child.InstructorProfile(config.instructorId)
            is Config.DiagnosticRideDetail -> Child.DiagnosticRideDetail(config.rideId)
            Config.Notifications -> Child.Notifications
            Config.DiagnosticRideIntro -> Child.DiagnosticRideIntro
            Config.DiagnosticRideHistory -> Child.DiagnosticRideHistory
            Config.FindInstructor -> Child.FindInstructor
        }
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateTo(config: Config) {
        navigation.push(config)
    }

    fun navigateBack() {
        navigation.pop()
    }

    @Serializable
    sealed class Config {
        @Serializable data object Splash : Config()
        @Serializable data object Login : Config()
        @Serializable data object Register : Config()
        @Serializable data object SetupStudent : Config()
        @Serializable data object SetupInstructor : Config()
        @Serializable data object StudentMain : Config()
        @Serializable data object InstructorMain : Config()
        @Serializable data object Notifications : Config()
        @Serializable data object DiagnosticRideIntro : Config()
        @Serializable data object DiagnosticRideHistory : Config()
        @Serializable data object FindInstructor : Config()
        @Serializable data class Chat(val recipientId: Int, val recipientName: String) : Config()
        @Serializable data class InstructorProfile(val instructorId: Int) : Config()
        @Serializable data class DiagnosticRideDetail(val rideId: Int) : Config()
    }

    sealed class Child {
        data object Splash : Child()
        data object Login : Child()
        data object Register : Child()
        data object SetupStudent : Child()
        data object SetupInstructor : Child()
        data object StudentMain : Child()
        data object InstructorMain : Child()
        data object Notifications : Child()
        data object DiagnosticRideIntro : Child()
        data object DiagnosticRideHistory : Child()
        data object FindInstructor : Child()
        data class Chat(val recipientId: Int, val recipientName: String) : Child()
        data class InstructorProfile(val instructorId: Int) : Child()
        data class DiagnosticRideDetail(val rideId: Int) : Child()
    }
}
