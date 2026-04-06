package com.example.testapplication2

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.OffsetDateTime

// ─────────────────────────────────────────────────────────────────────────────
// Models
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class SessionRow(
    val id: String,
    val class_id: String,
    val session_code: String,
    val time_window: Int,
    val started_at: String,
    val ended_at: String? = null,
    val status: String
)

@Serializable
data class NewSession(
    val class_id: String,
    val session_code: String,
    val time_window: Int,
    val started_at: String,
    val ended_at: String? = null,
    val status: String = "active"
)

@Serializable
data class EnrolledStudentId(
    val student_id: String?   // nullable because of pending enrollments
)

@Serializable
data class AttendedStudentId(
    val student_id: String
)

@Serializable
data class AbsentRecord(
    val session_id: String,
    val student_id: String,
    val status: String,
    val checked_in_at: String? = null
)

// Attendance status returned by Function 4
enum class CheckInStatus { PRESENT, LATE, ENDED }

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Resiliently parses a timestamp string from Supabase.
 */
internal fun parseSupabaseTimestamp(ts: String): Instant {
    var cleaned = ts.trim()
    
    // 1. Fix non-standard colon in fractional seconds: e.g. T02:32:28:091822 -> T02:32:28.091822
    val tIndex = cleaned.indexOf('T')
    if (tIndex != -1) {
        val colons = mutableListOf<Int>()
        for (i in tIndex until cleaned.length) {
            if (cleaned[i] == ':') colons.add(i)
        }
        if (colons.size >= 3) {
            val lastColon = colons.last()
            cleaned = cleaned.substring(0, lastColon) + "." + cleaned.substring(lastColon + 1)
        }
    }

    // 2. If it has an offset like +00:00 or -05:00, parse as OffsetDateTime
    if (cleaned.contains('+') || (cleaned.lastIndexOf('-') > 10)) {
        return try {
            OffsetDateTime.parse(cleaned).toInstant()
        } catch (e: Exception) {
            // Strip offset and fallback
            cleaned = cleaned.replace(Regex("[+-]\\d{2}:\\d{2}$"), "")
            if (!cleaned.endsWith("Z")) cleaned += "Z"
            Instant.parse(cleaned)
        }
    }

    // 3. Ensure it ends with Z for Instant.parse if no offset is present
    if (!cleaned.endsWith("Z")) {
        cleaned += "Z"
    }
    
    return Instant.parse(cleaned)
}

/**
 * Generates a short unique session code by combining the class_id and
 * the current timestamp.
 */
private fun generateSessionCode(classId: String): String {
    val stripped = classId.replace("-", "")
    val raw = "$stripped${System.currentTimeMillis()}"
    val hash = raw.hashCode().toLong().and(0x00FFFFFF)
    return hash.toString(36).uppercase().padStart(6, '0').takeLast(6)
}

/**
 * Returns current UTC time as an ISO-8601 string.
 */
private fun nowIso(): String {
    return Instant.now().toString()
}

// ─────────────────────────────────────────────────────────────────────────────
// Functions
// ─────────────────────────────────────────────────────────────────────────────

suspend fun startSession(
    supabase: SupabaseClient,
    classId: String,
    timeWindow: Int
): Result<SessionRow> {
    return try {
        val sessionCode = generateSessionCode(classId)
        val created = supabase.postgrest["sessions"]
            .insert(
                NewSession(
                    class_id = classId,
                    session_code = sessionCode,
                    time_window = timeWindow,
                    started_at = nowIso(),
                    ended_at = null,
                    status = "active"
                )
            ) { select() }
            .decodeSingle<SessionRow>()
        Result.success(created)
    } catch (e: Exception) {
        Result.failure(Exception("Failed to start session: ${e.message}"))
    }
}

suspend fun endSession(
    supabase: SupabaseClient,
    sessionId: String
): Result<Int> {
    if (sessionId.isBlank() || sessionId == "null") return Result.failure(Exception("Invalid session ID"))

    return try {
        val session = supabase.postgrest["sessions"]
            .select { filter { eq("id", sessionId) } }
            .decodeSingle<SessionRow>()

        supabase.postgrest["sessions"]
            .update(
                buildJsonObject {
                    put("status", "ended")
                    put("ended_at", nowIso())
                }
            ) { filter { eq("id", sessionId) } }

        val allEnrolled = supabase.postgrest["enrollments"]
            .select(Columns.list("student_id")) { filter { eq("class_id", session.class_id) } }
            .decodeList<EnrolledStudentId>()
            .mapNotNull { it.student_id }
            .filter { it != "null" && it.isNotBlank() }

        if (allEnrolled.isEmpty()) return Result.success(0)

        val alreadyAttended = supabase.postgrest["attendance"]
            .select(Columns.list("student_id")) { filter { eq("session_id", sessionId) } }
            .decodeList<AttendedStudentId>()
            .map { it.student_id }
            .toSet()

        val absentStudentIds = allEnrolled.filter { it !in alreadyAttended }
        if (absentStudentIds.isEmpty()) return Result.success(0)

        val absentRecords = absentStudentIds.map { studentId ->
            AbsentRecord(
                session_id = sessionId,
                student_id = studentId,
                status = "absent",
                checked_in_at = null
            )
        }

        supabase.postgrest["attendance"].insert(absentRecords)
        Result.success(absentStudentIds.size)
    } catch (e: Exception) {
        Result.failure(Exception("Failed to end session: ${e.message}"))
    }
}

suspend fun getActiveSession(
    supabase: SupabaseClient,
    classId: String
): Result<SessionRow?> {
    return try {
        val sessions = supabase.postgrest["sessions"]
            .select {
                filter {
                    eq("class_id", classId)
                    eq("status", "active")
                }
            }
            .decodeList<SessionRow>()
        Result.success(sessions.firstOrNull())
    } catch (e: Exception) {
        Result.failure(Exception("Failed to fetch active session: ${e.message}"))
    }
}

fun checkTimeWindow(session: SessionRow): CheckInStatus {
    if (session.status == "ended") return CheckInStatus.ENDED
    val startedAt = parseSupabaseTimestamp(session.started_at)
    val windowCloseAt = startedAt.plusSeconds(session.time_window * 60L)
    val now = Instant.now()
    return if (now.isBefore(windowCloseAt)) CheckInStatus.PRESENT else CheckInStatus.LATE
}
