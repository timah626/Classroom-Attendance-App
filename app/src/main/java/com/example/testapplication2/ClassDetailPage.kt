package com.example.testapplication2

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@Composable
fun ClassDetailPage(
    classId: String,
    navController: NavController,
    classViewModel: ClassViewModel = viewModel()
) {
    val blueColor     = Color(0xFF1A73E8)
    val isLoading     = classViewModel.isLoading
    val errorMessage  = classViewModel.errorMessage
    val classDetail   = classViewModel.selectedClass
    val studentCount  = classViewModel.studentCount
    val sessionCount  = classViewModel.sessionCount
    val avgAttendance = classViewModel.avgAttendance

    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    var isStartingSession          by remember { mutableStateOf(false) }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    var showBluetoothOffDialog     by remember { mutableStateOf(false) }  // ← new

    // ── Actual session start (only called once Bluetooth + permission are confirmed) ──
    fun doStartSession() {
        scope.launch {
            isStartingSession = true
            val result = startSession(supabase, classId, 10)
            isStartingSession = false
            result.onSuccess { session ->
                BleSessionManager.startBroadcast(context, session.session_code)
                navController.navigate(
                    "session/${session.id}/${classId}/${classDetail?.name ?: "Session"}"
                )
            }
        }
    }

    // ── Runtime permission launcher (Android 12+ only) ─────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) doStartSession() else showPermissionDeniedDialog = true
    }

    // ── Permission-and-Bluetooth-safe session starter ──────────────────────
    fun checkPermissionAndStartSession() {

        // Step 1 — Is Bluetooth switched on?
        if (!BleSessionManager.isBluetoothOn(context)) {
            showBluetoothOffDialog = true   // ask user to turn it on
            return
        }

        // Step 2 — Do we have BLUETOOTH_ADVERTISE permission? (Android 12+ only)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PermissionChecker.PERMISSION_GRANTED

            if (!granted) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
                return
            }
        }

        // Step 3 — All good, start the session
        doStartSession()
    }

    LaunchedEffect(classId) {
        classViewModel.fetchClassDetail(classId)
    }

    // ── Bluetooth is off dialog ────────────────────────────────────────────
    if (showBluetoothOffDialog) {
        AlertDialog(
            onDismissRequest = { showBluetoothOffDialog = false },
            title = { Text("Bluetooth is Off") },
            text  = {
                Text(
                    "AttendEase needs Bluetooth to broadcast the session code to students. " +
                            "Please turn on Bluetooth and try again."
                )
            },
            confirmButton = {
                TextButton(onClick = { showBluetoothOffDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // ── Permission denied dialog ───────────────────────────────────────────
    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text("Bluetooth Permission Required") },
            text  = {
                Text(
                    "AttendEase needs Bluetooth permission to broadcast the session " +
                            "code to students. Please grant it to start a session."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPermissionDeniedDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AppBar(
                title = classDetail?.name ?: "Class Detail",
                navController = navController
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = Color.White) {
                Button(
                    onClick = { checkPermissionAndStartSession() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .height(52.dp),
                    enabled = !isStartingSession,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = blueColor)
                ) {
                    if (isStartingSession) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = "Start Session",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF5F7FA)
    ) { innerPadding ->

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = blueColor)
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = errorMessage, color = Color.Red)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatBox("$studentCount",   "Students",        Modifier.weight(1f))
                            StatBox("$sessionCount",   "Sessions",        Modifier.weight(1f))
                            StatBox("$avgAttendance%", "Avg. Attendance", Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item {
                        Text(
                            text = "Students",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Upload student list",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    Text(
                                        text = "CSV or Excel file",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                Button(
                                    onClick = { navController.navigate("upload/$classId") },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = blueColor)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Upload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Upload", fontSize = 13.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item {
                        Button(
                            onClick = {
                                val encodedName = java.net.URLEncoder.encode(classDetail?.name ?: "Class", "UTF-8")
                                navController.navigate("sessionHistory/$classId/$encodedName")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = blueColor)
                        ) {
                            Text("View course attendance history", fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A73E8)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, fontSize = 11.sp, color = Color.Gray)
        }
    }
}
