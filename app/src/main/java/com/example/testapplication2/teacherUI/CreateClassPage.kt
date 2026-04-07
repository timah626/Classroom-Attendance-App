package com.example.testapplication2.teacherUI

import com.example.testapplication2.viewmodels.ClassViewModel
import com.example.testapplication2.AppBar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

private val BlueColor = Color(0xFF1A73E8)

private val academicYears = listOf(
    "2023 / 2024",
    "2024 / 2025",
    "2025 / 2026",
    "2026 / 2027",
)

@Composable
fun CreateClassPage(
    navController: NavController,
    classViewModel: ClassViewModel = viewModel()
) {
    var className    by remember { mutableStateOf("") }
    var description  by remember { mutableStateOf("") }
    var department   by remember { mutableStateOf("") }
    var yearExpanded by remember { mutableStateOf(false) }
    var selectedYear by remember { mutableStateOf("2025 / 2026") }

    val isLoading    = classViewModel.isLoading
    val errorMessage = classViewModel.errorMessage
    val canCreate    = className.isNotBlank() && !isLoading

    Scaffold(
        topBar = {
            AppBar(title = "Create Class", navController = navController)
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    // ⚠️ Show error if creation fails
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = Color.Red,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Button(
                        onClick = {
                            classViewModel.createClass(
                                name         = className,
                                description  = description,
                                department   = department,
                                academicYear = selectedYear
                            ) {
                                // On success, go back to My Classes
                                navController.navigate("myclasses") {
                                    popUpTo("myclasses") { inclusive = true }
                                }
                            }
                        },
                        enabled = canCreate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BlueColor,
                            disabledContainerColor = Color(0xFFBDBDBD)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Create class",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FieldLabel("Class name")
            OutlinedTextField(
                value = className,
                onValueChange = { className = it },
                placeholder = { Text("e.g. Mathematics — Year 2", color = Color.LightGray) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = outlinedFieldColors()
            )

            FieldLabel("Description (optional)")
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text("Add a short description of this class...", color = Color.LightGray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 5,
                colors = outlinedFieldColors()
            )

            FieldLabel("Department (optional)")
            OutlinedTextField(
                value = department,
                onValueChange = { department = it },
                placeholder = { Text("e.g. Science & Technology", color = Color.LightGray) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = outlinedFieldColors()
            )

            FieldLabel("Academic year")
            Box {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { yearExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = selectedYear, fontSize = 15.sp, color = Color(0xFF1A1A1A))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = Color.Gray
                        )
                    }
                }

                DropdownMenu(
                    expanded = yearExpanded,
                    onDismissRequest = { yearExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    academicYears.forEach { year ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = year,
                                    fontSize = 14.sp,
                                    fontWeight = if (year == selectedYear) FontWeight.Bold else FontWeight.Normal,
                                    color = if (year == selectedYear) BlueColor else Color(0xFF1A1A1A)
                                )
                            },
                            onClick = {
                                selectedYear = year
                                yearExpanded = false
                            }
                        )
                    }
                }
            }

            Text(
                text = "You can upload your student list after creating the class.",
                fontSize = 12.sp,
                color = Color.Gray,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF444444),
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = BlueColor,
    unfocusedBorderColor    = Color(0xFFDDDDDD),
    focusedContainerColor   = Color.White,
    unfocusedContainerColor = Color.White,
    cursorColor             = BlueColor
)