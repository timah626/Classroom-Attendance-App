package com.example.testapplication2.models

import kotlinx.serialization.Serializable

@Serializable
internal data class RawSessionRecord(
    val id: String,
    val started_at: String,
    val ended_at: String? = null,
    val status: String
)

@Serializable
internal data class RawAttendanceRecord(
    val session_id: String,
    val student_id: String,
    val status: String
)

data class SessionHistoryRecord(
    val sessionId: String,
    val classId: String,
    val date: String,
    val time: String,
    val present: Int,
    val late: Int,
    val absent: Int
)

data class StudentFilterItem(
    val studentId: String,
    val name: String
)

data class SessionHistoryData(
    val sessions: List<SessionHistoryRecord>,
    val students: List<StudentFilterItem>
)
