package com.example.testapplication2.repositories

import com.example.testapplication2.models.*
import com.example.testapplication2.utils.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import java.time.Instant

class AttendanceRepository(private val supabase: SupabaseClient) {

    // ─────────────────────────────────────────────────────────────────────────
    // Mark student as present or late
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun markAttendance(
        sessionId: String,
        studentId: String,
        session: SessionRow
    ): Result<CheckInStatus> {
        return try {

            // 1. Check for duplicate
            val existing = supabase.postgrest["attendance"]
                .select(Columns.list("id")) {
                    filter {
                        eq("session_id", sessionId)
                        eq("student_id", studentId)
                    }
                }
                .decodeList<ExistingAttendanceCheck>()

            if (existing.isNotEmpty()) {
                val recordedStatus = when (
                    supabase.postgrest["attendance"]
                        .select(Columns.list("status")) {
                            filter { eq("id", existing.first().id) }
                        }
                        .decodeSingle<AttendanceStatusOnly>().status
                ) {
                    "present" -> CheckInStatus.PRESENT
                    "late"    -> CheckInStatus.LATE
                    else      -> CheckInStatus.ENDED
                }
                return Result.success(recordedStatus)
            }

            // 2. Determine status from time window
            val checkInStatus = checkTimeWindow(session)
            if (checkInStatus == CheckInStatus.ENDED) {
                return Result.failure(Exception("This session has already ended."))
            }

            val statusString = when (checkInStatus) {
                CheckInStatus.PRESENT -> "present"
                CheckInStatus.LATE    -> "late"
                CheckInStatus.ENDED   -> "ended"
            }

            // 3. Insert the attendance record
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

    // ─────────────────────────────────────────────────────────────────────────
    // Auto-mark remaining students as absent
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun autoMarkAbsent(sessionId: String): Result<Int> {
        return try {

            // 1. Get class_id from session
            val session = supabase.postgrest["sessions"]
                .select(Columns.list("id", "class_id", "session_code", "time_window", "started_at", "ended_at", "status")) {
                    filter { eq("id", sessionId) }
                }
                .decodeSingle<SessionRow>()

            // 2. Get all enrolled student IDs (skip pending)
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

            // 3. Get students who already have a record
            val alreadyRecorded = supabase.postgrest["attendance"]
                .select(Columns.list("student_id")) {
                    filter { eq("session_id", sessionId) }
                }
                .decodeList<AttendedStudentId>()
                .map { it.student_id }
                .toSet()

            // 4. Insert absent rows for everyone else
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

    // ─────────────────────────────────────────────────────────────────────────
    // Fetch confirmed stats (for AttendanceConfirmedPage)
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchConfirmedStats(
        sessionId: String,
        studentId: String,
        classId: String,
        className: String
    ): Result<AttendanceConfirmedStats> {
        return try {

            // 1. Get the attendance record just created
            val record = supabase.postgrest["attendance"]
                .select(Columns.list("status", "checked_in_at")) {
                    filter {
                        eq("session_id", sessionId)
                        eq("student_id", studentId)
                    }
                }
                .decodeSingle<AttendanceStatusRow>()

            // 2. Format the check-in time and date
            val checkedInAt = record.checked_in_at?.let { parseInstant(it) }
                ?: Instant.now()

            val zoned = checkedInAt.atZone(java.time.ZoneId.systemDefault())

            val markedTime = "%02d:%02d %s".format(
                if (zoned.hour % 12 == 0) 12 else zoned.hour % 12,
                zoned.minute,
                if (zoned.hour < 12) "AM" else "PM"
            )

            val markedDate = "%s, %d %s %d".format(
                zoned.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH),
                zoned.dayOfMonth,
                zoned.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH),
                zoned.year
            )

            // 3. Count total sessions for this class
            val allSessionIds = supabase.postgrest["sessions"]
                .select(Columns.list("id")) {
                    filter { eq("class_id", classId) }
                }
                .decodeList<SessionIdOnly>()
                .map { it.id }

            val totalSessions = allSessionIds.size

            // 4. Count sessions this student attended (present or late)
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

            // 5. Calculate attendance rate
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

    // ─────────────────────────────────────────────────────────────────────────
    // Fetch attendance history (for StudentAttendanceHistoryPage)
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchAttendanceHistory(studentId: String): Result<List<ClassAttendance>> {
        return try {

            // 1. Fetch all attendance rows joined with session and class
            val rows = supabase.postgrest["attendance"]
                .select(
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

            // 2. Map each row to a flat AttendanceHistoryRecord
            val allRecords: List<Pair<String, AttendanceHistoryRecord>> = rows.map { row ->
                val className = row.sessions.classes.name

                val status = when (row.status) {
                    "present" -> AttendanceStatus.PRESENT
                    "late"    -> AttendanceStatus.LATE
                    else      -> AttendanceStatus.ABSENT
                }

                val date = try {
                    val instant = parseInstant(row.sessions.started_at)
                    val zoned = instant.atZone(java.time.ZoneId.systemDefault())
                    "%s, %d %s %d".format(
                        zoned.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                        zoned.dayOfMonth,
                        zoned.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                        zoned.year
                    )
                } catch (e: Exception) { "Unknown date" }

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

            // 3. Group by class name, sort records newest-first
            val grouped = allRecords
                .groupBy { it.first }
                .map { (className, pairs) ->
                    ClassAttendance(
                        className = className,
                        records   = pairs.map { it.second }.sortedByDescending { it.date }
                    )
                }
                .sortedBy { it.className }

            Result.success(grouped)

        } catch (e: Exception) {
            Result.failure(Exception("Failed to load attendance history: ${e.message}"))
        }
    }

    private fun checkTimeWindow(session: SessionRow): CheckInStatus {
        if (session.status == "ended") return CheckInStatus.ENDED
        val startedAt      = parseSupabaseTimestamp(session.started_at)
        val windowCloseAt  = startedAt.plusSeconds(session.time_window * 60L)
        val now            = Instant.now()
        return if (now.isBefore(windowCloseAt)) CheckInStatus.PRESENT else CheckInStatus.LATE
    }
}

/**
 * Top-level wrappers
 */
suspend fun markAttendance(
    supabase: SupabaseClient,
    sessionId: String,
    studentId: String,
    session: SessionRow
): Result<CheckInStatus> {
    return AttendanceRepository(supabase).markAttendance(sessionId, studentId, session)
}

suspend fun fetchConfirmedStats(
    supabase: SupabaseClient,
    sessionId: String,
    studentId: String,
    classId: String,
    className: String
): Result<AttendanceConfirmedStats> {
    return AttendanceRepository(supabase).fetchConfirmedStats(
        sessionId,
        studentId,
        classId,
        className
    )
}
