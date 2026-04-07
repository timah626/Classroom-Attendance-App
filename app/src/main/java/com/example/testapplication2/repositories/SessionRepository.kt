package com.example.testapplication2.repositories



import com.example.testapplication2.models.*
import com.example.testapplication2.supabase
import com.example.testapplication2.utils.parseSupabaseTimestamp
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class SessionRepository {

    private fun generateSessionCode(classId: String): String {
        val stripped = classId.replace("-", "")
        val raw = "$stripped${System.currentTimeMillis()}"
        val hash = raw.hashCode().toLong().and(0x00FFFFFF)
        return hash.toString(36).uppercase().padStart(6, '0').takeLast(6)
    }

    private fun nowIso(): String = Instant.now().toString()

    suspend fun startSession(classId: String, timeWindow: Int): Result<SessionRow> {
        return try {
            val sessionCode = generateSessionCode(classId)
            val created = supabase.postgrest["sessions"]
                .insert(
                    NewSession(
                        class_id     = classId,
                        session_code = sessionCode,
                        time_window  = timeWindow,
                        started_at   = nowIso(),
                        ended_at     = null,
                        status       = "active"
                    )
                ) { select() }
                .decodeSingle<SessionRow>()
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to start session: ${e.message}"))
        }
    }

    suspend fun endSession(sessionId: String): Result<Int> {
        if (sessionId.isBlank() || sessionId == "null") return Result.failure(Exception("Invalid session ID"))
        return try {
            val session = supabase.postgrest["sessions"]
                .select { filter { eq("id", sessionId) } }
                .decodeSingle<SessionRow>()

            supabase.postgrest["sessions"]
                .update(buildJsonObject {
                    put("status", "ended")
                    put("ended_at", nowIso())
                }) { filter { eq("id", sessionId) } }

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
                    session_id    = sessionId,
                    student_id    = studentId,
                    status        = "absent",
                    checked_in_at = null
                )
            }
            supabase.postgrest["attendance"].insert(absentRecords)
            Result.success(absentStudentIds.size)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to end session: ${e.message}"))
        }
    }

    suspend fun getActiveSession(classId: String): Result<SessionRow?> {
        return try {
            val sessions = supabase.postgrest["sessions"]
                .select { filter { eq("class_id", classId); eq("status", "active") } }
                .decodeList<SessionRow>()
            Result.success(sessions.firstOrNull())
        } catch (e: Exception) {
            Result.failure(Exception("Failed to fetch active session: ${e.message}"))
        }
    }

    fun checkTimeWindow(session: SessionRow): CheckInStatus {
        if (session.status == "ended") return CheckInStatus.ENDED
        val startedAt      = parseSupabaseTimestamp(session.started_at)
        val windowCloseAt  = startedAt.plusSeconds(session.time_window * 60L)
        val now            = Instant.now()
        return if (now.isBefore(windowCloseAt)) CheckInStatus.PRESENT else CheckInStatus.LATE
    }
}

/**
 * Top-level wrapper for TeacherActiveSessionPage
 */
suspend fun endSession(supabase: SupabaseClient, sessionId: String): Result<Int> {
    // Note: SessionRepository currently uses a global 'supabase' object, 
    // but we'll use the passed client if we were to refactor it.
    // For now, keeping it consistent with the existing repository usage.
    return SessionRepository().endSession(sessionId)
}
