package com.example.testapplication2.studentUI
import com.example.testapplication2.AppBar
import com.example.testapplication2.models.AttendanceStatus
import com.example.testapplication2.utils.parseInstant



import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

private val BlueColor  = Color(0xFF1A73E8)
private val GreenColor = Color(0xFF34A853)
private val AmberColor = Color(0xFFFFA000)
private val RedColor   = Color(0xFFD32F2F)
private val GreyColor  = Color(0xFF757575)

// ─────────────────────────────────────────────────────────────────────────────
// Models — joined query: attendance → sessions → classes
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class AttendanceWithClass(
    val status: String,
    val checked_in_at: String? = null,
    val sessions: SessionWithClass
)

@Serializable
data class SessionWithClass(
    val started_at: String,
    val classes: ClassNameOnly
)

@Serializable
data class ClassNameOnly(
    val name: String
)

// Flat resolved record used by the UI
data class ResolvedAttendanceRecord(
    val className: String,
    val date: String,
    val checkInTime: String?,
    val status: AttendanceStatus
)

// ─────────────────────────────────────────────────────────────────────────────
// Page
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StudentAttendanceHistoryPage(
    navController: NavController,
    supabase: SupabaseClient,
    studentId: String,
    className: String       // passed from nav — used to filter and as the top bar title
) {
    var records      by remember { mutableStateOf<List<ResolvedAttendanceRecord>>(emptyList()) }
    var isLoading    by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val presentCount = records.count { it.status == AttendanceStatus.PRESENT }
    val lateCount    = records.count { it.status == AttendanceStatus.LATE }
    val absentCount  = records.count { it.status == AttendanceStatus.ABSENT }

    LaunchedEffect(studentId, className) {
        try {
            // Single query: attendance → sessions → classes
            // This is how we get class name without it being in the attendance table
            val rows = supabase.postgrest["attendance"]
                .select(
                    Columns.raw("""
                        status,
                        checked_in_at,
                        sessions (
                            started_at,
                            classes (
                                name
                            )
                        )
                    """.trimIndent())
                ) {
                    filter { eq("student_id", studentId) }
                }
                .decodeList<AttendanceWithClass>()

            records = rows
                .filter { it.sessions.classes.name == className }
                .map { row ->
                    val status = when (row.status) {
                        "present" -> AttendanceStatus.PRESENT
                        "late"    -> AttendanceStatus.LATE
                        else      -> AttendanceStatus.ABSENT
                    }

                    val date = try {
                        val instant = parseInstant(row.sessions.started_at)
                        val zoned   = instant.atZone(java.time.ZoneId.systemDefault())
                        val day     = zoned.dayOfWeek.getDisplayName(
                            java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH
                        )
                        val month   = zoned.month.getDisplayName(
                            java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH
                        )
                        "$day, ${zoned.dayOfMonth} $month ${zoned.year}"
                    } catch (e: Exception) { "Unknown date" }

                    val checkInTime = row.checked_in_at?.let {
                        try {
                            val instant = parseInstant(it)
                            val zoned   = instant.atZone(java.time.ZoneId.systemDefault())
                            "%02d:%02d %s".format(
                                if (zoned.hour % 12 == 0) 12 else zoned.hour % 12,
                                zoned.minute,
                                if (zoned.hour < 12) "AM" else "PM"
                            )
                        } catch (e: Exception) { null }
                    }

                    ResolvedAttendanceRecord(
                        className   = row.sessions.classes.name,
                        date        = date,
                        checkInTime = checkInTime,
                        status      = status
                    )
                }
                .sortedByDescending { it.date }

        } catch (e: Exception) {
            errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────
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

            // ── Loading ──────────────────────────────────────────────────
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = BlueColor,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                return@LazyColumn
            }

            // ── Error ────────────────────────────────────────────────────
            errorMessage?.let { msg ->
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF0F0)
                    ) {
                        Text(
                            text = msg,
                            fontSize = 13.sp,
                            color = RedColor,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                return@LazyColumn
            }

            // ── Empty ────────────────────────────────────────────────────
            if (records.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No attendance records yet for $className.",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
                return@LazyColumn
            }

            // ── Stats row ────────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AttendanceStatBox("$presentCount", "Present", GreenColor, Modifier.weight(1f))
                    AttendanceStatBox("$lateCount",    "Late",    AmberColor, Modifier.weight(1f))
                    AttendanceStatBox("$absentCount",  "Absent",  GreyColor,  Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Section label ────────────────────────────────────────────
            item {
                Text(
                    text = "Past sessions — ${records.size} total",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Record rows ──────────────────────────────────────────────
            items(records) { record ->
                AttendanceRecordRow(record = record)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AttendanceStatBox(
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun AttendanceRecordRow(record: ResolvedAttendanceRecord) {
    val (badgeText, badgeColor, badgeBg) = when (record.status) {
        AttendanceStatus.PRESENT -> Triple("Present", GreenColor, Color(0xFFE6F4EA))
        AttendanceStatus.LATE    -> Triple("Late",    AmberColor, Color(0xFFFFF3E0))
        AttendanceStatus.ABSENT  -> Triple("Absent",  GreyColor,  Color(0xFFF1F3F4))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.date,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1A1A1A)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = record.checkInTime?.let { "Checked in at $it" } ?: "Not checked in",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Surface(shape = RoundedCornerShape(20.dp), color = badgeBg) {
                Text(
                    text = badgeText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}