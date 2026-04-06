package com.example.testapplication2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

private val GreenColor = Color(0xFF34A853)
private val GreenBg    = Color(0xFFE6F4EA)
private val BlueColor  = Color(0xFF1A73E8)
private val AmberColor = Color(0xFFFFA000)
private val RedColor   = Color(0xFFD32F2F)

@Composable
fun AttendanceConfirmedPage(
    navController: NavController,
    supabase: SupabaseClient,
    sessionId: String,
    studentId: String,
    classId: String,
    className: String          // already decoded from nav args
) {
    // ── State ──────────────────────────────────────────────────────────────
    var stats       by remember { mutableStateOf<AttendanceConfirmedStats?>(null) }
    var isLoading   by remember { mutableStateOf(true) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }
    var isLate      by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId, studentId) {
        // Check if marked late
        try {
            val record = supabase.postgrest.from("attendance")
                .select { filter {
                    eq("session_id", sessionId)
                    eq("student_id", studentId)
                }}
                .decodeSingle<AttendanceStatusRowPublic>()
            isLate = record.status == "late"
        } catch (_: Exception) {}

        // Fetch stats
        val result = fetchConfirmedStats(
            supabase  = supabase,
            sessionId = sessionId,
            studentId = studentId,
            classId   = classId,
            className = className
        )
        result.fold(
            onSuccess = { stats = it },
            onFailure = { errorMsg = it.message }
        )
        isLoading = false
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = { AppBar(title = "Check In", navController = navController) },
        containerColor = Color(0xFFF5F7FA)
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            when {
                isLoading -> {
                    CircularProgressIndicator(color = BlueColor)
                }

                errorMsg != null -> {
                    Text(
                        text = errorMsg!!,
                        color = RedColor,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { navController.popBackStack() }) {
                        Text("Go Back")
                    }
                }

                stats != null -> {
                    val s = stats!!
                    val accentColor = if (isLate) AmberColor else GreenColor
                    val accentBg    = if (isLate) Color(0xFFFFF3E0) else GreenBg

                    Spacer(modifier = Modifier.weight(1f))

                    // ── Circle icon ────────────────────────────────────────
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(accentBg)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Confirmed",
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // ── Title ──────────────────────────────────────────────
                    Text(
                        text = if (isLate) "Marked Late" else "Attendance marked!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Description ────────────────────────────────────────
                    Text(
                        text = if (isLate)
                            "You were outside the check-in window.\nYou've been marked late for\n${s.className}\non ${s.markedDate} at ${s.markedTime}."
                        else
                            "You have been marked present for\n${s.className}\non ${s.markedDate} at ${s.markedTime}.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // ── Stats row ──────────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ConfirmStatBox(
                            value    = "${s.attendancePercent}%",
                            label    = "Attendance rate",
                            color    = accentColor,
                            modifier = Modifier.weight(1f)
                        )
                        ConfirmStatBox(
                            value    = "${s.sessionsAttended}",
                            label    = "Sessions attended",
                            color    = accentColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── View history button ────────────────────────────────
                    OutlinedButton(
                        onClick = {
                            val encodedClassName = java.net.URLEncoder.encode(className, "UTF-8")
                            navController.navigate("studentHistory/$studentId/$encodedClassName")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BlueColor)
                    ) {
                        Text(
                            text = "View history",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// ── Stat box ──────────────────────────────────────────────────────────────────
@Composable
private fun ConfirmStatBox(
    value: String,
    label: String,
    color: Color,
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
                .padding(vertical = 18.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}

// Needs to be public so AttendanceConfirmedPage.kt can use it
@kotlinx.serialization.Serializable
data class AttendanceStatusRowPublic(val status: String)
