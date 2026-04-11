package com.roadready.ui.screens.student

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.roadready.LocalNavigator
import com.roadready.Screen
import com.roadready.ui.screens.diagnostic.DiagnosticRideHistoryScreen
import com.roadready.ui.screens.education.ModulesScreen
import com.roadready.ui.screens.shared.ConversationsScreen
import com.roadready.ui.theme.*

private enum class StudentTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Rides("Rides", Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar),
    Learn("Learn", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    Messages("Messages", Icons.Filled.Forum, Icons.Outlined.Forum),
    Profile("Profile", Icons.Filled.Person, Icons.Outlined.Person),
}

@Composable
fun StudentMainScreen() {
    val navigator = LocalNavigator.current
    var selectedTab by remember { mutableStateOf(StudentTab.Home) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                StudentTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Primary,
                            selectedTextColor = Primary,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = SurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                StudentTab.Home -> StudentHomeScreen(
                    onStartDiagnostic = { navigator.push(Screen.DiagnosticRideIntro) },
                    onFindInstructor = { navigator.push(Screen.FindInstructor()) },
                    onViewHistory = { navigator.push(Screen.DiagnosticRideHistory) },
                    onOpenNotifications = { navigator.push(Screen.Notifications) },
                )

                StudentTab.Rides -> DiagnosticRideHistoryScreen(
                    onSelectRide = { rideId -> navigator.push(Screen.DiagnosticRideDetail(rideId)) },
                    onBack = { selectedTab = StudentTab.Home },
                )

                StudentTab.Learn -> ModulesScreen(
                    onSelectModule = { module -> navigator.push(Screen.Quiz(module.id)) },
                    onBack = { selectedTab = StudentTab.Home },
                )

                StudentTab.Messages -> ConversationsScreen(
                    onOpenChat = { recipientId, recipientName ->
                        navigator.push(Screen.Chat(recipientId, recipientName))
                    },
                )

                StudentTab.Profile -> StudentEditProfileScreen(
                    onBack = { selectedTab = StudentTab.Home },
                )
            }
        }
    }
}
