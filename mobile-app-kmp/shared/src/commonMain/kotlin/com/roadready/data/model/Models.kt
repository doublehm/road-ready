package com.roadready.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Auth ---

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    @SerialName("full_name") val fullName: String,
    val role: String, // "student" or "instructor"
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
)

// --- User ---

@Serializable
data class User(
    val id: Int,
    val email: String,
    @SerialName("full_name") val fullName: String,
    val role: String,
    val phone: String? = null,
    @SerialName("profile_image") val profileImage: String? = null,
    @SerialName("student_profile") val studentProfile: StudentProfile? = null,
    @SerialName("instructor_profile") val instructorProfile: InstructorProfile? = null,
)

@Serializable
data class StudentProfile(
    val id: Int? = null,
    val age: Int = 0,
    @SerialName("license_number") val licenseNumber: String? = null,
    @SerialName("license_class") val licenseClass: String? = null,
    @SerialName("license_image") val licenseImage: String? = null,
    @SerialName("license_expiry") val licenseExpiry: String? = null,
    val city: String? = null,
    val province: String? = null,
)

@Serializable
data class InstructorProfile(
    val id: Int? = null,
    val city: String = "Unknown",
    val province: String? = null,
    @SerialName("license_number") val licenseNumber: String? = null,
    @SerialName("license_classes") val licenseClasses: String? = null,
    @SerialName("years_experience") val yearsExperience: Int = 0,
    @SerialName("hourly_rate") val hourlyRate: Double = 0.0,
    val bio: String? = null,
    @SerialName("car_make") val carMake: String? = null,
    @SerialName("car_model") val carModel: String? = null,
    @SerialName("car_year") val carYear: Int? = null,
    @SerialName("insurance_image") val insuranceImage: String? = null,
    @SerialName("certification_image") val certificationImage: String? = null,
    @SerialName("stripe_account_id") val stripeAccountId: String? = null,
    @SerialName("stripe_onboarded") val stripeOnboarded: Boolean = false,
    @SerialName("average_rating") val averageRating: Double? = null,
)

// --- Booking ---

@Serializable
data class Booking(
    val id: Int,
    @SerialName("student_id") val studentId: Int,
    @SerialName("instructor_id") val instructorId: Int,
    val status: String, // pending, accepted, completed, cancelled
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    @SerialName("scheduled_time") val scheduledTime: String? = null,
    val duration: Int? = null,
    @SerialName("pickup_lat") val pickupLat: Double? = null,
    @SerialName("pickup_lon") val pickupLon: Double? = null,
    @SerialName("pickup_address") val pickupAddress: String? = null,
    @SerialName("dropoff_lat") val dropoffLat: Double? = null,
    @SerialName("dropoff_lon") val dropoffLon: Double? = null,
    @SerialName("dropoff_address") val dropoffAddress: String? = null,
    @SerialName("total_price") val totalPrice: Double? = null,
    @SerialName("for_diagnostic_ride") val forDiagnosticRide: Boolean = false,
    val student: User? = null,
    val instructor: User? = null,
    @SerialName("instructor_rating") val instructorRating: Int? = null,
    @SerialName("instructor_notes") val instructorNotes: String? = null,
    @SerialName("fault_codes") val faultCodes: List<String>? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

// --- Diagnostic Ride ---

@Serializable
data class DiagnosticRide(
    val id: Int,
    @SerialName("student_id") val studentId: Int,
    @SerialName("instructor_id") val instructorId: Int? = null,
    @SerialName("supervisor_name") val supervisorName: String? = null,
    @SerialName("supervisor_type") val supervisorType: String? = null,
    val status: String, // active, completed, graded
    val score: Double? = null,
    @SerialName("overall_score") val overallScore: Double? = null,
    @SerialName("braking_score") val brakingScore: Double? = null,
    @SerialName("speed_score") val speedScore: Double? = null,
    @SerialName("cornering_score") val corneringScore: Double? = null,
    @SerialName("ride_type") val rideType: String? = null,
    @SerialName("route_data") val routeData: String? = null,
    @SerialName("sensor_data") val sensorData: String? = null,
    val events: String? = null,
    @SerialName("event_count") val eventCount: Int? = null,
    @SerialName("human_feedback") val humanFeedback: String? = null,
    @SerialName("evaluator_notes") val evaluatorNotes: String? = null,
    @SerialName("speed_data") val speedData: String? = null,
    @SerialName("speed_limit_data") val speedLimitData: String? = null,
    val duration: Int? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    val distance: Double? = null,
    @SerialName("distance_km") val distanceKm: Double? = null,
    @SerialName("max_speed_kmh") val maxSpeedKmh: Double? = null,
    @SerialName("average_speed_kmh") val averageSpeedKmh: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val student: User? = null,
    val instructor: User? = null,
)

// --- Message ---

@Serializable
data class Message(
    val id: Int,
    @SerialName("sender_id") val senderId: Int,
    @SerialName("recipient_id") val recipientId: Int,
    val content: String,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

// --- Notification ---

@Serializable
data class Notification(
    val id: Int,
    @SerialName("user_id") val userId: Int,
    val title: String,
    val message: String,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

// --- Module ---

@Serializable
data class Module(
    val id: Int,
    val title: String,
    val description: String? = null,
    @SerialName("order_index") val orderIndex: Int = 0,
    @SerialName("is_locked") val isLocked: Boolean = true,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("quiz_count") val quizCount: Int? = null,
)

@Serializable
data class ModuleProgress(
    @SerialName("module_id") val moduleId: Int,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("quiz_score") val quizScore: Double? = null,
)

// --- Quiz ---

@Serializable
data class Quiz(
    val id: Int = 0,
    @SerialName("module_id") val moduleId: Int = 0,
    val title: String = "",
    val questions: List<QuizQuestion> = emptyList(),
)

@Serializable
data class QuizQuestion(
    val id: Int = 0,
    val text: String,
    val options: List<String>,
    @SerialName("correct_index") val correctIndex: Int,
    val explanation: String? = null,
)

// --- Drive Log ---

@Serializable
data class DriveLog(
    val id: Int,
    @SerialName("student_id") val studentId: Int,
    val date: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    @SerialName("supervisor_name") val supervisorName: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
