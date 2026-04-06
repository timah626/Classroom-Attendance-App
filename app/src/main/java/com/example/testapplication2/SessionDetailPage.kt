package com.example.testapplication2



import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream

private val BlueColor  = Color(0xFF1A73E8)
private val GreenColor = Color(0xFF34A853)
private val AmberColor = Color(0xFFFFA000)
private val GreyColor  = Color(0xFFBDBDBD)
private val RedColor   = Color(0xFFD32F2F)

@Composable
fun SessionDetailPage(
    navController: NavController,
    supabase: SupabaseClient,
    sessionId: String,
    classId: String,
    sessionDate: String
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var students     by remember { mutableStateOf<List<StudentAttendanceRecord>>(emptyList()) }
    var isLoading    by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isExporting  by remember { mutableStateOf(false) }

    // ── Load data ──────────────────────────────────────────────────────────
    LaunchedEffect(sessionId) {
        val result = fetchSessionDetail(supabase, sessionId, classId)
        result.fold(
            onSuccess = { data -> students = data; isLoading = false },
            onFailure = { err -> errorMessage = err.message; isLoading = false }
        )
    }

    Scaffold(
        topBar = {
            AppBar(
                title = sessionDate,
                navController = navController,
                actions = {
                    // ── Download button ────────────────────────────────────
                    IconButton(
                        onClick = {
                            scope.launch {
                                isExporting = true
                                exportToExcel(
                                    context     = context,
                                    students    = students,
                                    sessionDate = sessionDate
                                )
                                isExporting = false
                            }
                        },
                        enabled = !isExporting && students.isNotEmpty()
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                color     = Color.White,
                                modifier  = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector        = Icons.Default.Download,
                                contentDescription = "Download",
                                tint               = Color.White
                            )
                        }
                    }
                }
            )
        },
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
                            color       = BlueColor,
                            modifier    = Modifier.size(28.dp),
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
                        shape    = RoundedCornerShape(12.dp),
                        color    = Color(0xFFFFF0F0)
                    ) {
                        Text(
                            text     = msg,
                            fontSize = 13.sp,
                            color    = RedColor,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                return@LazyColumn
            }

            // ── Summary chips ────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape     = RoundedCornerShape(12.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        SummaryChip(students.count { it.status == "present" }, "Present", GreenColor)
                        SummaryChip(students.count { it.status == "late" },    "Late",    AmberColor)
                        SummaryChip(students.count { it.status == "absent" },  "Absent",  GreyColor)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Section label ────────────────────────────────────────────
            item {
                Text(
                    text     = "Students",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color    = Color.Gray,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Empty state ──────────────────────────────────────────────
            if (students.isEmpty()) {
                item {
                    Text(
                        text     = "No students found for this session.",
                        fontSize = 13.sp,
                        color    = Color.Gray,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }

            // ── Student rows ─────────────────────────────────────────────
            items(students) { student ->
                StudentRow(student = student)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Student row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StudentRow(student: StudentAttendanceRecord) {
    val statusColor = when (student.status) {
        "present" -> GreenColor
        "late"    -> AmberColor
        else      -> GreyColor
    }

    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text       = student.name,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color(0xFF1A1A1A)
                )
                student.checkedInAt?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "Checked in at $it", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text     = student.status.replaceFirstChar { it.uppercase() },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color    = statusColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryChip(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$count", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Excel export — present students only, saved to Downloads
// ─────────────────────────────────────────────────────────────────────────────

private suspend fun exportToExcel(
    context: Context,
    students: List<StudentAttendanceRecord>,
    sessionDate: String
) = withContext(Dispatchers.IO) {
    try {
        val presentStudents = students.filter { it.status == "present" }

        val workbook  = XSSFWorkbook()
        val sheet     = workbook.createSheet("Attendance")

        // Header row
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("Name")
        headerRow.createCell(1).setCellValue("Status")
        headerRow.createCell(2).setCellValue("Checked In At")

        // Data rows
        presentStudents.forEachIndexed { index, student ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(student.name)
            row.createCell(1).setCellValue(student.status.replaceFirstChar { it.uppercase() })
            row.createCell(2).setCellValue(student.checkedInAt ?: "")
        }

        // Auto size columns
        repeat(3) { sheet.autoSizeColumn(it) }

        val fileName = "Attendance_${sessionDate.replace(",", "").replace(" ", "_")}.xlsx"

        // Save to Downloads
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                val stream: OutputStream? = context.contentResolver.openOutputStream(it)
                stream?.use { workbook.write(it) }
            }
        } else {
            val file = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            file.outputStream().use { workbook.write(it) }
        }

        workbook.close()

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
        }

    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}