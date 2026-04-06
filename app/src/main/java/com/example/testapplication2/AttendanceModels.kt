package com.example.testapplication2

import kotlinx.serialization.Serializable

enum class AttendanceStatus {
    PRESENT, LATE, ABSENT
}

data class SessionStudent(
    val name: String,
    val checkInTime: String?,   // null means not yet checked in
    val status: AttendanceStatus
)

@Serializable
data class ProfileEntry(
    val id: String,
    val username: String
)
