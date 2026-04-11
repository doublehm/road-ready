package com.roadready.ui.screens.instructor

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
import org.koin.compose.koinInject

@Composable
fun InstructorHomeFullScreen(
    onEditProfile: () -> Unit,
    onNotifications: () -> Unit,
    onBookingRequests: () -> Unit,
    onViewStudent: (Int) -> Unit,
    onLogout: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val authRepository: AuthRepository = koinInject()
    val authState by authRepository.authState.collectAsState()
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var bookings by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var pendingCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        apiClient.getBookings().onSuccess { all ->
            bookings = all.filter { it.status == "accepted" }
            pendingCount = all.count { it.status == "pending" }
        }
        isLoading = false
    }

    if (isLoading) { LoadingOverlay(); return }

    val userName = authState.user?.fullName?.split(" ")?.firstOrNull() ?: "Instructor"
    val profile = authState.user?.instructorProfile
    val completedCount = bookings.count { it.status == "completed" }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
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
                    Text("Hello, $userName 👋", style = MaterialTheme.typography.headlineLarge)
                    Text("Manage your lessons", style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onNotifications) {
                    Text("🔔", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }

        // Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard("💰", "Rate", "$${profile?.hourlyRate?.toInt() ?: 0}/hr", Accent, Modifier.weight(1f))
                StatCard("📅", "Upcoming", "${bookings.size}", Primary, Modifier.weight(1f))
                StatCard("📬", "Pending", "$pendingCount", Warning, Modifier.weight(1f))
            }
        }

        // Quick Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickAction("✏️", "Edit Profile", Accent, onEditProfile, Modifier.weight(1f))
                QuickAction("📬", "Requests ($pendingCount)", Warning, onBookingRequests, Modifier.weight(1f))
            }
        }

        // Upcoming Lessons
        if (bookings.isNotEmpty()) {
            item { SectionHeader("Upcoming Lessons") }
            items(bookings.take(5)) { booking ->
                InstructorBookingCard(booking) {
                    booking.studentId.let { onViewStudent(it) }
                }
            }
        }

        // Empty State
        if (bookings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("📚", style = MaterialTheme.typography.displayLarge)
                        Text("No upcoming lessons", style = MaterialTheme.typography.titleMedium)
                        Text("Check your booking requests", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }

        item {
            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("Sign Out", color = TextMuted)
            }
        }
    }
}

@Composable
private fun StatCard(emoji: String, label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(value, style = MaterialTheme.typography.titleMedium, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun QuickAction(emoji: String, label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

@Composable
private fun InstructorBookingCard(booking: Booking, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    booking.student?.fullName ?: "Student #${booking.studentId}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                booking.scheduledDate?.let {
                    Text("$it • ${booking.scheduledTime ?: ""}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Surface(color = AccentLight.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                Text(
                    "Confirmed", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = AccentLight,
                )
            }
        }
    }
}
