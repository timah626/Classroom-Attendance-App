package com.example.testapplication2


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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

private val BlueColor  = Color(0xFF1A73E8)
private val GreenColor = Color(0xFF34A853)
private val AmberColor = Color(0xFFFFA000)
private val GreyColor  = Color(0xFFBDBDBD)
private val RedColor   = Color(0xFFD32F2F)

@Composable
fun SessionHistoryPage(
    navController: NavController,
    supabase: SupabaseClient,
    classId: String,
    className: String
) {
    // ── State ──────────────────────────────────────────────────────────────
    var sessions        by remember { mutableStateOf<List<SessionHistoryRecord>>(emptyList()) }
    var students        by remember { mutableStateOf<List<StudentFilterItem>>(emptyList()) }
    var isLoading       by remember { mutableStateOf(true) }
    var errorMessage    by remember { mutableStateOf<String?>(null) }
    var expanded        by remember { mutableStateOf(false) }
    var selectedStudent by remember { mutableStateOf<StudentFilterItem?>(null) } // null = All Students

    // ── Initial load ───────────────────────────────────────────────────────
    LaunchedEffect(classId) {
        val result = fetchSessionHistory(supabase, classId, filterStudentId = null)
        result.fold(
            onSuccess = { data ->
                sessions  = data.sessions
                students  = data.students
                isLoading = false
            },
            onFailure = { err ->
                errorMessage = err.message
                isLoading    = false
            }
        )
    }

    // ── Re-fetch when student filter changes ───────────────────────────────
    LaunchedEffect(selectedStudent) {
        if (!isLoading) {   // don't double-fetch on first load
            isLoading = true
            val result = fetchSessionHistory(
                supabase         = supabase,
                classId          = classId,
                filterStudentId  = selectedStudent?.studentId
            )
            result.fold(
                onSuccess = { data -> sessions = data.sessions; isLoading = false },
                onFailure = { err -> errorMessage = err.message; isLoading = false }
            )
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = { AppBar(title = "Session History", navController = navController) },
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

            // ── Filter dropdown ──────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true },
                        shape = RoundedCornerShape(12.dp),
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
                            Column {
                                Text("Filter by student", fontSize = 11.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = selectedStudent?.name ?: "All Students",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1A1A1A)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand",
                                tint = Color.Gray
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f),
                        containerColor = Color.White
                    ) {
                        // "All Students" option
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "All Students",
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedStudent == null)
                                        FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedStudent == null)
                                        BlueColor else Color.Black
                                )
                            },
                            onClick = { selectedStudent = null; expanded = false }
                        )
                        // Individual students
                        students.forEach { student ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = student.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (student.studentId == selectedStudent?.studentId)
                                            FontWeight.Bold else FontWeight.Normal,
                                        color = if (student.studentId == selectedStudent?.studentId)
                                            BlueColor else Color.Black
                                    )
                                },
                                onClick = { selectedStudent = student; expanded = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Section label ────────────────────────────────────────────
            item {
                Text(
                    text = if (selectedStudent != null)
                        "${selectedStudent!!.name}'s sessions"
                    else "Past sessions",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Empty state ──────────────────────────────────────────────
            if (sessions.isEmpty()) {
                item {
                    Text(
                        text = "No sessions recorded yet.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }

            // ── Session rows ─────────────────────────────────────────────
           /* items(sessions) { session ->
                SessionRow(session = session)
            } */


            items(sessions) { session ->
                SessionRow(
                    session = session,
                    onClick = {
                        navController.navigate(
                            "sessionDetail/${session.sessionId}/${session.classId}/" +
                                    java.net.URLEncoder.encode(session.date, "UTF-8")
                        )
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Session row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SessionRow(session: SessionHistoryRecord,  onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { onClick() },                                          // ✅ add this
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
                    text = session.date,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1A1A1A)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = session.time, fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AttendanceSummaryChip(session.present, "present", GreenColor)
                    AttendanceSummaryChip(session.late,    "late",    AmberColor)
                    AttendanceSummaryChip(session.absent,  "absent",  GreyColor)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AttendanceSummaryChip(count: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = " $label", fontSize = 12.sp, color = Color.Gray)
    }
}
