package com.roadready.ui.screens.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadready.data.model.Message
import com.roadready.data.remote.ApiClient
import com.roadready.data.repository.AuthRepository
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ChatScreen(
    recipientId: Int,
    recipientName: String,
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val authRepository: AuthRepository = koinInject()
    val authState by authRepository.authState.collectAsState()
    val currentUserId = authState.user?.id ?: 0
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var newMessage by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    LaunchedEffect(recipientId) {
        while (true) {
            apiClient.getMessages(recipientId).onSuccess { allMsgs ->
                val relevant = allMsgs.filter {
                    (it.senderId == recipientId && it.recipientId == currentUserId) ||
                    (it.senderId == currentUserId && it.recipientId == recipientId)
                }.sortedBy { it.createdAt }
                messages = relevant
                relevant.filter { !it.isRead && it.recipientId == currentUserId }.forEach { msg ->
                    apiClient.markMessageRead(msg.id)
                }
            }
            isLoading = false
            delay(5000)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = Surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge, color = Primary) }
                Spacer(Modifier.width(8.dp))
                Text(recipientName, style = MaterialTheme.typography.titleLarge)
            }
        }

        if (isLoading) { LoadingOverlay(); return }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(messages) { msg ->
                val isMine = msg.senderId == currentUserId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMine) Primary else SurfaceVariant,
                        ),
                        shape = RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (isMine) 16.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 16.dp,
                        ),
                    ) {
                        Text(
                            msg.content,
                            modifier = Modifier.padding(12.dp).widthIn(max = 260.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                    }
                }
            }
        }

        Surface(color = Surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newMessage,
                    onValueChange = { newMessage = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...", color = TextMuted) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = SurfaceVariant,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    ),
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newMessage.isBlank()) return@Button
                        val content = newMessage
                        newMessage = ""
                        isSending = true
                        scope.launch {
                            apiClient.sendMessage(recipientId, content)
                            isSending = false
                        }
                    },
                    enabled = newMessage.isNotBlank() && !isSending,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("Send")
                }
            }
        }
    }
}
