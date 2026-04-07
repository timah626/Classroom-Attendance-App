package com.example.testapplication2.models



import kotlinx.serialization.Serializable

@Serializable
data class EnrolledClassId(
    val class_id: String
)

@Serializable
data class ClassDetails(
    val id: String,
    val name: String,
    val teacher_id: String
)

@Serializable
data class TeacherName(
    val id: String,
    val name: String
)

@Serializable
data class ActiveSessionInfo(
    val id: String,
    val class_id: String,
    val time_window: Int,
    val started_at: String,
    val status: String
)

/**
 * A fully resolved class card — everything the UI needs in one object.
 */
data class StudentClassItem(
    val classId: String,
    val className: String,
    val teacherName: String,
    val activeSession: ActiveSessionInfo?   // null = no active session right now
)