package com.example.testapplication2.models



import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClassRecord(
    val id: String = "",
    @SerialName("teacher_id") val teacherId: String = "",
    val name: String = "",
    val description: String? = null,
    val department: String? = null,
    @SerialName("academic_year") val academicYear: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class ClassDetail(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val department: String? = null,
    @SerialName("academic_year") val academicYear: String = "",
    @SerialName("teacher_id") val teacherId: String = ""
)

// ── Internal DB row mappings ──────────────────────────────────────────────────

@Serializable
internal data class EnrollmentCount(
    @SerialName("class_id") val classId: String = ""
)

@Serializable
internal data class SessionRecord(
    val id: String = "",
    @SerialName("class_id") val classId: String = "",
    val status: String = ""
)

@Serializable
internal data class AttendanceRecord(
    val id: String = "",
    @SerialName("session_id") val sessionId: String = "",
    val status: String = ""
)