package com.example.testapplication2

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

internal fun parseInstant(raw: String): java.time.Instant =
    java.time.Instant.parse(if (raw.endsWith("Z")) raw else "${raw}Z")

// ─────────────────────────────────────────────────────────────────────────────
// Models
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class RawSessionRecord(
    val id: String,
    val started_at: String,
    val ended_at: String? = null,
    val status: String
)

@Serializable
data class RawAttendanceRecord(
    val session_id: String,
    val student_id: String,
    val status: String
)

@Serializable
data class EnrolledStudent(
    val student_id: String?
)

data class SessionHistoryRecord(
    val sessionId: String,
    val classId: String,
    val date: String,           // "Mon, 24 Mar 2025"
    val time: String,           // "09:00 AM – 09:12 AM"
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
    val students: List<StudentFilterItem>   // for the dropdown, without "All Students"
)

// ─────────────────────────────────────────────────────────────────────────────
// fetchSessionHistory — powers SessionHistoryPage
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fetches all ended sessions for [classId], their attendance counts,
 * and the list of enrolled students for the filter dropdown.
 */
suspend fun fetchSessionHistory(
    supabase: SupabaseClient,
    classId: String,
    filterStudentId: String? = null
): Result<SessionHistoryData> {
    return try {

        // ── 1. Fetch all sessions for this class ─────────────────────────────
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

        // ── 2. Fetch all attendance records ──────────────────────────────────
        val allAttendance = supabase.postgrest["attendance"]
            .select(Columns.list("session_id", "student_id", "status")) {
                filter { isIn("session_id", sessionIds) }
            }
            .decodeList<RawAttendanceRecord>()

        val attendanceBySession = allAttendance.groupBy { it.session_id }

        // ── 3. Build session rows ────────────────────────────────────────────
        val sessions = rawSessions.map { s ->
            val records = attendanceBySession[s.id] ?: emptyList()
            val filtered = if (filterStudentId != null) {
                records.filter { it.student_id == filterStudentId }
            } else {
                records
            }

            SessionHistoryRecord(
                sessionId = s.id,
                classId   = classId,
                date      = formatDate(s.started_at),
                time      = "${formatTime(s.started_at)} – ${s.ended_at?.let { formatTime(it) } ?: "ongoing"}",
                present   = filtered.count { it.status == "present" },
                late      = filtered.count { it.status == "late" },
                absent    = filtered.count { it.status == "absent" }
            )
        }

        // ── 4. Fetch students (RESOLVE from profiles to get names) ──────────
        val studentIds = supabase.postgrest["enrollments"]
            .select(Columns.list("student_id")) {
                filter {
                    eq("class_id", classId)
                }
            }
            .decodeList<EnrolledStudent>()
            .mapNotNull { it.student_id }

        val enrolledStudents = if (studentIds.isEmpty()) {
            emptyList()
        } else {
            supabase.postgrest["profiles"]
                .select(Columns.list("id", "username")) {
                    filter { isIn("id", studentIds) }
                }
                .decodeList<ProfileEntry>()
                .map { profile -> StudentFilterItem(studentId = profile.id, name = profile.username) }
                .sortedBy { it.name }
        }

        Result.success(SessionHistoryData(sessions = sessions, students = enrolledStudents))

    } catch (e: Exception) {
        Result.failure(Exception("Failed to load session history: ${e.message}"))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

internal fun formatDate(raw: String): String {
    return try {
        val instant = parseInstant(raw)
        val zoned   = instant.atZone(java.time.ZoneId.systemDefault())
        val day     = zoned.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
        val month   = zoned.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
        "$day, ${zoned.dayOfMonth} $month ${zoned.year}"
    } catch (e: Exception) { "Unknown date" }
}

internal fun formatTime(raw: String): String {
    return try {
        val instant = parseInstant(raw)
        val zoned   = instant.atZone(java.time.ZoneId.systemDefault())
        "%02d:%02d %s".format(
            if (zoned.hour % 12 == 0) 12 else zoned.hour % 12,
            zoned.minute,
            if (zoned.hour < 12) "AM" else "PM"
        )
    } catch (e: Exception) { "--:--" }
}
