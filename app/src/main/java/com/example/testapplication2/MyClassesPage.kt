package com.example.testapplication2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@Composable
fun MyClassesPage(
    navController: NavController,
    classViewModel: ClassViewModel = viewModel()
) {
    val classes      = classViewModel.classes
    val isLoading    = classViewModel.isLoading
    val errorMessage = classViewModel.errorMessage

    // Fetch classes when screen opens
    LaunchedEffect(Unit) {
        classViewModel.fetchMyClasses()
    }

    Scaffold(
        topBar = {
            AppBar(title = "My Classes", navController = navController)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("createClass") },
                containerColor = Color(0xFF1A73E8),
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Create class")
            }
        },
        containerColor = Color(0xFFF5F7FA)
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // Loading state
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF1A73E8)
                    )
                }

                // Error state
                errorMessage != null -> {
                    Text(
                        text = errorMessage,
                        color = Color.Red,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                // Empty state
                classes.isEmpty() -> {
                    Text(
                        text = "No classes yet. Tap + to create one.",
                        color = Color.Gray,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                // Classes loaded
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp)
                    ) {
                        // Live classes (sessions active — for now just show all as idle)
                        val liveClasses = classes.filter { false } // hook up sessions later
                        val idleClasses = classes

                        if (liveClasses.isNotEmpty()) {
                            SectionLabel("Active")
                            liveClasses.forEach { cls ->
                                ClassesWidget(
                                    title = cls.name,
                                    subtitle = "Session in progress",
                                    status = ClassStatus.LIVE,
                                    navController = navController,
                                    onClick = { navController.navigate("classDetail/${cls.id}") }
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        SectionLabel("All Classes")
                        idleClasses.forEach { cls ->
                            ClassesWidget(
                                title = cls.name,
                                subtitle = "${cls.academicYear} • No active session",
                                status = ClassStatus.IDLE,
                                navController = navController,
                                onClick = { navController.navigate("classDetail/${cls.id}") }
                            )
                        }
                    }
                }
            }
        }
    }
}

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







