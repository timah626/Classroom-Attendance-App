package com.example.testapplication2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val BlueColor  = Color(0xFF1A73E8)
private val GreenColor = Color(0xFF1E8C45)
private val GreenBg    = Color(0xFFE6F4EA)
private val GreyColor  = Color(0xFF757575)
private val GreyBg     = Color(0xFFF1F3F4)
private val RedColor   = Color(0xFFD32F2F)

@Composable
fun StudentHomePage(
    navController: NavController,
    supabase: SupabaseClient,
    studentId: String           // the logged-in student's user id
) {
    // ── State ──────────────────────────────────────────────────────────────
    var allClasses    by remember { mutableStateOf<List<StudentClassItem>>(emptyList()) }
    var isLoading     by remember { mutableStateOf(true) }
    var errorMessage  by remember { mutableStateOf<String?>(null) }

    // ── Poll every 10 seconds so active sessions appear without manual refresh
    LaunchedEffect(studentId) {
        while (isActive) {
            val result = fetchStudentClasses(supabase, studentId)
            result.fold(
                onSuccess = {
                    allClasses = it
                    isLoading = false
                    errorMessage = null
                },
                onFailure = {
                    errorMessage = it.message
                    isLoading = false
                }
            )
            delay(10_000L)
        }
    }

    // ── Split into active-session classes vs the rest ──────────────────────
    val activeClasses = allClasses.filter { it.activeSession != null }
    val otherClasses  = allClasses.filter { it.activeSession == null }

    // ── UI ─────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            AppBar(title = "My Classes", navController = navController)
        },
       floatingActionButton = {
            if (otherClasses.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { navController.navigate("debug") },
                    containerColor = Color(0xFF1A73E8),
                    contentColor = Color.White
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "we debug")
                }
            }
        },
        containerColor = Color(0xFFF5F7FA)
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            // ── Loading state ────────────────────────────────────────────
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
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

            // ── Error state ──────────────────────────────────────────────
            errorMessage?.let { msg ->
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
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
            }

            // ── Empty state ──────────────────────────────────────────────
            if (allClasses.isEmpty() && errorMessage == null) {
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "You're not enrolled in any classes yet.",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ask your teacher to add you.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
                return@LazyColumn
            }

            // ── "Session active now" section ─────────────────────────────
            if (activeClasses.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionLabel(text = "Session active now")
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(activeClasses) { cls ->
                    ActiveClassCard(
                        cls = cls,
                        navController = navController,
                        studentId = studentId
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }

            // ── "My classes" section ─────────────────────────────────────
            if (otherClasses.isNotEmpty()) {
                item {
                    SectionLabel(
                        text = if (activeClasses.isEmpty()) "My classes" else "My other classes"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

               /* items(otherClasses) { cls ->
                    IdleClassCard(cls = cls)
                } */

                items(otherClasses) { cls ->
                    IdleClassCard(
                        cls = cls,
                        studentId = studentId,
                        navController = navController
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Active class card — green border, Check in button, live minutes left
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveClassCard(
    cls: StudentClassItem,
    navController: NavController,
    studentId: String
) {
    // Local countdown ticking down each minute
    var minutesLeft by remember(cls.classId) {
        mutableStateOf(
            cls.activeSession?.let { minutesLeftInSession(it) } ?: 0
        )
    }

    LaunchedEffect(cls.classId) {
        while (isActive && minutesLeft > 0) {
            delay(60_000L)
            minutesLeft = cls.activeSession?.let { minutesLeftInSession(it) } ?: 0
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, GreenColor),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cls.className,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1A1A1A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (minutesLeft > 0)
                        "Session live · Window: $minutesLeft min left"
                    else
                        "Session live · Window closing soon",
                    fontSize = 13.sp,
                    color = GreenColor,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cls.teacherName,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = {
                    val sessionId = cls.activeSession?.id ?: return@Button
                    val encodedClassName = java.net.URLEncoder.encode(cls.className, "UTF-8")
                    val encodedTeacherName = java.net.URLEncoder.encode(cls.teacherName, "UTF-8")
                    navController.navigate(
                        "studentScanning/$sessionId/$studentId/$encodedTeacherName/$encodedClassName/${cls.classId}"
                    )
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenColor,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Check in",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Idle class card — no active session
// ─────────────────────────────────────────────────────────────────────────────


@Composable
private fun IdleClassCard(
    cls: StudentClassItem,
    studentId: String,
    navController: NavController
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable {
                val encodedClassName = java.net.URLEncoder.encode(cls.className, "UTF-8")
                navController.navigate("studentHistory/$studentId/$encodedClassName")
            },
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
                    text = cls.className,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1A1A1A)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "No active session",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cls.teacherName,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = GreyBg
            ) {
                Text(
                    text = "Idle",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GreyColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Reusable section label
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Gray,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}
