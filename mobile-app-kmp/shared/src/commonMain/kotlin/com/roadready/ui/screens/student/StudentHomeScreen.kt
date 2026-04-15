package com.roadready.ui.screens.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.data.model.Booking
import com.roadready.data.remote.ApiClient
import com.roadready.data.repository.AuthRepository
import com.roadready.ui.components.GlassCard
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

    val nextLesson = viewModel.upcomingBookings.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Top Bar / Greeting
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Hey, ${viewModel.userName} 👋",
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Ready to hit the road?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Surface)
                        .clickable { onOpenNotifications() }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = TextPrimary
                    )
                }
            }
        }

        // Stats Summary
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassCard(modifier = Modifier.weight(1f)) {
                    Text("12.5", style = MaterialTheme.typography.headlineLarge, color = Primary)
                    Text("Hours Logged", style = MaterialTheme.typography.bodySmall)
                }
                GlassCard(modifier = Modifier.weight(1f)) {
                    Text("75%", style = MaterialTheme.typography.headlineLarge, color = Secondary)
                    Text("Skills Mastery", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Quick Actions Bento Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BentoActionCard(
                    title = "Diagnostic Ride",
                    subtitle = "Test your skills with AI",
                    icon = Icons.Rounded.DirectionsCar,
                    gradient = Brush.linearGradient(listOf(Primary, PrimaryContainer)),
                    onClick = onStartDiagnostic,
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BentoActionCard(
                        title = "Find Instructor",
                        subtitle = "Book a lesson",
                        icon = Icons.Rounded.Search,
                        gradient = Brush.linearGradient(listOf(Secondary, SecondaryContainer)),
                        onClick = onFindInstructor,
                        modifier = Modifier.weight(1f).height(120.dp)
                    )
                    BentoActionCard(
                        title = "History",
                        subtitle = "Your progress",
                        icon = Icons.Rounded.History,
                        gradient = Brush.linearGradient(listOf(SurfaceVariant, Surface)),
                        onClick = onViewHistory,
                        modifier = Modifier.weight(1f).height(120.dp)
                    )
                }
            }
        }

        // Next Lesson Spotlight
        if (nextLesson != null) {
            item {
                SectionHeader("Next Lesson")
                SpotlightBookingCard(booking = nextLesson)
            }
        }

        // Upcoming & Pending
        if (viewModel.upcomingBookings.size > 1 || viewModel.pendingBookings.isNotEmpty()) {
            item { SectionHeader("All Lessons") }
            
            val otherUpcoming = if (viewModel.upcomingBookings.isNotEmpty()) 
                viewModel.upcomingBookings.drop(1) else emptyList()
            
            items(otherUpcoming) { booking ->
                ModernBookingCard(booking = booking)
            }
            
            items(viewModel.pendingBookings) { booking ->
                ModernBookingCard(booking = booking, isPending = true)
            }
        }

        // Empty State
        if (viewModel.upcomingBookings.isEmpty() && viewModel.pendingBookings.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📚", fontSize = 48.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("No upcoming lessons", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Start a diagnostic ride to begin",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Logout
        item {
            TextButton(
                onClick = { viewModel.logout() },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign Out", color = TextMuted)
            }
        }
    }
}

@Composable
private fun BentoActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SpotlightBookingCard(booking: Booking) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassStroke)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        booking.instructor?.fullName?.firstOrNull()?.toString() ?: "I",
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        booking.instructor?.fullName ?: "Instructor",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "Certified Professional",
                        style = MaterialTheme.typography.bodySmall,
                        color = Secondary
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem(label = "Date", value = booking.scheduledDate ?: "TBD")
                InfoItem(label = "Time", value = booking.scheduledTime ?: "TBD")
                InfoItem(label = "Pickup", value = booking.pickupAddress?.split(",")?.firstOrNull() ?: "TBD")
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ModernBookingCard(
    booking: Booking,
    isPending: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        color = Surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassStroke.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = booking.instructor?.fullName ?: "Instructor",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${booking.scheduledDate} • ${booking.scheduledTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            val (statusColor, statusText) = if (isPending) {
                Warning to "Pending"
            } else {
                Secondary to "Confirmed"
            }
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
        }
    }
}
