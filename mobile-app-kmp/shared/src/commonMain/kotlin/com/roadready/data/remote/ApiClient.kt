package com.roadready.data.remote

import com.roadready.createPlatformHttpClient
import com.roadready.data.model.*
import com.roadready.data.repository.SpeedLimitResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class ApiClient(
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
) {
    val httpClient: HttpClient = createPlatformHttpClient().config {
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
            tokenProvider()?.let { token ->
                header("Authorization", "Bearer $token")
            }
        }
    }

    // --- Auth ---

    suspend fun login(request: LoginRequest): Result<AuthResponse> = safeCall {
        httpClient.submitForm(
            url = "auth/login",
            formParameters = Parameters.build {
                append("username", request.email)
                append("password", request.password)
            }
        ).body()
    }

    suspend fun register(request: RegisterRequest): Result<AuthResponse> = safeCall {
        httpClient.post("users/") {
            setBody(request)
        }.body()
    }

    suspend fun getCurrentUser(): Result<User> = safeCall {
        httpClient.get("users/me").body()
    }

    // --- Bookings ---

    suspend fun getBookings(status: String? = null): Result<List<Booking>> = safeCall {
        httpClient.get("bookings/") {
            status?.let { parameter("status", it) }
        }.body()
    }

    suspend fun createBooking(booking: Map<String, String>): Result<Booking> = safeCall {
        httpClient.post("bookings/") {
            setBody(booking)
        }.body()
    }

    suspend fun updateBookingAction(bookingId: Int, action: String): Result<Booking> = safeCall {
        httpClient.post("bookings/$bookingId/action") {
            setBody(mapOf("action" to action))
        }.body()
    }

    // --- Diagnostic Rides ---

    suspend fun getDiagnosticRide(rideId: Int): Result<DiagnosticRide> = safeCall {
        httpClient.get("diagnostic-rides/$rideId").body()
    }

    // --- Messages ---

    suspend fun getMessages(recipientId: Int? = null): Result<List<Message>> = safeCall {
        httpClient.get("messages/") {
            recipientId?.let { parameter("recipient_id", it) }
        }.body()
    }

    suspend fun sendMessage(recipientId: Int, content: String): Result<Message> = safeCall {
        httpClient.post("messages/") {
            setBody(mapOf("recipient_id" to recipientId.toString(), "content" to content))
        }.body()
    }

    suspend fun markMessageRead(messageId: Int): Result<Message> = safeCall {
        httpClient.put("messages/$messageId/read").body()
    }

    // --- Notifications ---

    suspend fun getNotifications(): Result<List<Notification>> = safeCall {
        httpClient.get("notifications/").body()
    }

    suspend fun markNotificationRead(notificationId: Int): Result<Notification> = safeCall {
        httpClient.post("notifications/$notificationId/read").body()
    }

    // --- Instructors ---

    suspend fun getInstructors(): Result<List<User>> = safeCall {
        httpClient.get("instructors/").body()
    }

    // --- Modules ---

    suspend fun getModules(): Result<List<Module>> = safeCall {
        httpClient.get("modules/").body()
    }

    suspend fun getModuleProgress(): Result<List<ModuleProgress>> = safeCall {
        httpClient.get("modules/student-progress").body()
    }

    // --- Quiz ---

    suspend fun getQuiz(moduleId: Int): Result<Quiz> = safeCall {
        httpClient.get("modules/$moduleId/quiz").body()
    }

    suspend fun submitQuiz(moduleId: Int, data: Map<String, Int>): Result<Unit> = safeCall {
        httpClient.post("modules/$moduleId/quiz/submit") { setBody(data) }
    }

    // --- Diagnostic Rides (extended) ---

    suspend fun getDiagnosticRides(studentId: Int? = null, status: String? = null): Result<List<DiagnosticRide>> = safeCall {
        httpClient.get("diagnostic-rides/") {
            studentId?.let { parameter("student_id", it) }
            status?.let { parameter("status", it) }
        }.body()
    }

    suspend fun completeRide(data: Map<String, Any?>): Result<DiagnosticRide> = safeCall {
        httpClient.post("diagnostic-rides/") {
            contentType(ContentType.Application.Json)
            setBody(data)
        }.body()
    }

    suspend fun getProgressTrends(studentId: Int? = null): Result<ProgressTrends> = safeCall {
        httpClient.get("diagnostic-rides/progress-trends") {
            studentId?.let { parameter("student_id", it) }
        }.body()
    }

    // --- Grading ---

    suspend fun gradeSession(bookingId: Int, data: Map<String, Any>): Result<Unit> = safeCall {
        httpClient.post("bookings/$bookingId/grade") { setBody(data) }
    }

    suspend fun gradeRide(rideId: Int, data: Map<String, Any>): Result<Unit> = safeCall {
        httpClient.post("diagnostic-rides/$rideId/grade") { setBody(data) }
    }

    // --- Bookings (single) ---

    suspend fun getBooking(bookingId: Int): Result<Booking> = safeCall {
        httpClient.get("bookings/$bookingId").body()
    }

    // --- Profile ---

    suspend fun updateProfile(data: Map<String, String>): Result<User> = safeCall {
        httpClient.put("users/me") { setBody(data) }.body()
    }

    // --- Drive Logs ---

    suspend fun createDriveLog(data: Map<String, String>): Result<DriveLog> = safeCall {
        httpClient.post("drivelogs/") { setBody(data) }.body()
    }

    // --- Speed Limit ---

    suspend fun getSpeedLimit(lat: Double, lon: Double): Result<SpeedLimitResponse> = safeCall {
        httpClient.get("diagnostic-rides/speed-limit") {
            parameter("lat", lat)
            parameter("lon", lon)
        }.body()
    }

    // --- Helpers ---

    private suspend inline fun <reified T> safeCall(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
