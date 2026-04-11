package com.roadready.ui.screens.student

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roadready.data.model.Booking
import com.roadready.data.remote.ApiClient
import com.roadready.data.repository.AuthRepository
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.components.SectionHeader
import com.roadready.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

class StudentHomeViewModel(
    private val apiClient: ApiClient,
    private val authRepository: AuthRepository,
) : androidx.lifecycle.ViewModel() {
    var isLoading by mutableStateOf(true)
    var upcomingBookings by mutableStateOf<List<Booking>>(emptyList())
    var pendingBookings by mutableStateOf<List<Booking>>(emptyList())
    var userName by mutableStateOf("")

    init {
        val user = authRepository.authState.value.user
        userName = user?.fullName?.split(" ")?.firstOrNull() ?: "Student"
        loadBookings()
    }

    fun loadBookings() {
        isLoading = true
        kotlinx.coroutines.MainScope().launch {
            apiClient.getBookings().onSuccess { bookings ->
                upcomingBookings = bookings.filter { it.status == "accepted" }
                pendingBookings = bookings.filter { it.status == "pending" }
            }
            isLoading = false
        }
    }

    fun logout() {
        kotlinx.coroutines.MainScope().launch {
            authRepository.logout()
        }
    }
}

@Composable
fun StudentHomeScreen(
    onStartDiagnostic: () -> Unit,
    onFindInstructor: () -> Unit,
    onViewHistory: () -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: StudentHomeViewModel = koinViewModel(),
) {
    if (viewModel.isLoading) {
        LoadingOverlay()
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Greeting
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Hey, ${viewModel.userName} 👋",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = "Ready to hit the road?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(onClick = onOpenNotifications) {
                    Text("🔔", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }

        // Quick Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickActionCard(
                    emoji = "🛞",
                    title = "Diagnostic Ride",
                    subtitle = "Test your skills",
                    color = Primary,
                    onClick = onStartDiagnostic,
                    modifier = Modifier.weight(1f),
                )
                QuickActionCard(
                    emoji = "🔍",
                    title = "Find Instructor",
                    subtitle = "Book a lesson",
                    color = Accent,
                    onClick = onFindInstructor,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Upcoming Lessons
        if (viewModel.upcomingBookings.isNotEmpty()) {
            item { SectionHeader("Upcoming Lessons") }
            items(viewModel.upcomingBookings) { booking ->
                BookingCard(booking = booking)
            }
        }

        // Pending Requests
        if (viewModel.pendingBookings.isNotEmpty()) {
            item { SectionHeader("Pending Requests") }
            items(viewModel.pendingBookings) { booking ->
                BookingCard(booking = booking, isPending = true)
            }
        }

        // Empty State
        if (viewModel.upcomingBookings.isEmpty() && viewModel.pendingBookings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("📚", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No upcoming lessons",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Start a diagnostic ride or find an instructor to begin",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                        )
                    }
                }
            }
        }

        // Ride History shortcut
        item {
            OutlinedButton(
                onClick = onViewHistory,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
            ) {
                Text("View Diagnostic Ride History")
            }
        }

        // Logout
        item {
            TextButton(
                onClick = { viewModel.logout() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign Out", color = TextMuted)
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    emoji: String,
    title: String,
    subtitle: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = color)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BookingCard(
    booking: Booking,
    isPending: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = booking.instructor?.fullName ?: "Instructor",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                booking.scheduledDate?.let {
                    Text(
                        text = "$it • ${booking.scheduledTime ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                booking.pickupAddress?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Status chip
            val (statusColor, statusText) = if (isPending) {
                Warning to "Pending"
            } else {
                AccentLight to "Confirmed"
            }
            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                )
            }
        }
    }
}
