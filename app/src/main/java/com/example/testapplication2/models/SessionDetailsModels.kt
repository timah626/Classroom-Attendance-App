package com.example.testapplication2.models



import kotlinx.serialization.Serializable

@Serializable
internal data class AttendanceDetailRow(
    val student_id: String,
    val status: String,
    val checked_in_at: String? = null
)

@Serializable
internal data class UserNameRow(
    val id: String,
    val name: String
)

data class StudentAttendanceRecord(
    val name: String,
    val status: String,
    val checkedInAt: String?
)