package com.roadready.ui.screens.instructor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.roadready.LocalNavigator
import com.roadready.Screen
import com.roadready.ui.screens.shared.ConversationsScreen
import com.roadready.ui.theme.*

private enum class InstructorTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Schedule("Schedule", Icons.Filled.DateRange, Icons.Outlined.DateRange),
    Earnings("Earnings", Icons.Filled.AttachMoney, Icons.Outlined.AttachMoney),
    Messages("Messages", Icons.Filled.Forum, Icons.Outlined.Forum),
    Profile("Profile", Icons.Filled.Person, Icons.Outlined.Person),
}

@Composable
fun InstructorMainScreen() {
    val navigator = LocalNavigator.current
    var selectedTab by remember { mutableStateOf(InstructorTab.Home) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                InstructorTab.entries.forEach { tab ->
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
                InstructorTab.Home -> InstructorHomeFullScreen(
                    onEditProfile = { navigator.push(Screen.EditInstructorProfile) },
                    onNotifications = { navigator.push(Screen.Notifications) },
                    onBookingRequests = { navigator.push(Screen.BookingRequests) },
                    onViewStudent = { studentId ->
                        navigator.push(Screen.StudentDetailStats(studentId, "Student"))
                    },
                    onLogout = { /* Auth state change handles navigation */ },
                )

                InstructorTab.Schedule -> InstructorScheduleScreen(
                    onSessionDetail = { booking ->
                        navigator.push(Screen.SessionDetail(booking.id))
                    },
                )

                InstructorTab.Earnings -> InstructorEarningsScreen()

                InstructorTab.Messages -> ConversationsScreen(
                    onOpenChat = { recipientId, recipientName ->
                        navigator.push(Screen.Chat(recipientId, recipientName))
                    },
                )

                InstructorTab.Profile -> EditProfileScreen(
                    onBack = { selectedTab = InstructorTab.Home },
                )
            }
        }
    }
}
