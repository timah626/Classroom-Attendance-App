package com.example.testapplication2



import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Model
// ─────────────────────────────────────────────────────────────────────────────

data class AttendanceConfirmedStats(
    val className: String,
    val markedDate: String,       // e.g. "Monday, 24 March 2025"
    val markedTime: String,       // e.g. "09:03 AM"
    val attendancePercent: Int,   // e.g. 87
    val sessionsAttended: Int     // e.g. 13
)

@Serializable
private data class AttendanceStatusRow(
    val status: String,
    val checked_in_at: String? = null
)
/*
@Serializable
private data class EnrollmentCount(
    val class_id: String
)
*/
// ─────────────────────────────────────────────────────────────────────────────
// fetchConfirmedStats
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fetches everything [AttendanceConfirmedPage] needs after a student checks in.
 *
 * @param sessionId   The session the student just checked into
 * @param studentId   The logged-in student
 * @param classId     The class this session belongs to
 * @param className   Already known from nav args — passed through to avoid extra query
 */
suspend fun fetchConfirmedStats(
    supabase: SupabaseClient,
    sessionId: String,
    studentId: String,
    classId: String,
    className: String
): Result<AttendanceConfirmedStats> {
    return try {

        // ── 1. Get the attendance record just created ─────────────────────────
        val record = supabase.postgrest["attendance"]
            .select(Columns.list("status", "checked_in_at")) {
                filter {
                    eq("session_id", sessionId)
                    eq("student_id", studentId)
                }
            }
            .decodeSingle<AttendanceStatusRow>()

        // ── 2. Format the check-in time and date ──────────────────────────────
        val checkedInAt = record.checked_in_at?.let { parseInstant(it) }
            ?: java.time.Instant.now()

        val zoned = checkedInAt.atZone(java.time.ZoneId.systemDefault())

        val markedTime = "%02d:%02d %s".format(
            if (zoned.hour % 12 == 0) 12 else zoned.hour % 12,
            zoned.minute,
            if (zoned.hour < 12) "AM" else "PM"
        )

        val dayName = zoned.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH
        )
        val monthName = zoned.month.getDisplayName(
            java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH
        )
        val markedDate = "$dayName, ${zoned.dayOfMonth} $monthName ${zoned.year}"

        // ── 3. Count total sessions for this class ────────────────────────────
        val allSessionIds = supabase.postgrest["sessions"]
            .select(Columns.list("id")) {
                filter { eq("class_id", classId) }
            }
            .decodeList<SessionIdOnly>()
            .map { it.id }

        val totalSessions = allSessionIds.size

        // ── 4. Count sessions this student attended (present or late) ─────────
        val attended = if (allSessionIds.isEmpty()) 0 else {
            supabase.postgrest["attendance"]
                .select(Columns.list("status")) {
                    filter {
                        eq("student_id", studentId)
                        isIn("session_id", allSessionIds)
                        neq("status", "absent")
                    }
                }
                .decodeList<AttendanceStatusRow>()
                .size
        }

        // ── 5. Calculate attendance rate ──────────────────────────────────────
        val percent = if (totalSessions > 0) {
            ((attended.toFloat() / totalSessions) * 100).toInt()
        } else 0

        Result.success(
            AttendanceConfirmedStats(
                className         = className,
                markedDate        = markedDate,
                markedTime        = markedTime,
                attendancePercent = percent,
                sessionsAttended  = attended
            )
        )

    } catch (e: Exception) {
        Result.failure(Exception("Failed to load confirmation stats: ${e.message}"))
    }
}

@Serializable
private data class SessionIdOnly(val id: String)
