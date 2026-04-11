package com.roadready.ui.screens.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roadready.data.model.Message
import com.roadready.data.remote.ApiClient
import com.roadready.data.repository.AuthRepository
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.theme.*
import org.koin.compose.koinInject

data class Conversation(
    val recipientId: Int,
    val recipientName: String,
    val lastMessage: String,
    val lastTime: String?,
    val unreadCount: Int,
)

@Composable
fun ConversationsScreen(
    onOpenChat: (recipientId: Int, recipientName: String) -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val authRepository: AuthRepository = koinInject()
    val authState by authRepository.authState.collectAsState()
    val currentUserId = authState.user?.id ?: 0

    var isLoading by remember { mutableStateOf(true) }
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }

    LaunchedEffect(Unit) {
        val bookingsResult = apiClient.getBookings()
        val messagesResult = apiClient.getMessages()

        val bookings = bookingsResult.getOrDefault(emptyList())
        val messages = messagesResult.getOrDefault(emptyList())

        val convMap = mutableMapOf<Int, Conversation>()
        bookings.filter { it.status in listOf("accepted", "completed", "pending") }.forEach { booking ->
            val isStudent = authState.role == "student"
            val recipientId = if (isStudent) booking.instructorId else booking.studentId
            val recipientName = if (isStudent) {
                booking.instructor?.fullName ?: "Instructor #${booking.instructorId}"
            } else {
                booking.student?.fullName ?: "Student #${booking.studentId}"
            }

            val relevantMsgs = messages.filter {
                (it.senderId == recipientId && it.recipientId == currentUserId) ||
                (it.senderId == currentUserId && it.recipientId == recipientId)
            }.sortedByDescending { it.createdAt }

            val lastMsg = relevantMsgs.firstOrNull()
            val unread = relevantMsgs.count { !it.isRead && it.recipientId == currentUserId }

            convMap[recipientId] = Conversation(
                recipientId = recipientId,
                recipientName = recipientName,
                lastMessage = lastMsg?.content ?: "No messages yet",
                lastTime = lastMsg?.createdAt,
                unreadCount = unread,
            )
        }
        conversations = convMap.values.sortedByDescending { it.lastTime }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Messages", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(16.dp))

        if (isLoading) { LoadingOverlay(); return }

        if (conversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💬", style = MaterialTheme.typography.displayLarge)
                    Text("No conversations yet", style = MaterialTheme.typography.titleMedium)
                    Text("Book a lesson to start chatting", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(conversations) { conv ->
                    ConversationItem(conv) { onOpenChat(conv.recipientId, conv.recipientName) }
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(conv: Conversation, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (conv.unreadCount > 0) Primary.copy(alpha = 0.05f) else Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(modifier = Modifier.size(44.dp).clip(CircleShape), color = Primary.copy(alpha = 0.2f)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(conv.recipientName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = Primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(conv.recipientName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(conv.lastMessage, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = if (conv.unreadCount > 0) TextPrimary else TextMuted)
            }
            if (conv.unreadCount > 0) {
                Badge(containerColor = Primary) { Text("${conv.unreadCount}") }
            }
        }
    }
}
