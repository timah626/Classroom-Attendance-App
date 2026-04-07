package com.example.testapplication2.models

import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Enums
// ─────────────────────────────────────────────────────────────────────────────

enum class AttendanceStatus {
    PRESENT, LATE, ABSENT
}

// ─────────────────────────────────────────────────────────────────────────────
// Domain models (used by UI / screens)
// ─────────────────────────────────────────────────────────────────────────────

data class AttendanceConfirmedStats(
    val className: String,
    val markedDate: String,
    val markedTime: String,
    val attendancePercent: Int,
    val sessionsAttended: Int
)

data class AttendanceHistoryRecord(
    val date: String,
    val checkInTime: String?,
    val status: AttendanceStatus,
    val className: String
)

data class ClassAttendance(
    val className: String,
    val records: List<AttendanceHistoryRecord>
)

data class SessionStudent(
    val name: String,
    val checkInTime: String?,
    val status: AttendanceStatus
)

// ─────────────────────────────────────────────────────────────────────────────
// Supabase row / DTO models (internal to repository)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
internal data class ProfileEntry(
    val id: String,
    val username: String
)

@Serializable
internal data class AttendanceStatusRow(
    val status: String,
    val checked_in_at: String? = null
)

@Serializable
internal data class AttendanceStatusOnly(
    val status: String
)

@Serializable
internal data class ExistingAttendanceCheck(
    val id: String
)

@Serializable
internal data class NewAttendanceRecord(
    val session_id: String,
    val student_id: String,
    val status: String,
    val checked_in_at: String?
)

@Serializable
internal data class AttendanceWithSession(
    val status: String,
    val checked_in_at: String?,
    val sessions: SessionSummary
)

@Serializable
internal data class SessionSummary(
    val started_at: String,
    val class_id: String,
    val classes: ClassSummary
)

@Serializable
internal data class ClassSummary(
    val name: String
)

@Serializable
internal data class SessionIdOnly(
    val id: String
)
