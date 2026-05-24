package com.roadready.ui.screens.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.data.model.StudentProfileCreateRequest
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
    val clipboardManager = LocalClipboardManager.current

    var fullName by remember { mutableStateOf(user?.fullName ?: "") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var phone by remember { mutableStateOf(user?.phoneNumber ?: "") }
    var city by remember { mutableStateOf(profile?.city ?: "") }
    var province by remember { mutableStateOf(profile?.province ?: "") }
    
    var isLoading by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var isAnonymizing by remember { mutableStateOf(false) }
    
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    
    var exportedData by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAnonymizeConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = Primary) }
        Text("Edit Profile", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 20.dp))

        error?.let { ErrorBanner(it, Modifier.padding(bottom = 16.dp)) }
        if (success) {
            Card(colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.15f))) {
                Text("✅ Profile updated successfully!", modifier = Modifier.padding(12.dp), color = Accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }

        // --- Basic Info Section ---
        Text("Personal Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        RoadReadyTextField(value = fullName, onValueChange = { fullName = it }, label = "Full Name", modifier = Modifier.padding(bottom = 12.dp))
        
        // Email & Phone protected warning text
        RoadReadyTextField(value = email, onValueChange = {}, label = "Email (ReadOnly)", enabled = false, modifier = Modifier.padding(bottom = 4.dp))
        Text("Contact email cannot be changed directly for account security.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 12.dp))
        
        RoadReadyTextField(value = phone, onValueChange = {}, label = "Phone Number (ReadOnly)", enabled = false, modifier = Modifier.padding(bottom = 4.dp))
        Text("Contact phone cannot be changed directly for account security.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 12.dp))
        
        RoadReadyTextField(value = city, onValueChange = { city = it }, label = "City", modifier = Modifier.padding(bottom = 12.dp))
        RoadReadyTextField(value = province, onValueChange = { province = it }, label = "Province", modifier = Modifier.padding(bottom = 20.dp))

        PrimaryButton(
            text = "Save Profile Info",
            isLoading = isLoading,
            onClick = {
                isLoading = true; error = null; success = false
                scope.launch {
                    // Update user basic info
                    val userResult = apiClient.updateProfile(mapOf("full_name" to fullName))
                    if (userResult.isFailure) {
                        error = userResult.exceptionOrNull()?.message ?: "User update failed"
                        isLoading = false
                        return@launch
                    }

                    val profileResult = apiClient.setupStudentProfile(StudentProfileCreateRequest(
                        age = profile?.age ?: 18,
                        licenseNumber = profile?.licenseNumber ?: "0000000",
                        licenseClass = profile?.licenseClass ?: "Class 7",
                        city = city,
                        province = province,
                    ))

                    if (profileResult.isSuccess) {
                        success = true
                        authRepository.initialize() // Refresh local user state
                    } else {
                        error = profileResult.exceptionOrNull()?.message ?: "Profile update failed"
                    }
                    isLoading = false
                }
            },
        )

        Spacer(Modifier.height(24.dp))

        // --- Verification Center ---
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, Primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🛡️ Verification & Compliance Center", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
                Spacer(Modifier.height(8.dp))
                
                val currentStatus = profile?.licenseStatus ?: "pending_upload"
                val statusText = when (currentStatus) {
                    "verified" -> "✅ VERIFIED & APPROVED"
                    "submitted" -> "⏳ PENDING ADMIN REVIEW"
                    "rejected" -> "❌ REJECTED"
                    else -> "⚠️ ACTION REQUIRED: UPLOAD LICENSE"
                }
                val statusColor = when (currentStatus) {
                    "verified" -> Accent
                    "submitted" -> Color(0xFFD97706)
                    "rejected" -> Color.Red
                    else -> Color.DarkGray
                }

                Text(statusText, fontWeight = FontWeight.Bold, color = statusColor, fontSize = 14.sp)
                
                if (currentStatus == "rejected" && !profile?.rejectionReason.isNullOrEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reason: ${profile?.rejectionReason}", color = Color.Red, modifier = Modifier.padding(8.dp), fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                
                PrimaryButton(
                    text = if (currentStatus == "verified") "Re-upload Driver's License" else "Upload Simulated Driver's License",
                    isLoading = isUploading,
                    onClick = {
                        isUploading = true; error = null; success = false
                        scope.launch {
                            val dummyFileBytes = "Simulated Driver License Image Content".encodeToByteArray()
                            val uploadResult = apiClient.uploadDocument("student_license", dummyFileBytes, "simulated_license.jpg")
                            if (uploadResult.isSuccess) {
                                success = true
                                authRepository.initialize() // Refresh status from backend
                            } else {
                                error = uploadResult.exceptionOrNull()?.message ?: "License upload failed"
                            }
                            isUploading = false
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- GDPR & Privacy Controls ---
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🔐 GDPR & Privacy Sovereignty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Spacer(Modifier.height(4.dp))
                Text("Manage your personal identifiers, requested document logs, and Right to be Forgotten compliance parameters.", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            isExporting = true; error = null
                            scope.launch {
                                val exportResult = apiClient.exportPersonalData()
                                if (exportResult.isSuccess) {
                                    exportedData = exportResult.getOrNull()
                                    showExportDialog = true
                                } else {
                                    error = exportResult.exceptionOrNull()?.message ?: "Data export failed"
                                }
                                isExporting = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    ) {
                        Text("📥 Export", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showAnonymizeConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.weight(1f).padding(start = 6.dp)
                    ) {
                        Text("⚠️ Anonymize", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // GDPR Export Dialog
    if (showExportDialog && exportedData != null) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Personal Data Export Bundle") },
            text = {
                Column {
                    Text("Here is your personal data currently stored in our system. Tap below to copy to clipboard.", fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f)),
                        modifier = Modifier.height(200.dp).verticalScroll(rememberScrollState())
                    ) {
                        Text(exportedData ?: "", modifier = Modifier.padding(8.dp), fontSize = 10.sp, color = Color.DarkGray)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(exportedData ?: ""))
                    showExportDialog = false
                }) {
                    Text("📋 Copy & Close", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Close", color = Color.Gray) }
            }
        )
    }

    // GDPR Anonymization Confirm Dialog
    if (showAnonymizeConfirm) {
        AlertDialog(
            onDismissRequest = { showAnonymizeConfirm = false },
            title = { Text("Anonymize Profile (Right to be Forgotten)?") },
            text = {
                Text("WARNING: This will permanently delete your driver license numbers, uploaded files, addresses, and identifying parameters. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAnonymizeConfirm = false
                        isAnonymizing = true
                        scope.launch {
                            val result = apiClient.anonymizeProfile()
                            if (result.isSuccess) {
                                authRepository.logout() // Logs user out locally
                            } else {
                                error = result.exceptionOrNull()?.message ?: "Anonymization failed"
                            }
                            isAnonymizing = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("⚠️ Permanently Anonymize")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAnonymizeConfirm = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }
}
