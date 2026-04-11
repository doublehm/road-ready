package com.roadready.ui.screens.student

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadready.data.remote.ApiClient
import com.roadready.data.repository.AuthRepository
import com.roadready.ui.components.ErrorBanner
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.components.RoadReadyTextField
import com.roadready.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun StudentEditProfileScreen(
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val authRepository: AuthRepository = koinInject()
    val authState by authRepository.authState.collectAsState()
    val user = authState.user
    val profile = user?.studentProfile
    val scope = rememberCoroutineScope()

    var fullName by remember { mutableStateOf(user?.fullName ?: "") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var phone by remember { mutableStateOf(user?.phone ?: "") }
    var city by remember { mutableStateOf(profile?.city ?: "") }
    var province by remember { mutableStateOf(profile?.province ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = Primary) }
        Text("Edit Profile", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 24.dp))

        error?.let { ErrorBanner(it, Modifier.padding(bottom = 16.dp)) }
        if (success) {
            Card(colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.15f))) {
                Text("✅ Profile updated!", modifier = Modifier.padding(12.dp), color = Accent)
            }
            Spacer(Modifier.height(16.dp))
        }

        RoadReadyTextField(value = fullName, onValueChange = { fullName = it }, label = "Full Name", modifier = Modifier.padding(bottom = 12.dp))
        RoadReadyTextField(value = email, onValueChange = { email = it }, label = "Email", modifier = Modifier.padding(bottom = 12.dp))
        RoadReadyTextField(value = phone, onValueChange = { phone = it }, label = "Phone", modifier = Modifier.padding(bottom = 12.dp))
        RoadReadyTextField(value = city, onValueChange = { city = it }, label = "City", modifier = Modifier.padding(bottom = 12.dp))
        RoadReadyTextField(value = province, onValueChange = { province = it }, label = "Province", modifier = Modifier.padding(bottom = 24.dp))

        PrimaryButton(
            text = "Save Changes",
            isLoading = isLoading,
            onClick = {
                isLoading = true; error = null; success = false
                scope.launch {
                    apiClient.updateProfile(mapOf(
                        "full_name" to fullName, "email" to email, "phone" to phone,
                        "city" to city, "province" to province,
                    )).onSuccess { success = true }
                        .onFailure { error = it.message ?: "Update failed" }
                    isLoading = false
                }
            },
        )

        Spacer(Modifier.height(32.dp))
    }
}
