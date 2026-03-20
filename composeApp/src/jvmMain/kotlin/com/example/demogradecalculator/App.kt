package com.example.demogradecalculator

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.FileDialog
import java.awt.Frame

@Composable
fun App() {
    var students by remember { mutableStateOf<List<Student>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF4F6AF5),
            background = Color(0xFFF5F6FA),
            surface = Color.White
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {


                // Header
                {/* Column {
                    Text(
                        "Grade Calculator",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A2E)
                    )
                    Text(
                        "Import an Excel file to calculate student grades",
                        fontSize = 14.sp,
                        color = Color(0xFF6B7280)
                    )
                } */}
                Column(
                    modifier = Modifier.fillMaxWidth(), // takes full width
                    horizontalAlignment = Alignment.CenterHorizontally // centers children
                ) {
                    Text(
                        text = "Grade Calculator",
                        modifier = Modifier.padding(top = 52.dp),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A2E),
                        textAlign = TextAlign.Center // centers text inside
                    )

                    Text(
                        text = "Import an Excel file to calculate student grades",
                        modifier = Modifier.padding(top = 14.dp),
                        fontSize = 14.sp,
                        color = Color(0xFF6B7280),
                        textAlign = TextAlign.Center
                    )
                }




                // Upload button
                Button(
                    onClick = {
                        val dialog = FileDialog(Frame(), "Select Excel File", FileDialog.LOAD)
                        dialog.isVisible = true
                        val file = dialog.file
                        val dir = dialog.directory
                        if (file != null) {
                            try {
                                val path = dir + file
                                students = readStudentsFromExcel(path)
                                val outputPath = dir + "results.xlsx"
                                writeResultsToExcel(students, outputPath)
                                statusMessage = "results.xlsx saved to $dir"
                                isError = false
                            } catch (e: Exception) {
                                statusMessage = "Error: ${e.message}"
                                isError = true
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F6AF5)),
                    modifier = Modifier.height(46.dp)
                ) {
                    Text("Select Excel File", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }

                // Status message
                if (statusMessage.isNotEmpty()) {
                    Text(
                        statusMessage,
                        fontSize = 13.sp,
                        color = if (isError) Color(0xFFE53935) else Color(0xFF2E7D32)
                    )
                }

                // Results table
                if (students.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {

                            // Table header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF0F1FF), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                TableCell("Name", weight = 2f, header = true)
                                TableCell("CA (/30)", weight = 1f, header = true)
                                TableCell("Exam (/70)", weight = 1f, header = true)
                                TableCell("Total", weight = 1f, header = true)
                                TableCell("Grade", weight = 1f, header = true)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Table rows
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(students) { student ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TableCell(student.name, weight = 2f)
                                        TableCell(student.ca_mark?.toString() ?: "N/A", weight = 1f)
                                        TableCell(student.exam_mark?.toString() ?: "N/A", weight = 1f)
                                        TableCell(student.score.toString(), weight = 1f)
                                        GradeChip(student.grade)
                                    }
                                    HorizontalDivider(color = Color(0xFFF0F0F0))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.TableCell(text: String, weight: Float, header: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        fontSize = if (header) 13.sp else 14.sp,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        color = if (header) Color(0xFF4F6AF5) else Color(0xFF1A1A2E)
    )
}

@Composable
fun RowScope.GradeChip(grade: String) {
    val color = when (grade) {
        "A" -> Color(0xFF2E7D32)
        "B" -> Color(0xFF1565C0)
        "C" -> Color(0xFFF57F17)
        "D" -> Color(0xFFE65100)
        else -> Color(0xFFC62828)
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .wrapContentWidth(Alignment.Start)
    ) {
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(grade, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}