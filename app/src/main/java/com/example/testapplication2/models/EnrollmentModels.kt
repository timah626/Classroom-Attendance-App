package com.example.testapplication2.models


import kotlinx.serialization.Serializable

data class StudentEntry(
    val name: String,
    val email: String
)

@Serializable
data class UserRow(
    val id: String,
    val email: String
)

@Serializable
data class EnrollmentRow(
    val class_id: String,
    val student_id: String? = null,
    val pending_email: String? = null
)

@Serializable
data class ExistingEnrollment(
    val student_id: String? = null,
    val pending_email: String? = null
)

sealed class ParseResult {
    data class Success(val students: List<StudentEntry>) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

data class EnrollmentSummary(
    val enrolled: Int,      // linked to existing user
    val pending: Int,       // stored with pending_email (no account yet)
    val skipped: Int        // already enrolled, duplicate skipped
)