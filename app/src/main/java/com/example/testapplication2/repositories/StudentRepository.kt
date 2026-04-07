package com.example.testapplication2.repositories

import com.example.testapplication2.models.ActiveSessionInfo
import com.example.testapplication2.models.ClassDetails
import com.example.testapplication2.models.EnrolledClassId
import com.example.testapplication2.models.StudentClassItem
import com.example.testapplication2.models.TeacherName
import com.example.testapplication2.models.CheckInStatus
import com.example.testapplication2.models.SessionRow
import com.example.testapplication2.utils.parseSupabaseTimestamp
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import java.time.Instant
import java.time.temporal.ChronoUnit

class StudentRepository(private val supabase: SupabaseClient) {

    private val sessionRepository = SessionRepository()

    /**
     * Fetches all classes [studentId] is enrolled in, resolves teacher names,
     * and checks each class for an active session.
     *
     * Returns a list of [StudentClassItem] sorted so classes with an active
     * session appear first, then alphabetically by name.
     */
    suspend fun fetchStudentClasses(studentId: String): Result<List<StudentClassItem>> {
        return try {

            // ── Step 1: Get class IDs this student is enrolled in ─────────────────
            val enrolledClassIds = supabase.postgrest["enrollments"]
                .select(Columns.list("class_id")) {
                    filter { eq("student_id", studentId) }
                }
                .decodeList<EnrolledClassId>()
                .map { it.class_id }

            if (enrolledClassIds.isEmpty()) return Result.success(emptyList())

            // ── Step 2: Fetch class details for those IDs ─────────────────────────
            val classes = supabase.postgrest["classes"]
                .select(Columns.list("id", "name", "teacher_id")) {
                    filter { isIn("id", enrolledClassIds) }
                }
                .decodeList<ClassDetails>()

            if (classes.isEmpty()) return Result.success(emptyList())

            // ── Step 3: Fetch teacher names in one go ─────────────────────────────
            val teacherIds = classes.map { it.teacher_id }.distinct()

            val teacherMap = supabase.postgrest["users"]
                .select(Columns.list("id", "name")) {
                    filter { isIn("id", teacherIds) }
                }
                .decodeList<TeacherName>()
                .associateBy { it.id }   // map: teacher_id → TeacherName

            // ── Step 4: Fetch active sessions for all enrolled classes at once ─────
            val activeSessionsFromDb = supabase.postgrest["sessions"]
                .select(
                    Columns.list("id", "class_id", "session_code", "time_window", "started_at", "ended_at", "status")
                ) {
                    filter {
                        isIn("class_id", enrolledClassIds)
                        eq("status", "active")
                    }
                }
                .decodeList<SessionRow>()
                // Use the shared checkTimeWindow logic from SessionRepository
                // A session is "active" if it hasn't ended.
                .filter { sessionRepository.checkTimeWindow(it) != CheckInStatus.ENDED }
                .associateBy { it.class_id }

            // ── Step 5: Merge everything into StudentClassItem ────────────────────
            val result = classes.map { cls ->
                val session = activeSessionsFromDb[cls.id]
                StudentClassItem(
                    classId = cls.id,
                    className = cls.name,
                    teacherName = teacherMap[cls.teacher_id]?.name ?: "Unknown teacher",
                    activeSession = session?.let {
                        ActiveSessionInfo(
                            id = it.id,
                            class_id = it.class_id,
                            time_window = it.time_window,
                            started_at = it.started_at,
                            status = it.status
                        )
                    }
                )
            }.sortedWith(
                // Active sessions first, then alphabetically
                compareByDescending<StudentClassItem> { it.activeSession != null }
                    .thenBy { it.className }
            )

            Result.success(result)

        } catch (e: Exception) {
            Result.failure(Exception("Failed to load your classes: ${e.message}"))
        }
    }

    /**
     * Given an [ActiveSessionInfo], returns how many whole minutes are left
     * in the check-in window. Returns 0 if the window has already closed.
     * Uses the shared parseSupabaseTimestamp for consistency.
     */
    fun minutesLeftInSession(session: ActiveSessionInfo): Int {
        return try {
            val startedAt = parseSupabaseTimestamp(session.started_at)
            val windowEnd = startedAt.plusSeconds(session.time_window * 60L)
            val secondsLeft = Instant.now().until(windowEnd, ChronoUnit.SECONDS)
            if (secondsLeft > 0) (secondsLeft / 60).toInt() else 0
        } catch (e: Exception) {
            0
        }
    }
}
