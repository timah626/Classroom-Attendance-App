package com.example.testapplication2.studentUI
import com.example.testapplication2.AppBar




import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── Raw models — decode everything as strings so nothing fails silently ───────

@Serializable
data class RawSession(
    val id: String,
    val class_id: String,
    val session_code: String,
    val time_window: Int,
    val started_at: String,
    val ended_at: String? = null,
    val status: String
)

@Serializable
data class RawEnrollment(
    val id: String,
    val class_id: String,
    val student_id: String? = null,
    val pending_email: String? = null
)

@Serializable
data class RawUser(
    val id: String,
    val name: String,
    val email: String,
    val role: String
)

// ─────────────────────────────────────────────────────────────────────────────
// Debug screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DebugScreen(
    navController: NavController,
    supabase: SupabaseClient,
    studentId: String    // the logged-in student's id
) {
    var output  by remember { mutableStateOf("Fetching...") }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sb = StringBuilder()

        try {
            // ── 1. Who is the logged-in student? ─────────────────────────────
            sb.appendLine("═══ LOGGED IN STUDENT ═══")
            sb.appendLine("student_id: $studentId")
            sb.appendLine()

            val user = supabase.postgrest["users"]
                .select(Columns.list("id", "name", "email", "role")) {
                    filter { eq("id", studentId) }
                }
                .decodeList<RawUser>()

            if (user.isEmpty()) {
                sb.appendLine("⚠️ No user found with this id!")
            } else {
                user.forEach {
                    sb.appendLine("name:  ${it.name}")
                    sb.appendLine("email: ${it.email}")
                    sb.appendLine("role:  ${it.role}")
                }
            }

            sb.appendLine()

            // ── 2. What classes is this student enrolled in? ──────────────────
            sb.appendLine("═══ ENROLLMENTS ═══")
            val enrollments = supabase.postgrest["enrollments"]
                .select(Columns.list("id", "class_id", "student_id", "pending_email")) {
                    filter { eq("student_id", studentId) }
                }
                .decodeList<RawEnrollment>()

            if (enrollments.isEmpty()) {
                sb.appendLine("⚠️ No enrollments found for this student!")
                sb.appendLine("    This is likely the problem.")
            } else {
                sb.appendLine("Found ${enrollments.size} enrollment(s):")
                enrollments.forEach {
                    sb.appendLine("  class_id: ${it.class_id}")
                }
            }

            sb.appendLine()

            // ── 3. What sessions exist in the DB right now? ───────────────────
            sb.appendLine("═══ ALL SESSIONS IN DB ═══")
            val allSessions = supabase.postgrest["sessions"]
                .select(
                    Columns.list(
                        "id", "class_id", "session_code",
                        "time_window", "started_at", "ended_at", "status"
                    )
                )
                .decodeList<RawSession>()

            if (allSessions.isEmpty()) {
                sb.appendLine("⚠️ Sessions table is empty!")
                sb.appendLine("    No session was saved when you tapped Start.")
            } else {
                allSessions.forEach { s ->
                    sb.appendLine("---")
                    sb.appendLine("  id:           ${s.id}")
                    sb.appendLine("  class_id:     ${s.class_id}")
                    sb.appendLine("  status:       ${s.status}")
                    sb.appendLine("  time_window:  ${s.time_window} min")
                    sb.appendLine("  started_at:   ${s.started_at}")
                    sb.appendLine("  ended_at:     ${s.ended_at ?: "null (still active)"}")
                    sb.appendLine("  session_code: ${s.session_code}")
                }
            }

            sb.appendLine()

            // ── 4. Cross-check: do any session class_ids match enrollment class_ids?
            sb.appendLine("═══ CROSS-CHECK ═══")
            val enrolledClassIds = enrollments.map { it.class_id }.toSet()
            val activeSessions   = allSessions.filter { it.status == "active" }

            if (activeSessions.isEmpty()) {
                sb.appendLine("⚠️ No sessions with status='active' found.")
                sb.appendLine("    Check if startSession() is saving to Supabase.")
            } else {
                val matchingSessions = activeSessions.filter { it.class_id in enrolledClassIds }
                if (matchingSessions.isEmpty()) {
                    sb.appendLine("⚠️ Active session exists BUT class_id doesn't")
                    sb.appendLine("    match any of the student's enrollments!")
                    sb.appendLine()
                    sb.appendLine("  Active session class_id(s):")
                    activeSessions.forEach { sb.appendLine("    ${it.class_id}") }
                    sb.appendLine()
                    sb.appendLine("  Student's enrolled class_id(s):")
                    enrolledClassIds.forEach { sb.appendLine("    $it") }
                    sb.appendLine()
                    sb.appendLine("  These don't match → that's the bug.")
                } else {
                    sb.appendLine("✅ Match found! Session class_id is in student enrollments.")
                    sb.appendLine()

                    // ── 5. Check time window ──────────────────────────────────
                    sb.appendLine("═══ TIME WINDOW CHECK ═══")
                    matchingSessions.forEach { s ->
                        try {
                            val startedAt  = java.time.Instant.parse(s.started_at)
                            val windowEnd  = startedAt.plusSeconds(s.time_window * 60L)
                            val now        = java.time.Instant.now()
                            val remaining  = now.until(windowEnd, java.time.temporal.ChronoUnit.SECONDS)

                            sb.appendLine("  started_at parsed OK: $startedAt")
                            sb.appendLine("  window ends at:       $windowEnd")
                            sb.appendLine("  now:                  $now")
                            sb.appendLine("  seconds remaining:    $remaining")

                            if (remaining <= 0) {
                                sb.appendLine("  ⚠️ Window has EXPIRED — minutesLeftInSession() returns 0")
                                sb.appendLine("     This is why it shows as Idle!")
                                sb.appendLine("     Fix: set a longer time_window or restart the session.")
                            } else {
                                sb.appendLine("  ✅ Window still open — ${remaining / 60} min ${remaining % 60} sec left")
                            }
                        } catch (e: Exception) {
                            sb.appendLine("  ⚠️ Failed to parse started_at: ${e.message}")
                            sb.appendLine("     This is why minutesLeftInSession() returns 0!")
                        }
                    }
                }
            }

        } catch (e: Exception) {
            isError = true
            sb.appendLine("EXCEPTION: ${e.message}")
            sb.appendLine()
            sb.appendLine("This likely means a Supabase query failed.")
            sb.appendLine("Check your RLS (Row Level Security) policies — the student")
            sb.appendLine("account might not have permission to read sessions or enrollments.")
        }

        output = sb.toString()
    }

    Scaffold(
        topBar = { AppBar(title = "Debug — Session Check", navController = navController) },
        containerColor = Color(0xFFF5F7FA)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (output == "Fetching...") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isError) Color(0xFFFFF0F0) else Color(0xFF1E1E2E)
                ) {
                    Text(
                        text = output,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (isError) Color(0xFFB71C1C) else Color(0xFFCDD6F4),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}