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
    @SerialName("phone_number") val phoneNumber: String,
    val role: String, // "student" or "instructor"
    
    // Additional Profile Fields (Required by Backend)
    val city: String = "Unknown",
    val province: String = "British Columbia",
    val bio: String = "New User",
    @SerialName("hourly_rate") val hourlyRate: Double = 0.0,
    @SerialName("car_make") val carMake: String = "Not Specified",
    @SerialName("car_model") val carModel: String = "Not Specified",
    @SerialName("car_year") val carYear: Int = 0,
    @SerialName("insurance_policy") val insurancePolicy: String = "PENDING",
    @SerialName("certification_id") val certificationId: String = "PENDING",
    @SerialName("license_classes") val licenseClasses: List<InstructorLicenseClass> = emptyList(),
    
    // Student specific
    val age: Int = 0,
    @SerialName("l_license_number") val licenseNumber: String = "0000000",
)

@Serializable
data class StudentProfileCreateRequest(
    val age: Int,
    @SerialName("l_license_number") val licenseNumber: String,
    @SerialName("license_class") val licenseClass: String? = null,
    val city: String? = null,
    val province: String? = null,
)

@Serializable
data class InstructorProfileCreateRequest(
    val city: String,
    val province: String,
    @SerialName("license_number") val licenseNumber: String,
    @SerialName("license_classes") val licenseClasses: List<InstructorLicenseClass>,
    @SerialName("years_experience") val yearsExperience: Int,
    @SerialName("insurance_policy") val insurancePolicy: String,
    @SerialName("certification_id") val certificationId: String,
    @SerialName("hourly_rate") val hourlyRate: Double,
    val bio: String,
    @SerialName("car_make") val carMake: String,
    @SerialName("car_model") val carModel: String,
    @SerialName("car_year") val carYear: Int,
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
    @SerialName("full_name") val fullName: String = "User",
    val role: String,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("profile_image") val profileImage: String? = null,
    @SerialName("student_profile") val studentProfile: StudentProfile? = null,
    @SerialName("instructor_profile") val instructorProfile: InstructorProfile? = null,
)

@Serializable
data class StudentProfile(
    val id: Int? = null,
    val age: Int = 0,
    @SerialName("l_license_number") val licenseNumber: String? = null,
    @SerialName("license_class") val licenseClass: String? = null,
    @SerialName("license_image") val licenseImage: String? = null,
    @SerialName("license_expiry") val licenseExpiry: String? = null,
    val city: String? = null,
    val province: String? = null,
    @SerialName("license_status") val licenseStatus: String? = "pending",
    @SerialName("rejection_reason") val rejectionReason: String? = null,
)

@Serializable
data class InstructorLicenseClass(
    val id: Int? = null,
    @SerialName("instructor_id") val instructorId: Int? = null,
    @SerialName("license_class") val licenseClass: String,
    val price: Double,
)

@Serializable
data class InstructorProfile(
    val id: Int? = null,
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("is_available") val isAvailable: Boolean = true,
    val user: User? = null,
    val city: String = "Unknown",
    val province: String? = null,
    @SerialName("license_number") val licenseNumber: String? = null,
    @SerialName("license_classes") val licenseClasses: List<InstructorLicenseClass> = emptyList(),
    @SerialName("years_experience") val yearsExperience: Int = 0,
    @SerialName("hourly_rate") val hourlyRate: Double = 0.0,
    val bio: String? = null,
    @SerialName("car_make") val carMake: String? = null,
    @SerialName("car_model") val carModel: String? = null,
    @SerialName("car_year") val carYear: Int? = null,
    @SerialName("insurance_policy") val insurancePolicy: String? = null,
    @SerialName("certification_id") val certificationId: String? = null,
    @SerialName("business_registration_number") val businessRegistrationNumber: String? = null,
    @SerialName("tax_id") val taxId: String? = null,
    @SerialName("worksafe_bc_id") val worksafeBcId: String? = null,
    @SerialName("legal_entity_name") val legalEntityName: String? = null,
    @SerialName("stripe_account_id") val stripeAccountId: String? = null,
    @SerialName("stripe_onboarding_completed") val stripeOnboardingCompleted: Boolean = false,
    @SerialName("average_rating") val averageRating: Double? = null,
    @SerialName("license_image_status") val licenseImageStatus: String? = "pending_upload",
    @SerialName("insurance_image_status") val insuranceImageStatus: String? = "pending_upload",
    @SerialName("certification_image_status") val certificationImageStatus: String? = "pending_upload",
) {
    /** Display name from the nested user, or fallback. */
    val fullName: String get() = user?.fullName ?: "Instructor"
}

// --- Booking ---

@Serializable
data class Booking(
    val id: Int,
    @SerialName("student_id") val studentId: Int,
    @SerialName("instructor_id") val instructorId: Int? = null,
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
    @SerialName("total_amount") val totalAmount: Double? = null,
    @SerialName("total_price") val totalPrice: Double? = null,
    @SerialName("for_diagnostic_ride") val forDiagnosticRide: Boolean = false,
    val student: User? = null,
    val instructor: InstructorProfile? = null,
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
    @SerialName("booking_id") val bookingId: Int? = null,
    @SerialName("ride_type") val rideType: String? = null,
    val status: String, // active, completed, evaluated
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Double? = null,
    @SerialName("distance_km") val distanceKm: Double? = null,
    @SerialName("braking_score") val brakingScore: Double? = null,
    @SerialName("speed_score") val speedScore: Double? = null,
    @SerialName("cornering_score") val corneringScore: Double? = null,
    @SerialName("smoothness_score") val smoothnessScore: Double? = null,
    @SerialName("overall_score") val overallScore: Double? = null,
    val passed: Boolean? = null,
    @SerialName("evaluation_result") val evaluationResult: String? = null,
    @SerialName("criteria_results") val criteriaResults: String? = null,
    @SerialName("evaluator_notes") val evaluatorNotes: String? = null,
    @SerialName("human_feedback") val humanFeedback: String? = null,
    @SerialName("instructor_override") val instructorOverride: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("evaluated_at") val evaluatedAt: String? = null,
    // Blob fields — present in detail, null in list
    @SerialName("route_coords") val routeCoords: String? = null,
    @SerialName("acceleration_data") val accelerationData: String? = null,
    @SerialName("rotation_data") val rotationData: String? = null,
    @SerialName("speed_data") val speedData: String? = null,
    @SerialName("speed_limit_data") val speedLimitData: String? = null,
    @SerialName("heading_data") val headingData: String? = null,
    val student: User? = null,
    val instructor: InstructorProfile? = null,
)

// --- Student Progress ---

@Serializable
data class StudentProgress(
    val id: Int,
    @SerialName("student_id") val studentId: Int,
    @SerialName("overall_score") val overallScore: Double = 0.0,
    @SerialName("total_lessons") val totalLessons: Int = 0,
    @SerialName("total_hours") val totalHours: Double = 0.0,
    @SerialName("quizzes_completed") val quizzesCompleted: Int = 0,
    @SerialName("diagnostic_ride_passed") val diagnosticRidePassed: Boolean = false,
    val status: String = "new",
    @SerialName("last_updated") val lastUpdated: String? = null,
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

// --- Progress Trends ---

@Serializable
data class ProgressTrends(
    @SerialName("total_rides") val totalRides: Int = 0,
    @SerialName("pass_rate") val passRate: Double = 0.0,
    @SerialName("average_overall") val averageOverall: Double = 0.0,
    @SerialName("best_overall") val bestOverall: Double = 0.0,
    @SerialName("recent_score") val recentScore: Double = 0.0,
    @SerialName("avg_duration_minutes") val avgDurationMinutes: Double = 0.0,
    @SerialName("category_averages") val categoryAverages: CategoryAverages? = null,
    @SerialName("improvement_areas") val improvementAreas: List<String> = emptyList(),
    @SerialName("total_distance_km") val totalDistanceKm: Double = 0.0,
    @SerialName("total_time_hours") val totalTimeHours: Double = 0.0,
)

@Serializable
data class CategoryAverages(
    val braking: Double = 0.0,
    val speed: Double = 0.0,
    val cornering: Double = 0.0,
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
