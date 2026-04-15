package com.roadready.ui.screens.instructor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.roadready.LocalNavigator
import com.roadready.Screen
import com.roadready.data.repository.AuthRepository
import com.roadready.ui.screens.shared.ConversationsScreen
import com.roadready.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class InstructorTab(
    val label: String,
    val icon: ImageVector,
) {
    Home("Home", Icons.Rounded.Home),
    Schedule("Schedule", Icons.Rounded.DateRange),
    Earnings("Earnings", Icons.Rounded.AttachMoney),
    Messages("Messages", Icons.Rounded.Forum),
    Profile("Profile", Icons.Rounded.Person),
}

@Composable
fun InstructorMainScreen() {
    val navigator = LocalNavigator.current
    val authRepository: AuthRepository = koinInject()
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(InstructorTab.Home) }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            CustomInstructorBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(bottom = 80.dp)) {
            when (selectedTab) {
                InstructorTab.Home -> InstructorHomeFullScreen(
                    onEditProfile = { navigator.push(Screen.EditInstructorProfile) },
                    onNotifications = { navigator.push(Screen.Notifications) },
                    onBookingRequests = { navigator.push(Screen.BookingRequests) },
                    onViewStudent = { studentId ->
                        navigator.push(Screen.StudentDetailStats(studentId, "Student"))
                    },
                    onLogout = { 
                        scope.launch { authRepository.logout() }
                    },
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

@Composable
private fun CustomInstructorBottomNav(
    selectedTab: InstructorTab,
    onTabSelected: (InstructorTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = Surface.copy(alpha = 0.9f),
            shape = CircleShape,
            modifier = Modifier
                .height(64.dp)
                .fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassStroke),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                InstructorTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    InstructorNavItem(
                        tab = tab,
                        selected = selected,
                        onClick = { onTabSelected(tab) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstructorNavItem(
    tab: InstructorTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (selected) Primary else TextMuted,
            modifier = Modifier.size(24.dp)
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Primary)
            )
        }
    }
}
