package com.example.testapplication2

import com.example.testapplication2.models.AttendanceDetailRow
import com.example.testapplication2.models.EnrolledStudent
import com.example.testapplication2.models.StudentAttendanceRecord
import com.example.testapplication2.models.UserNameRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import com.example.testapplication2.utils.formatTime

suspend fun fetchSessionDetail(
    supabase: SupabaseClient,
    sessionId: String,
    classId: String
): Result<List<StudentAttendanceRecord>> {
    return try {
        val attendanceRecords = supabase.postgrest["attendance"]
            .select(Columns.list("student_id", "status", "checked_in_at")) {
                filter { eq("session_id", sessionId) }
            }
            .decodeList<AttendanceDetailRow>()

        val enrolledStudentIds = supabase.postgrest["enrollments"]
            .select(Columns.list("student_id")) {
                filter { eq("class_id", classId) }
            }
            .decodeList<EnrolledStudent>()
            .mapNotNull { it.student_id }

        if (enrolledStudentIds.isEmpty()) return Result.success(emptyList())

        val users = supabase.postgrest["users"]
            .select(Columns.list("id", "name")) {
                filter { isIn("id", enrolledStudentIds) }
            }
            .decodeList<UserNameRow>()

        val nameById = users.associate { it.id to it.name }
        val attendanceByStudentId = attendanceRecords.associateBy { it.student_id }

        val result = enrolledStudentIds.map { studentId ->
            val attendance = attendanceByStudentId[studentId]
            StudentAttendanceRecord(
                name        = nameById[studentId] ?: "Unknown",
                status      = attendance?.status ?: "absent",
                checkedInAt = attendance?.checked_in_at?.let { formatTime(it) }
            )
        }.sortedWith(compareBy(
            { when (it.status) { "present" -> 0; "late" -> 1; else -> 2 } },
            { it.name }
        ))

        Result.success(result)
    } catch (e: Exception) {
        Result.failure(Exception("Failed to load session detail: ${e.message}"))
    }
}