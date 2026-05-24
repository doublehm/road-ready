package com.roadready.data.remote

import com.roadready.createPlatformHttpClient
import com.roadready.data.model.*
import com.roadready.data.repository.ElevationResponse
import com.roadready.data.repository.NearbyHazardsResponse
import com.roadready.data.repository.SpeedLimitFlagRequest
import com.roadready.data.repository.SpeedLimitFlagResponse
import com.roadready.data.repository.SpeedLimitResponse
import com.roadready.data.repository.TripReportRequest
import com.roadready.data.repository.TripReportResponse
import com.roadready.ml.RoadConditionEvent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject

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
        httpClient.post("modules/$moduleId/quiz/submit") { setBody(data) }.body()
    }

    // --- Diagnostic Rides (extended) ---

    suspend fun getDiagnosticRides(studentId: Int? = null, status: String? = null): Result<List<DiagnosticRide>> = safeCall {
        httpClient.get("diagnostic-rides/") {
            studentId?.let { parameter("student_id", it) }
            status?.let { parameter("status", it) }
        }.body()
    }

    suspend fun completeRide(data: JsonObject): Result<DiagnosticRide> = safeCall {
        httpClient.post("diagnostic-rides/") {
            contentType(ContentType.Application.Json)
            setBody(data.toString())
        }.body()
    }

    suspend fun evaluateRide(rideId: Int): Result<Unit> = safeCall {
        httpClient.post("diagnostic-rides/$rideId/evaluate").body()
    }

    suspend fun getProgressTrends(studentId: Int? = null): Result<ProgressTrends> = safeCall {
        httpClient.get("diagnostic-rides/progress-trends") {
            studentId?.let { parameter("student_id", it) }
        }.body()
    }

    // --- Grading ---

    suspend fun gradeSession(bookingId: Int, data: Map<String, Any>): Result<Unit> = safeCall {
        httpClient.post("bookings/$bookingId/grade") { setBody(data) }.body()
    }

    suspend fun gradeRide(rideId: Int, data: Map<String, Any>): Result<Unit> = safeCall {
        httpClient.post("diagnostic-rides/$rideId/grade") { setBody(data) }.body()
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

    // --- Road Conditions ---

    suspend fun reportRoadConditions(
        rideId: String,
        events: List<RoadConditionEvent>,
    ): Result<TripReportResponse> = safeCall {
        httpClient.post("road-conditions/report") {
            setBody(TripReportRequest(rideId = rideId, events = events))
        }.body()
    }

    suspend fun getNearbyHazards(
        lat: Double,
        lon: Double,
        radiusMetres: Double = 5000.0,
    ): Result<NearbyHazardsResponse> = safeCall {
        httpClient.get("road-conditions/nearby") {
            parameter("lat", lat)
            parameter("lng", lon)
            parameter("radius_m", radiusMetres)
        }.body()
    }

    // --- Elevation ---

    suspend fun getElevation(lat: Double, lon: Double, lat2: Double? = null, lon2: Double? = null): Result<ElevationResponse> = safeCall {
        httpClient.get("diagnostic-rides/elevation") {
            parameter("lat", lat)
            parameter("lng", lon)
            lat2?.let { parameter("lat2", it) }
            lon2?.let { parameter("lng2", it) }
        }.body()
    }

    // --- Speed Limit Flags ---

    suspend fun flagSpeedLimit(request: SpeedLimitFlagRequest): Result<SpeedLimitFlagResponse> = safeCall {
        httpClient.post("speed-limits/flag") {
            setBody(request)
        }.body()
    }

    /**
     * Report a road event (construction, traffic, hazard, road condition, or
     * speed-limit-sign correction) from the active ride screen orbit buttons.
     * Posts to `/road-events/` which feeds the flagged-incidents pipeline.
     */
    suspend fun reportRoadEvent(
        category: com.roadready.ui.components.ReportCategory,
        subtypeId: String,
        severity: String,
        lat: Double,
        lon: Double,
        rideId: Int?,
    ): Result<Unit> = safeCall {
        httpClient.post("road-events/") {
            setBody(buildString {
                append("{")
                append("\"category\":\"${category.name.lowercase()}\",")
                append("\"subtype\":\"$subtypeId\",")
                append("\"severity\":\"$severity\",")
                append("\"lat\":$lat,")
                append("\"lon\":$lon")
                if (rideId != null) append(",\"ride_id\":$rideId")
                append("}")
            })
            contentType(io.ktor.http.ContentType.Application.Json)
        }.body<Unit>()
    }

    suspend fun getNearbyFlags(lat: Double, lon: Double, radiusKm: Double = 1.0): Result<List<SpeedLimitFlagResponse>> = safeCall {
        httpClient.get("speed-limits/flags/nearby") {
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("radius_km", radiusKm)
        }.body()
    }

    suspend fun setupStudentProfile(data: StudentProfileCreateRequest): Result<Unit> = safeCall {
        httpClient.put("users/me/student-profile") {
            setBody(data)
        }.body()
    }

    suspend fun setupInstructorProfile(data: InstructorProfileCreateRequest): Result<Unit> = safeCall {
        httpClient.put("users/me/instructor-profile") {
            setBody(data)
        }.body()
    }

    suspend fun getStudentProgress(): Result<StudentProgress> = safeCall {
        httpClient.get("users/me/progress").body()
    }

    // --- Document Verification & GDPR Compliance ---

    suspend fun uploadDocument(documentType: String, fileBytes: ByteArray, fileName: String): Result<Unit> = safeCall {
        httpClient.post("users/me/upload-document") {
            parameter("document_type", documentType)
            contentType(ContentType.MultiPart.FormData)
            setBody(MultiPartFormDataContent(
                formData {
                    append("file", fileBytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    })
                }
            ))
        }.body()
    }

    suspend fun exportPersonalData(): Result<String> = safeCall {
        httpClient.get("users/me/export").body()
    }

    suspend fun anonymizeProfile(): Result<Unit> = safeCall {
        httpClient.delete("users/me").body()
    }

    // --- Helpers ---

    private suspend inline fun <reified T> safeCall(block: () -> T): Result<T> {
        return try {
            val result = block()
            Result.success(result)
        } catch (e: Exception) {
            com.roadready.logError("ApiClient", "API Error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
