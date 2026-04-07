package com.example.testapplication2.teacherUI

import com.example.testapplication2.models.StudentEntry
import com.example.testapplication2.models.EnrollmentSummary
import com.example.testapplication2.models.ParseResult
import com.example.testapplication2.AppBar
import com.example.testapplication2.repositories.parseStudentFile
import com.example.testapplication2.repositories.bulkEnrollStudents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.launch

@Composable





fun UploadDocumentPage(
    navController: NavController,
    supabase: SupabaseClient,
    classId: String              // pass the class the teacher is enrolling into
) {
    val blueColor = Color(0xFF1A73E8)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── State ────────────────────────────────────────────────────────────────
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var parsedStudents by remember { mutableStateOf<List<StudentEntry>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var enrollmentSummary by remember { mutableStateOf<EnrollmentSummary?>(null) }
    var enrollmentError by remember { mutableStateOf<String?>(null) }

    val hasFile = selectedUri != null && parseError == null && parsedStudents.isNotEmpty()

    // ── File picker launcher ──────────────────────────────────────────────────
    // Accepts CSV and Excel MIME types
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        // Reset previous state
        selectedUri = uri
        selectedFileName = uri.lastPathSegment ?: "selected_file"
        parsedStudents = emptyList()
        parseError = null
        enrollmentSummary = null
        enrollmentError = null

        // Parse immediately after selection
        val result = parseStudentFile(context, uri)
        when (result) {
            is ParseResult.Success -> parsedStudents = result.students
            is ParseResult.Error -> parseError = result.message
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            AppBar(title = "Upload Students", navController = navController)
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Button(
                    onClick = {
                        if (parsedStudents.isEmpty()) return@Button
                        scope.launch {
                            isLoading = true
                            enrollmentSummary = null
                            enrollmentError = null

                            val result = bulkEnrollStudents(supabase, parsedStudents, classId)

                            result.fold(
                                onSuccess = { enrollmentSummary = it },
                                onFailure = { enrollmentError = it.message }
                            )
                            isLoading = false
                        }
                    },
                    enabled = hasFile && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = blueColor,
                        disabledContainerColor = Color(0xFFBDBDBD)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (hasFile) "Upload & Enroll ${parsedStudents.size} Students"
                            else "Upload & Enroll Students",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF5F7FA)
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Description ──────────────────────────────────────────────────
            Text(
                text = "Upload a CSV or Excel file containing your students' names and emails. They will be enrolled into this class automatically.",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Dashed Upload Box ────────────────────────────────────────────
            val strokeColor = when {
                parseError != null -> Color(0xFFE53935)
                hasFile -> blueColor
                else -> Color(0xFFBDBDBD)
            }
            val boxBackground = when {
                parseError != null -> Color(0xFFFFF0F0)
                hasFile -> Color(0xFFE8F0FE)
                else -> Color.White
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(boxBackground)
                    .drawBehind {
                        drawRoundRect(
                            color = strokeColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(12f, 8f), 0f
                                )
                            ),
                            cornerRadius = CornerRadius(16.dp.toPx())
                        )
                    }
                    .clickable { filePicker.launch("*/*") },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = strokeColor,
                        modifier = Modifier.size(48.dp)
                    )

                    Text(
                        text = selectedFileName ?: "Tap to select file",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = if (selectedFileName != null) strokeColor else Color(0xFF1A1A1A)
                    )

                    // Show parsed count if successful, or error message
                    when {
                        parseError != null -> Text(
                            text = parseError!!,
                            fontSize = 12.sp,
                            color = Color(0xFFE53935),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        parsedStudents.isNotEmpty() -> Text(
                            text = "${parsedStudents.size} students found",
                            fontSize = 12.sp,
                            color = blueColor,
                            fontWeight = FontWeight.Medium
                        )
                        else -> Text(
                            text = "Supports .csv and .xlsx",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    OutlinedButton(
                        onClick = { filePicker.launch("*/*") },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, strokeColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = strokeColor)
                    ) {
                        Text(
                            text = if (selectedFileName != null) "Change file" else "Browse files",
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Enrollment result banner ──────────────────────────────────────
            enrollmentSummary?.let { summary ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                "Enrollment complete",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color(0xFF1B5E20)
                            )
                            Text(
                                "${summary.enrolled} enrolled · ${summary.pending} pending · ${summary.skipped} skipped",
                                fontSize = 12.sp,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            enrollmentError?.let { error ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF0F0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = error,
                            fontSize = 13.sp,
                            color = Color(0xFFB71C1C)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Section Label ─────────────────────────────────────────────────
            Text(
                text = "Expected format",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 10.dp)
            )

            // ── Code Preview Box ──────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1E2E)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row {
                        CodeText("name", color = Color(0xFF82AAFF), modifier = Modifier.width(180.dp))
                        CodeText("email", color = Color(0xFF82AAFF))
                    }
                    HorizontalDivider(color = Color(0xFF3A3A4A), thickness = 1.dp)
                    Row {
                        CodeText("Alice Mbarga", modifier = Modifier.width(180.dp))
                        CodeText("alice@email.com")
                    }
                    Row {
                        CodeText("Bruno Tagne", modifier = Modifier.width(180.dp))
                        CodeText("bruno@email.com")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CodeText(
    text: String,
    color: Color = Color(0xFFCDD6F4),
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = color,
        modifier = modifier
    )
}

