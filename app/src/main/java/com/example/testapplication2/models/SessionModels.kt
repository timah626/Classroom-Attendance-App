package com.example.testapplication2.models



import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class SessionRow(
    val id: String,
    val class_id: String,
    val session_code: String,
    val time_window: Int,
    val started_at: String,
    val ended_at: String? = null,
    val status: String
)

@Serializable
internal data class NewSession(
    val class_id: String,
    val session_code: String,
    val time_window: Int,
    val started_at: String,
    val ended_at: String? = null,
    val status: String = "active"
)

@Serializable
internal data class EnrolledStudentId(
    val student_id: String?
)

@Serializable
internal data class AttendedStudentId(
    val student_id: String
)

@Serializable
internal data class AbsentRecord(
    val session_id: String,
    val student_id: String,
    val status: String,
    val checked_in_at: String? = null
)

@Serializable
internal data class EnrolledStudent(
    val student_id: String?
)

enum class CheckInStatus { PRESENT, LATE, ENDED }