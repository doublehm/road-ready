package com.roadready.ui.screens.student

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.roadready.LocalNavigator
import com.roadready.Screen
import com.roadready.ui.screens.diagnostic.DiagnosticRideHistoryScreen
import com.roadready.ui.screens.education.ModulesScreen
import com.roadready.ui.screens.shared.ConversationsScreen
import com.roadready.ui.theme.*

private enum class StudentTab(
    val label: String,
    val icon: ImageVector,
) {
    Home("Home", Icons.Rounded.Home),
    Rides("Rides", Icons.Rounded.DirectionsCar),
    Learn("Learn", Icons.Rounded.MenuBook),
    Messages("Messages", Icons.Rounded.Forum),
    Profile("Profile", Icons.Rounded.Person),
}

@Composable
fun StudentMainScreen() {
    val navigator = LocalNavigator.current
    var selectedTab by remember { mutableStateOf(StudentTab.Home) }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            CustomBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(bottom = 80.dp)) {
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

@Composable
private fun CustomBottomNav(
    selectedTab: StudentTab,
    onTabSelected: (StudentTab) -> Unit
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
                StudentTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    NavItem(
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
private fun NavItem(
    tab: StudentTab,
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
