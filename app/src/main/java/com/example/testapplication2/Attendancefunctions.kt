package com.example.testapplication2

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import java.time.Instant

// ─────────────────────────────────────────────────────────────────────────────
// Models
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class ExistingAttendanceCheck(
    val id: String
)

@Serializable
data class NewAttendanceRecord(
    val session_id: String,
    val student_id: String,
    val status: String,
    val checked_in_at: String?
)

@Serializable
data class AttendanceWithSession(
    val status: String,
    val checked_in_at: String?,
    val sessions: SessionSummary
)

@Serializable
data class SessionSummary(
    val started_at: String,
    val class_id: String,
    val classes: ClassSummary
)

@Serializable
data class ClassSummary(
    val name: String
)

/**
 * One resolved record for the attendance history screen.
 */
data class AttendanceHistoryRecord(
    val date: String,
    val checkInTime: String?,
    val status: AttendanceStatus,
    val className: String
)

data class ClassAttendance(
    val className: String,
    val records: List<AttendanceHistoryRecord>
)

// ─────────────────────────────────────────────────────────────────────────────
// Function 4 — Mark student as present or late
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Marks [studentId]'s attendance for [sessionId].
 *
 * - Checks for a duplicate record first — if one exists, does nothing.
 * - Calls [checkTimeWindow] to decide between PRESENT or LATE.
 * - Inserts the attendance row with the correct status and timestamp.
 *
 * @return [Result.success] with the final [CheckInStatus] that was recorded,
 *         or [Result.failure] on error.
 */
suspend fun markAttendance(
    supabase: SupabaseClient,
    sessionId: String,
    studentId: String,
    session: SessionRow          // needed by checkTimeWindow — fetch before calling this
): Result<CheckInStatus> {
    return try {

        // ── 1. Check for duplicate ────────────────────────────────────────────
        val existing = supabase.postgrest["attendance"]
            .select(Columns.list("id")) {
                filter {
                    eq("session_id", sessionId)
                    eq("student_id", studentId)
                }
            }
            .decodeList<ExistingAttendanceCheck>()

        if (existing.isNotEmpty()) {
            // Already marked — return what was recorded without touching the DB
            val recordedStatus = when (existing.firstOrNull()?.let {
                supabase.postgrest["attendance"]
                    .select(Columns.list("status")) {
                        filter { eq("id", it.id) }
                    }
                    .decodeSingle<AttendanceStatusOnly>().status
            }) {
                "present" -> CheckInStatus.PRESENT
                "late"    -> CheckInStatus.LATE
                else      -> CheckInStatus.ENDED
            }
            return Result.success(recordedStatus)
        }

        // ── 2. Determine status from time window ──────────────────────────────
        val checkInStatus = checkTimeWindow(session)

        if (checkInStatus == CheckInStatus.ENDED) {
            return Result.failure(Exception("This session has already ended."))
        }

        val statusString = when (checkInStatus) {
            CheckInStatus.PRESENT -> "present"
            CheckInStatus.LATE    -> "late"
            CheckInStatus.ENDED   -> "ended"  // unreachable — handled above
        }

        // ── 3. Insert the attendance record ───────────────────────────────────
        supabase.postgrest["attendance"].insert(
            NewAttendanceRecord(
                session_id    = sessionId,
                student_id    = studentId,
                status        = statusString,
                checked_in_at = Instant.now().toString()
            )
        )

        Result.success(checkInStatus)

    } catch (e: Exception) {
        Result.failure(Exception("Failed to mark attendance: ${e.message}"))
    }
}

// small helper model used inside markAttendance
@Serializable
private data class AttendanceStatusOnly(val status: String)

// ─────────────────────────────────────────────────────────────────────────────
// Function 5 — Auto-mark remaining students as absent
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Marks all enrolled students who have no attendance record for [sessionId]
 * as 'absent'. This is the standalone version — note that [endSession] in
 * SessionFunctions.kt already calls this logic internally. Only use this
 * function if you need to trigger the absent sweep independently.
 *
 * Pending enrollments (null student_id) are skipped — no FK to insert.
 *
 * @return [Result.success] with the number of students marked absent
 */
suspend fun autoMarkAbsent(
    supabase: SupabaseClient,
    sessionId: String
): Result<Int> {
    return try {

        // ── 1. Get class_id from session ──────────────────────────────────────
        val session = supabase.postgrest["sessions"]
            .select(Columns.list("id", "class_id", "session_code", "time_window", "started_at", "ended_at", "status")) {
                filter { eq("id", sessionId) }
            }
            .decodeSingle<SessionRow>()

        // ── 2. Get all enrolled student IDs (skip pending) ────────────────────
        val allEnrolled = supabase.postgrest["enrollments"]
            .select(Columns.list("student_id")) {
                filter {
                    eq("class_id", session.class_id)
                    neq("student_id", "null")
                }
            }
            .decodeList<EnrolledStudentId>()
            .mapNotNull { it.student_id }

        if (allEnrolled.isEmpty()) return Result.success(0)

        // ── 3. Get students who already have a record ─────────────────────────
        val alreadyRecorded = supabase.postgrest["attendance"]
            .select(Columns.list("student_id")) {
                filter { eq("session_id", sessionId) }
            }
            .decodeList<AttendedStudentId>()
            .map { it.student_id }
            .toSet()

        // ── 4. Insert absent rows for everyone else ───────────────────────────
        val absentIds = allEnrolled.filter { it !in alreadyRecorded }
        if (absentIds.isEmpty()) return Result.success(0)

        val absentRecords = absentIds.map { studentId ->
            AbsentRecord(
                session_id    = sessionId,
                student_id    = studentId,
                status        = "absent",
                checked_in_at = null
            )
        }

        supabase.postgrest["attendance"].insert(absentRecords)
        Result.success(absentIds.size)

    } catch (e: Exception) {
        Result.failure(Exception("Failed to auto-mark absent: ${e.message}"))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// fetchAttendanceHistory — powers StudentAttendanceHistoryPage
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fetches all attendance records for [studentId], joined with session and
 * class info, and returns them grouped by class name.
 *
 * Returns a list of [ClassAttendance] sorted alphabetically by class name,
 * with each class's records sorted newest-first.
 */
suspend fun fetchAttendanceHistory(
    supabase: SupabaseClient,
    studentId: String
): Result<List<ClassAttendance>> {
    return try {

        // ── 1. Fetch all attendance rows for this student ─────────────────────
        // We use a join: attendance → sessions → classes
        val rows = supabase.postgrest["attendance"]
            .select(
                // Supabase nested select syntax
                Columns.raw("""
                    status,
                    checked_in_at,
                    sessions (
                        started_at,
                        class_id,
                        classes (
                            name
                        )
                    )
                """.trimIndent())
            ) {
                filter { eq("student_id", studentId) }
            }
            .decodeList<AttendanceWithSession>()

        // ── 2. Map each row to a flat AttendanceHistoryRecord ─────────────────
        val allRecords: List<Pair<String, AttendanceHistoryRecord>> = rows.map { row ->
            val className = row.sessions.classes.name

            val status = when (row.status) {
                "present" -> AttendanceStatus.PRESENT
                "late"    -> AttendanceStatus.LATE
                else      -> AttendanceStatus.ABSENT
            }

            // Format date: "Mon, 24 Mar 2025"
            val date = try {
                val instant = parseInstant(row.sessions.started_at)
                val zoned = instant.atZone(java.time.ZoneId.systemDefault())
                val dayName = zoned.dayOfWeek.getDisplayName(
                    java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH
                )
                val monthName = zoned.month.getDisplayName(
                    java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH
                )
                "$dayName, ${zoned.dayOfMonth} $monthName ${zoned.year}"
            } catch (e: Exception) { "Unknown date" }

            // Format check-in time: "09:01 AM"
            val checkInTime = row.checked_in_at?.let { iso ->
                try {
                    val instant = parseInstant(iso)
                    val zoned = instant.atZone(java.time.ZoneId.systemDefault())
                    "%02d:%02d %s".format(
                        if (zoned.hour % 12 == 0) 12 else zoned.hour % 12,
                        zoned.minute,
                        if (zoned.hour < 12) "AM" else "PM"
                    )
                } catch (e: Exception) { null }
            }

            className to AttendanceHistoryRecord(
                date        = date,
                checkInTime = checkInTime,
                status      = status,
                className   = className
            )
        }

        // ── 3. Group by class name, sort records newest-first ─────────────────
        val grouped = allRecords
            .groupBy { it.first }
            .map { (className, pairs) ->
                ClassAttendance(
                    className = className,
                    records = pairs
                        .map { it.second }
                        .sortedByDescending { it.date }
                )
            }
            .sortedBy { it.className }

        Result.success(grouped)

    } catch (e: Exception) {
        Result.failure(Exception("Failed to load attendance history: ${e.message}"))
    }
}
