package com.example.testapplication2



import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Models
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class AttendanceDetailRow(
    val student_id: String,
    val status: String,
    val checked_in_at: String? = null
)

@Serializable
data class UserNameRow(
    val id: String,
    val name: String
)

data class StudentAttendanceRecord(
    val name: String,
    val status: String,       // "present", "late", "absent"
    val checkedInAt: String?  // formatted time, null if absent
)

// ─────────────────────────────────────────────────────────────────────────────
// fetchSessionDetail
// ─────────────────────────────────────────────────────────────────────────────

suspend fun fetchSessionDetail(
    supabase: SupabaseClient,
    sessionId: String,
    classId: String
): Result<List<StudentAttendanceRecord>> {
    return try {

        // ── 1. Fetch attendance records for this session ──────────────────────
        val attendanceRecords = supabase.postgrest["attendance"]
            .select(Columns.list("student_id", "status", "checked_in_at")) {
                filter { eq("session_id", sessionId) }
            }
            .decodeList<AttendanceDetailRow>()

        // ── 2. Fetch all enrolled student IDs for this class ─────────────────
        val enrolledStudentIds = supabase.postgrest["enrollments"]
            .select(Columns.list("student_id")) {
                filter { eq("class_id", classId) }
            }
            .decodeList<EnrolledStudent>()
            .mapNotNull { it.student_id }

        if (enrolledStudentIds.isEmpty()) {
            return Result.success(emptyList())
        }

        // ── 3. Fetch names from users table ───────────────────────────────────
        val users = supabase.postgrest["users"]
            .select(Columns.list("id", "name")) {
                filter { isIn("id", enrolledStudentIds) }
            }
            .decodeList<UserNameRow>()

        val nameById = users.associate { it.id to it.name }
        val attendanceByStudentId = attendanceRecords.associateBy { it.student_id }

        // ── 4. Merge — every enrolled student gets a row ──────────────────────
        val result = enrolledStudentIds.map { studentId ->
            val attendance = attendanceByStudentId[studentId]
            val name = nameById[studentId] ?: "Unknown"
            StudentAttendanceRecord(
                name        = name,
                status      = attendance?.status ?: "absent",
                checkedInAt = attendance?.checked_in_at?.let { formatTime(it) }
            )
        }.sortedWith(
            compareBy(
                // Sort: present first, late second, absent last
                {
                    when (it.status) {
                        "present" -> 0
                        "late"    -> 1
                        else      -> 2
                    }
                },
                { it.name }
            )
        )

        Result.success(result)

    } catch (e: Exception) {
        Result.failure(Exception("Failed to load session detail: ${e.message}"))
    }
}