package com.roadready.data.repository

import com.roadready.data.model.*
import com.roadready.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthState(
    val isLoading: Boolean = true,
    val token: String? = null,
    val user: User? = null,
    val error: String? = null,
) {
    val isLoggedIn: Boolean get() = token != null
    val role: String? get() = user?.role
    val needsSetup: Boolean
        get() {
            val u = user ?: return false
            return when (u.role) {
                "student" -> u.studentProfile == null || u.studentProfile.age == 0
                "instructor" -> {
                    val p = u.instructorProfile
                    p == null || p.hourlyRate == 0.0 || p.bio == "New User" || p.carModel == "Not Specified"
                }
                else -> false
            }
        }
}

interface TokenStorage {
    suspend fun getToken(): String?
    suspend fun saveToken(token: String)
    suspend fun clearToken()
}

class AuthRepository(
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
) {
    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    suspend fun initialize() {
        val savedToken = tokenStorage.getToken()
        if (savedToken != null) {
            _authState.value = AuthState(isLoading = true, token = savedToken)
            fetchCurrentUser()
        } else {
            _authState.value = AuthState(isLoading = false)
        }
    }

    suspend fun login(email: String, password: String): Result<User> {
        _authState.value = _authState.value.copy(isLoading = true, error = null)

        val result = apiClient.login(LoginRequest(email, password))
        return result.fold(
            onSuccess = { authResponse ->
                tokenStorage.saveToken(authResponse.accessToken)
                _authState.value = _authState.value.copy(token = authResponse.accessToken)
                fetchCurrentUser()
            },
            onFailure = { error ->
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Login failed",
                )
                Result.failure(error)
            },
        )
    }

    suspend fun register(
        fullName: String,
        email: String,
        password: String,
        phoneNumber: String,
        role: String,
        city: String? = null,
        province: String? = null,
        bio: String? = null,
        hourlyRate: Double? = null,
        carMake: String? = null,
        carModel: String? = null,
        carYear: Int? = null,
        insurancePolicy: String? = null,
        certificationId: String? = null,
        licenseClasses: List<InstructorLicenseClass>? = null,
        age: Int? = null,
        licenseNumber: String? = null,
    ): Result<AuthResponse> {
        _authState.value = _authState.value.copy(isLoading = true, error = null)

        val result = apiClient.register(
            RegisterRequest(
                email = email,
                password = password,
                fullName = fullName,
                phoneNumber = phoneNumber,
                role = role,
                city = city ?: "Unknown",
                province = province ?: "British Columbia",
                bio = bio ?: "New User",
                hourlyRate = hourlyRate ?: 0.0,
                carModel = carModel ?: "Not Specified",
                insurancePolicy = insurancePolicy ?: "PENDING",
                certificationId = certificationId ?: "PENDING",
                licenseClasses = licenseClasses ?: emptyList(),
                age = age ?: 0,
                licenseNumber = licenseNumber ?: "0000000"
            ),
        )
        result.onSuccess { authResponse ->
            tokenStorage.saveToken(authResponse.accessToken)
            _authState.value = _authState.value.copy(token = authResponse.accessToken, isLoading = true)
            fetchCurrentUser()
        }.onFailure { error ->
            _authState.value = _authState.value.copy(
                isLoading = false,
                error = error.message ?: "Registration failed",
            )
        }
        return result
    }

    suspend fun logout() {
        tokenStorage.clearToken()
        _authState.value = AuthState(isLoading = false)
    }

    private suspend fun fetchCurrentUser(): Result<User> {
        _authState.value = _authState.value.copy(isLoading = true)
        val result = apiClient.getCurrentUser()
        result.fold(
            onSuccess = { user ->
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    user = user,
                    error = null,
                )
            },
            onFailure = { error ->
                tokenStorage.clearToken()
                _authState.value = AuthState(
                    isLoading = false,
                    error = "Session expired",
                )
            },
        )
        return result
    }
}
