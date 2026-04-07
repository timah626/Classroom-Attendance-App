package com.example.testapplication2.repositories

import com.example.testapplication2.models.*
import com.example.testapplication2.supabase
import com.example.testapplication2.utils.formatDate
import com.example.testapplication2.utils.formatTime
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

class SessionHistoryRepository {

    companion object {
        suspend fun fetchSessionHistory(
            classId: String,
            filterStudentId: String? = null
        ): Result<SessionHistoryData> {
            return try {
                val rawSessions = supabase.postgrest["sessions"]
                    .select(Columns.list("id", "started_at", "ended_at", "status")) {
                        filter { eq("class_id", classId) }
                    }
                    .decodeList<RawSessionRecord>()
                    .sortedByDescending { it.started_at }

                if (rawSessions.isEmpty()) {
                    return Result.success(SessionHistoryData(emptyList(), emptyList()))
                }

                val sessionIds = rawSessions.map { it.id }

                val allAttendance = supabase.postgrest["attendance"]
                    .select(Columns.list("session_id", "student_id", "status")) {
                        filter { isIn("session_id", sessionIds) }
                    }
                    .decodeList<RawAttendanceRecord>()

                val attendanceBySession = allAttendance.groupBy { it.session_id }

                val sessions = rawSessions.map { s ->
                    val records = attendanceBySession[s.id] ?: emptyList()
                    val filtered = if (filterStudentId != null)
                        records.filter { it.student_id == filterStudentId }
                    else records

                    SessionHistoryRecord(
                        sessionId = s.id,
                        classId = classId,
                        date = formatDate(s.started_at),
                        time = "${formatTime(s.started_at)} – ${s.ended_at?.let { formatTime(it) } ?: "ongoing"}",
                        present = filtered.count { it.status == "present" },
                        late = filtered.count { it.status == "late" },
                        absent = filtered.count { it.status == "absent" }
                    )
                }

                val studentIds = supabase.postgrest["enrollments"]
                    .select(Columns.list("student_id")) { filter { eq("class_id", classId) } }
                    .decodeList<EnrolledStudent>()
                    .mapNotNull { it.student_id }

                val enrolledStudents = if (studentIds.isEmpty()) emptyList() else {
                    supabase.postgrest["profiles"]
                        .select(Columns.list("id", "username")) {
                            filter { isIn("id", studentIds) }
                        }
                        .decodeList<ProfileEntry>()
                        .map { StudentFilterItem(studentId = it.id, name = it.username) }
                        .sortedBy { it.name }
                }

                Result.success(SessionHistoryData(sessions = sessions, students = enrolledStudents))
            } catch (e: Exception) {
                Result.failure(Exception("Failed to load session history: ${e.message}"))
            }
        }
    }
}
