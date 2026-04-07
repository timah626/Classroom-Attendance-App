package com.example.testapplication2.studentUI
import com.example.testapplication2.models.CheckInStatus
import com.example.testapplication2.models.SessionRow
import com.example.testapplication2.utils.parseInstant
import com.example.testapplication2.bluettoothfunctionalities.startBleScanning
import com.example.testapplication2.bluettoothfunctionalities.stopBleScanning
import com.example.testapplication2.repositories.markAttendance
import com.example.testapplication2.AppBar



import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BlueColor  = Color(0xFF1A73E8)
private val BlueFaint  = Color(0x221A73E8)
private val BlueMid    = Color(0x661A73E8)
private val GreenColor = Color(0xFF1E8C45)
private val RedColor   = Color(0xFFD32F2F)

private enum class ScanState { SCANNING, MARKING, SUCCESS, ALREADY_MARKED, ERROR }

@Composable
fun StudentScanningPage(
    navController: NavController,
    supabase: SupabaseClient,
    sessionId: String,
    studentId: String,
    studentName: String,
    className: String,
    classId: String       // needed to calculate attendance stats on the confirmed page
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // ── State ──────────────────────────────────────────────────────────────
    var scanState          by remember { mutableStateOf(ScanState.SCANNING) }
    var errorMessage       by remember { mutableStateOf<String?>(null) }
    var markedStatus       by remember { mutableStateOf<CheckInStatus?>(null) }
    var session            by remember { mutableStateOf<SessionRow?>(null) }
    var secondsLeft        by remember { mutableStateOf(0) }
    var permissionsGranted by remember { mutableStateOf(false) } // ← gates the scan

    // ── Permission launcher ────────────────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            permissionsGranted = true   // ← scan will now start
        } else {
            errorMessage = "Bluetooth permissions are required to check in."
            scanState    = ScanState.ERROR
        }
    }

    // Ask immediately when screen opens
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    // ── Fetch session ──────────────────────────────────────────────────────
    LaunchedEffect(sessionId) {
        try {
            val fetched = supabase.postgrest.from("sessions")
                .select { filter { eq("id", sessionId) } }
                .decodeSingle<SessionRow>()

            session = fetched

            val startedAt = parseInstant(fetched.started_at)
            val windowEnd = startedAt.plusSeconds(fetched.time_window * 60L)
            val remaining = java.time.Instant.now()
                .until(windowEnd, java.time.temporal.ChronoUnit.SECONDS)
            secondsLeft   = if (remaining > 0) remaining.toInt() else 0

        } catch (e: Exception) {
            scanState    = ScanState.ERROR
            errorMessage = "Could not load session info: ${e.message}"
        }
    }

    // ── Countdown tick ─────────────────────────────────────────────────────
    LaunchedEffect(scanState) {
        if (scanState == ScanState.SCANNING) {
            while (secondsLeft > 0) {
                delay(1_000L)
                secondsLeft = (secondsLeft - 1).coerceAtLeast(0)
            }
        }
    }

    // ── Start BLE scan — waits for BOTH session loaded + permissions granted
    LaunchedEffect(session, permissionsGranted) {
        val currentSession = session ?: return@LaunchedEffect
        if (!permissionsGranted) return@LaunchedEffect  // don't scan until user taps Allow

        val result = startBleScanning(context) { detectedCode ->
            if (scanState != ScanState.SCANNING) return@startBleScanning
            if (detectedCode != currentSession.session_code) return@startBleScanning

            scope.launch {
                stopBleScanning(context)
                scanState = ScanState.MARKING

                val attendanceResult = markAttendance(
                    supabase   = supabase,
                    sessionId  = sessionId,
                    studentId  = studentId,
                    session    = currentSession
                )

                attendanceResult.fold(
                    onSuccess = { status: CheckInStatus ->
                        markedStatus = status
                        scanState    = ScanState.SUCCESS
                    },
                    onFailure = { err: Throwable ->
                        errorMessage = err.message
                        scanState    = ScanState.ERROR
                    }
                )
            }
        }

        result.onFailure { err ->
            errorMessage = err.message
            scanState    = ScanState.ERROR
        }
    }

    // ── Stop scan when leaving the screen ─────────────────────────────────
    DisposableEffect(Unit) {
        onDispose { stopBleScanning(context) }
    }

    // ── Timer display ──────────────────────────────────────────────────────
    val minutes   = secondsLeft / 60
    val seconds   = secondsLeft % 60
    val timerText = "%02d:%02d".format(minutes, seconds)

    // ── Pulse animation ────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0.80f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "pulseAlpha"
    )
    val dashRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(4000, easing = LinearEasing)
        ), label = "dashRotation"
    )

    // ── UI ─────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = { AppBar(title = "Check In", navController = navController) },
        containerColor = Color(0xFFF5F7FA)
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (scanState) {

                    ScanState.SCANNING, ScanState.MARKING -> {
                        Text(
                            text = if (scanState == ScanState.SCANNING)
                                "Keep Bluetooth on and stay in the classroom"
                            else "Signal detected — marking attendance…",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                            Canvas(modifier = Modifier.size(220.dp)) {
                                drawCircle(
                                    color = BlueColor.copy(alpha = pulseAlpha * 0.25f),
                                    radius = (size.minDimension / 2) * pulseScale,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                            Canvas(modifier = Modifier.size(172.dp)) {
                                drawCircle(
                                    color = BlueMid,
                                    radius = size.minDimension / 2,
                                    style = Stroke(width = 2.5f.dp.toPx())
                                )
                            }
                            Canvas(modifier = Modifier.size(128.dp)) {
                                drawCircle(
                                    color = BlueColor,
                                    radius = size.minDimension / 2,
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        pathEffect = PathEffect.dashPathEffect(
                                            floatArrayOf(14f, 8f), dashRotation
                                        )
                                    )
                                )
                            }
                            Canvas(modifier = Modifier.size(80.dp)) {
                                drawCircle(color = BlueFaint)
                            }
                            if (scanState == ScanState.MARKING) {
                                CircularProgressIndicator(
                                    color = BlueColor,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = "Bluetooth",
                                    tint = BlueColor,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(36.dp))

                        Text(
                            text = if (scanState == ScanState.MARKING) "Marking attendance…"
                            else "Scanning for signal...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Your attendance will be marked automatically\nonce the classroom signal is detected.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }

                    ScanState.SUCCESS -> {
                        // Navigate to the confirmed page — do it once via LaunchedEffect
                        LaunchedEffect(Unit) {
                            val encodedClass = java.net.URLEncoder.encode(className, "UTF-8")
                            navController.navigate(
                                "attendanceConfirmed/$sessionId/$studentId/$classId/$encodedClass"
                            ) {
                                // Remove the scanning screen from the back stack so
                                // pressing back from the confirmed page goes home, not back here
                                popUpTo("studentScanning/{sessionId}/{studentId}/{studentName}/{className}/{classId}") {
                                    inclusive = true
                                }
                            }
                        }
                        // Show a brief spinner while the navigation fires
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GreenColor)
                        }
                    }

                    ScanState.ALREADY_MARKED -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = BlueColor,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Already Checked In",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(40.dp))
                        Button(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back Home")
                        }
                    }

                    ScanState.ERROR -> {
                        Text("⚠️", fontSize = 50.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Check-in Failed",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "Unknown error occurred",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = {
                                scanState    = ScanState.SCANNING
                                errorMessage = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BlueColor)
                        ) {
                            Text("Try Again")
                        }
                        TextButton(onClick = { navController.popBackStack() }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    }
                }
            }

            // ── Top Timer ──────────────────────────────────────────────────
            if (scanState == ScanState.SCANNING || scanState == ScanState.MARKING) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (secondsLeft < 60) Color(0xFFFFEBEE) else Color.White,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Time remaining: ", fontSize = 13.sp, color = Color.Gray)
                        Text(
                            text = timerText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (secondsLeft < 60) RedColor else Color.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
