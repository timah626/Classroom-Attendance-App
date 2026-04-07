package com.example.testapplication2.repositories



import com.example.testapplication2.models.*
import com.example.testapplication2.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

class ClassRepository {

    suspend fun createClass(
        name: String,
        description: String,
        department: String,
        academicYear: String
    ) {
        val user = supabase.auth.currentUserOrNull()
            ?: error("Not logged in")

        supabase.from("classes").insert(
            mapOf(
                "teacher_id"    to user.id,
                "name"          to name,
                "description"   to description.ifBlank { null },
                "department"    to department.ifBlank { null },
                "academic_year" to academicYear
            )
        )
    }

    suspend fun fetchMyClasses(): List<ClassRecord> {
        val user = supabase.auth.currentUserOrNull()
            ?: error("Not logged in")

        return supabase.from("classes")
            .select(Columns.ALL) { filter { eq("teacher_id", user.id) } }
            .decodeList<ClassRecord>()
    }

    suspend fun fetchClassDetail(classId: String): ClassDetailBundle {
        val detail = supabase.from("classes")
            .select(Columns.ALL) { filter { eq("id", classId) } }
            .decodeSingle<ClassDetail>()

        val studentCount = supabase.from("enrollments")
            .select(Columns.ALL) { filter { eq("class_id", classId) } }
            .decodeList<EnrollmentCount>()
            .size

        val sessions = supabase.from("sessions")
            .select(Columns.ALL) { filter { eq("class_id", classId) } }
            .decodeList<SessionRecord>()

        val avgAttendance = if (sessions.isNotEmpty()) {
            val sessionIds = sessions.map { it.id }
            val attendanceRecords = supabase.from("attendance")
                .select(Columns.ALL) { filter { isIn("session_id", sessionIds) } }
                .decodeList<AttendanceRecord>()

            val presentCount = attendanceRecords.count { it.status == "present" }
            val totalExpected = sessions.size * studentCount
            if (totalExpected > 0)
                ((presentCount.toFloat() / totalExpected) * 100).toInt()
            else 0
        } else 0

        return ClassDetailBundle(
            detail        = detail,
            studentCount  = studentCount,
            sessionCount  = sessions.size,
            avgAttendance = avgAttendance
        )
    }
}

// Bundles everything fetchClassDetail needs to return in one shot
data class ClassDetailBundle(
    val detail: ClassDetail,
    val studentCount: Int,
    val sessionCount: Int,
    val avgAttendance: Int
)