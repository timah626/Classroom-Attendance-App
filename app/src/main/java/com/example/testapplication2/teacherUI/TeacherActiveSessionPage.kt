package com.example.testapplication2.teacherUI



import com.example.testapplication2.AppBar
import com.example.testapplication2.bluettoothfunctionalities.BleSessionManager
import com.example.testapplication2.models.AttendanceStatus
import com.example.testapplication2.models.ProfileEntry
import com.example.testapplication2.models.SessionRow
import com.example.testapplication2.models.SessionStudent
import com.example.testapplication2.repositories.endSession

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.example.testapplication2.utils.parseInstant
import com.example.testapplication2.utils.parseSupabaseTimestamp



// ─────────────────────────────────────────────────────────────────────────────
// Local models
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
private data class EnrolledEntry(val student_id: String?)

@Serializable
private data class AttendanceEntry(
    val student_id: String,
    val status: String,
    val checked_in_at: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Colors
// ─────────────────────────────────────────────────────────────────────────────

private val GreenColor = Color(0xFF34A853)
private val AmberColor = Color(0xFFFFA000)
private val GreyColor  = Color(0xFFBDBDBD)
private val RedColor   = Color(0xFFD32F2F)
private val BlueColor  = Color(0xFF1A73E8)

// ─────────────────────────────────────────────────────────────────────────────
// Helper
// ─────────────────────────────────────────────────────────────────────────────

private suspend fun fetchLiveAttendance(
    supabase: SupabaseClient,
    sessionId: String,
    classId: String
): List<SessionStudent> {

    val enrolled = supabase.postgrest["enrollments"]
        .select(Columns.list("student_id")) {
            filter { eq("class_id", classId) }
        }
        .decodeList<EnrolledEntry>()
        .mapNotNull { it.student_id }

    if (enrolled.isEmpty()) return emptyList()

    val profiles = supabase.postgrest["profiles"]
        .select(Columns.list("id", "username")) {
            filter { isIn("id", enrolled) }
        }
        .decodeList<ProfileEntry>()
        .associateBy { it.id }

    val attended = supabase.postgrest["attendance"]
        .select(Columns.list("student_id", "status", "checked_in_at")) {
            filter { eq("session_id", sessionId) }
        }
        .decodeList<AttendanceEntry>()
        .associateBy { it.student_id }

    return enrolled.mapNotNull { studentId ->
        val profile = profiles[studentId] ?: return@mapNotNull null
        val record  = attended[studentId]

        val status = when (record?.status) {
            "present" -> AttendanceStatus.PRESENT
            "late"    -> AttendanceStatus.LATE
            else      -> AttendanceStatus.ABSENT
        }

        val checkInTime = record?.checked_in_at?.let { iso ->
            try {
                val instant = parseInstant(iso)
                val zoned   = instant.atZone(java.time.ZoneId.systemDefault())
                "%02d:%02d %s".format(
                    if (zoned.hour % 12 == 0) 12 else zoned.hour % 12,
                    zoned.minute,
                    if (zoned.hour < 12) "AM" else "PM"
                )
            } catch (e: Exception) { null }
        }

        SessionStudent(name = profile.username, checkInTime = checkInTime, status = status)
    }.sortedWith(compareBy({ it.status.ordinal }, { it.name }))
}

// ─────────────────────────────────────────────────────────────────────────────
// Main screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TeacherActiveSessionPage(
    navController: NavController,
    supabase: SupabaseClient,
    sessionId: String,
    classId: String,
    className: String = "Active Session"
) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current                          // ← needed to stop the service

    var session      by remember { mutableStateOf<SessionRow?>(null) }
    var students     by remember { mutableStateOf<List<SessionStudent>>(emptyList()) }
    var isEnding     by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var secondsLeft  by remember { mutableStateOf(0) }
    var timerRunning by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        while (isActive) {
            try {
                val fetched = supabase.postgrest["sessions"]
                    .select(
                        Columns.list(
                            "id", "class_id", "session_code",
                            "time_window", "started_at", "ended_at", "status"
                        )
                    ) { filter { eq("id", sessionId) } }
                    .decodeSingle<SessionRow>()

                session = fetched

                if (fetched.status == "active") {
                    val startedAt  = parseSupabaseTimestamp(fetched.started_at)
                    val windowEnd  = startedAt.plusSeconds(fetched.time_window * 60L)
                    val remaining  = java.time.Instant.now().until(
                        windowEnd, java.time.temporal.ChronoUnit.SECONDS
                    )
                    secondsLeft  = if (remaining > 0) remaining.toInt() else 0
                    timerRunning = true
                } else {
                    secondsLeft  = 0
                    timerRunning = false
                }

                students = fetchLiveAttendance(supabase, sessionId, classId)

            } catch (e: Exception) {
                errorMessage = "Failed to load session data: ${e.message}"
            }

            delay(5_000L)
        }
    }

    LaunchedEffect(timerRunning) {
        while (timerRunning && secondsLeft > 0) {
            delay(1_000L)
            secondsLeft = (secondsLeft - 1).coerceAtLeast(0)
        }
    }

    val minutes      = secondsLeft / 60
    val seconds      = secondsLeft % 60
    val timerText    = "%02d:%02d".format(minutes, seconds)
    val presentCount = students.count { it.status == AttendanceStatus.PRESENT }
    val lateCount    = students.count { it.status == AttendanceStatus.LATE }
    val absentCount  = students.count { it.status == AttendanceStatus.ABSENT }
    val sessionCode  = session?.session_code ?: "------"

    Scaffold(
        topBar = { AppBar(title = className, navController = navController) },
        containerColor = Color(0xFFF5F7FA)
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            // ── Session Code + Timer Card ──────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Time window closes in",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = timerText,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (secondsLeft <= 60) RedColor else Color(0xFF1A1A1A)
                                )
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isEnding = true
                                        val result = endSession(supabase, sessionId)
                                        isEnding = false

                                        result.fold(
                                            onSuccess = {
                                                // ── STOP BLE BROADCAST ───────────────────────
                                                BleSessionManager.stopBroadcast(context)
                                                // ────────────────────────────────────────────
                                                navController.popBackStack()
                                            },
                                            onFailure = { errorMessage = it.message }
                                        )
                                    }
                                },
                                enabled = !isEnding,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFEBEE),
                                    contentColor = RedColor,
                                    disabledContainerColor = Color(0xFFFFEBEE).copy(alpha = 0.5f),
                                    disabledContentColor = RedColor.copy(alpha = 0.4f)
                                ),
                                elevation = ButtonDefaults.buttonElevation(0.dp)
                            ) {
                                if (isEnding) {
                                    CircularProgressIndicator(
                                        color = RedColor,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "End Session",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "Session code", fontSize = 11.sp, color = Color.Gray)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFE8F0FE)
                            ) {
                                Text(
                                    text = sessionCode,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = BlueColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Error banner ───────────────────────────────────────────────
            errorMessage?.let { msg ->
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFF0F0)
                    ) {
                        Text(
                            text = msg,
                            fontSize = 13.sp,
                            color = RedColor,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // ── Stats Row ──────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SessionStatBox("$presentCount", "Present", GreenColor, Modifier.weight(1f))
                    SessionStatBox("$lateCount",    "Late",    AmberColor, Modifier.weight(1f))
                    SessionStatBox("$absentCount",  "Absent",  GreyColor,  Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Section label ──────────────────────────────────────────────
            item {
                Text(
                    text = if (students.isEmpty()) "Loading students…"
                    else "Attendance — ${students.size} total",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Student rows ───────────────────────────────────────────────
            if (students.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = BlueColor,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            } else {
                items(students) { student -> SessionStudentRow(student = student) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SessionStatBox(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun SessionStudentRow(student: SessionStudent) {
    val dotColor = when (student.status) {
        AttendanceStatus.PRESENT -> GreenColor
        AttendanceStatus.LATE    -> AmberColor
        AttendanceStatus.ABSENT  -> GreyColor
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1A1A1A)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = student.checkInTime?.let { "Checked in at $it" } ?: "Not yet checked in",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}