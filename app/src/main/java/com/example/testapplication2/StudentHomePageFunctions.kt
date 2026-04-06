package com.example.testapplication2

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Models
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class EnrolledClassId(
    val class_id: String
)

@Serializable
data class ClassDetails(
    val id: String,
    val name: String,
    val teacher_id: String
)

@Serializable
data class TeacherName(
    val id: String,
    val name: String
)

@Serializable
data class ActiveSessionInfo(
    val id: String,
    val class_id: String,
    val time_window: Int,
    val started_at: String,
    val status: String
)

/**
 * A fully resolved class card — everything the UI needs in one object.
 */
data class StudentClassItem(
    val classId: String,
    val className: String,
    val teacherName: String,
    val activeSession: ActiveSessionInfo?   // null = no active session right now
)

// ─────────────────────────────────────────────────────────────────────────────
// fetchStudentClasses — the single function powering StudentHomePage
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fetches all classes [studentId] is enrolled in, resolves teacher names,
 * and checks each class for an active session.
 *
 * Returns a list of [StudentClassItem] sorted so classes with an active
 * session appear first, then alphabetically by name.
 */
suspend fun fetchStudentClasses(
    supabase: SupabaseClient,
    studentId: String
): Result<List<StudentClassItem>> {
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
        val activeSessions = supabase.postgrest["sessions"]
            .select(
                Columns.list("id", "class_id", "time_window", "started_at", "status")
            ) {
                filter {
                    isIn("class_id", enrolledClassIds)
                    eq("status", "active")
                }
            }
            .decodeList<ActiveSessionInfo>()
            // Safeguard: ignore sessions whose time window has already elapsed.
            // This handles stale 'active' rows left over from crashes or missed endSession() calls.
            .filter { minutesLeftInSession(it) > 0 }
            .associateBy { it.class_id }   // map: class_id → ActiveSessionInfo

        // ── Step 5: Merge everything into StudentClassItem ────────────────────
        val result = classes.map { cls ->
            StudentClassItem(
                classId = cls.id,
                className = cls.name,
                teacherName = teacherMap[cls.teacher_id]?.name ?: "Unknown teacher",
                activeSession = activeSessions[cls.id]
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

// ─────────────────────────────────────────────────────────────────────────────
// minutesLeft — helper used by the UI to display the countdown on active cards
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Given an [ActiveSessionInfo], returns how many whole minutes are left
 * in the check-in window. Returns 0 if the window has already closed.
 */
fun minutesLeftInSession(session: ActiveSessionInfo): Int {
    return try {
        val startedAt = parseInstant(session.started_at)
        val windowEnd = startedAt.plusSeconds(session.time_window * 60L)
        val secondsLeft = java.time.Instant.now()
            .until(windowEnd, java.time.temporal.ChronoUnit.SECONDS)
        if (secondsLeft > 0) (secondsLeft / 60).toInt() else 0
    } catch (e: Exception) {
        0
    }
}
