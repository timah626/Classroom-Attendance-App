package com.example.demogradecalculator



import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.awt.Frame

@Composable
fun App() {
    var students by remember { mutableStateOf<List<Student>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("") }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Grade Calculator", style = MaterialTheme.typography.headlineMedium)

            Button(onClick = {
                val dialog = FileDialog(Frame(), "Select Excel File", FileDialog.LOAD)
                dialog.isVisible = true
                val file = dialog.file
                val dir = dialog.directory
                if (file != null) {
                    val path = dir + file
                    students = readStudentsFromExcel(path)
                    val outputPath = dir + "results.xlsx"
                    writeResultsToExcel(students, outputPath)
                    statusMessage = "Done! results.xlsx saved to $outputPath"
                }
            }) {
                Text("Select Excel File")
            }

            if (statusMessage.isNotEmpty()) {
                Text(statusMessage, color = MaterialTheme.colorScheme.primary)
            }

            if (students.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Name", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text("Score", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text("Grade", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                }
                HorizontalDivider()
                LazyColumn {
                    items(students) { student ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(student.name, modifier = Modifier.weight(1f))
                            Text(student.score?.toString() ?: "N/A", modifier = Modifier.weight(1f))
                            Text(student.grade, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}