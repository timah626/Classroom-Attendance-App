package com.example.testapplication2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.testapplication2.ui.theme.TestApplication2Theme
import com.example.testapplication2.authUI.*
import com.example.testapplication2.studentUI.*
import com.example.testapplication2.teacherUI.*
import com.example.testapplication2.repositories.fetchConfirmedStats

import io.github.jan.supabase.auth.auth






class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            TestApplication2Theme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "landing") {
                    composable("landing") { LandingPage(navController) }
                    composable("login") { LoginScreen(navController) }
                    composable("signup") { CreateAccountPage(navController) }

                    composable("studenthome") {
                        val currentUserId = supabase.auth.currentUserOrNull()?.id ?: ""
                        StudentHomePage(
                            navController = navController,
                            supabase = supabase,
                            studentId = currentUserId
                        )
                    }

                    composable("myclasses") { MyClassesPage(navController) }

                    composable(
                        route = "classDetail/{classId}",
                        arguments = listOf(navArgument("classId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val classId = backStackEntry.arguments?.getString("classId") ?: ""
                        ClassDetailPage(classId = classId, navController = navController)
                    }

                    composable("upload/{classId}") { backStackEntry ->
                        val classId = backStackEntry.arguments?.getString("classId") ?: ""
                        UploadDocumentPage(navController, supabase, classId)
                    }

                    composable(
                        "session/{sessionId}/{classId}/{className}",
                        arguments = listOf(
                            navArgument("sessionId") { type = NavType.StringType },
                            navArgument("classId") { type = NavType.StringType },
                            navArgument("className") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                        val classId = backStackEntry.arguments?.getString("classId") ?: ""
                        val className = backStackEntry.arguments?.getString("className") ?: ""
                        TeacherActiveSessionPage(
                            navController = navController,
                            supabase = supabase,
                            sessionId = sessionId,
                            classId = classId,
                            className = className
                        )
                    }

                    composable("studentScanning/{sessionId}/{studentId}/{studentName}/{className}/{classId}") { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                        val studentId = backStackEntry.arguments?.getString("studentId") ?: ""

                        val studentName = java.net.URLDecoder.decode(
                            backStackEntry.arguments?.getString("studentName") ?: "", "UTF-8"
                        )
                        val className = java.net.URLDecoder.decode(
                            backStackEntry.arguments?.getString("className") ?: "", "UTF-8"
                        )
                        val classId = backStackEntry.arguments?.getString("classId") ?: ""

                        StudentScanningPage(
                            navController = navController,
                            supabase = supabase,
                            sessionId = sessionId,
                            studentId = studentId,
                            studentName = studentName,
                            className = className,
                            classId = classId
                        )
                    }

                    composable("studentHistory/{studentId}/{className}") { back ->
                        StudentAttendanceHistoryPage(
                            navController = navController,
                            supabase      = supabase,
                            studentId     = back.arguments?.getString("studentId") ?: "",
                            className     = java.net.URLDecoder.decode(
                                back.arguments?.getString("className") ?: "", "UTF-8"
                            )
                        )
                    }

                    composable("createClass") { CreateClassPage(navController = navController) }
                    composable("attendanceConfirmed/{sessionId}/{studentId}/{classId}/{className}") { back ->
                        AttendanceConfirmedPage(
                            navController = navController,
                            supabase      = supabase,
                            sessionId     = back.arguments?.getString("sessionId") ?: "",
                            studentId     = back.arguments?.getString("studentId") ?: "",
                            classId       = back.arguments?.getString("classId") ?: "",
                            className     = java.net.URLDecoder.decode(
                                back.arguments?.getString("className") ?: "", "UTF-8"
                            ),
                            fetchConfirmedStats = ::fetchConfirmedStats  // your repository function
                        )
                    }

                    composable("sessionHistory/{classId}/{className}") { back ->
                        SessionHistoryPage(
                            navController = navController,
                            supabase      = supabase,
                            classId       = back.arguments?.getString("classId") ?: "",
                            className     = java.net.URLDecoder.decode(
                                back.arguments?.getString("className") ?: "", "UTF-8"
                            )
                        )
                    }

                    composable("sessionDetail/{sessionId}/{classId}/{sessionDate}") { back ->
                        SessionDetailPage(
                            navController = navController,
                            supabase      = supabase,
                            sessionId     = back.arguments?.getString("sessionId") ?: "",
                            classId       = back.arguments?.getString("classId") ?: "",
                            sessionDate   = java.net.URLDecoder.decode(
                                back.arguments?.getString("sessionDate") ?: "", "UTF-8"
                            )
                        )
                    }

                    composable("debug") {
                        val currentUserId = supabase.auth.currentUserOrNull()?.id ?: ""
                        DebugScreen(
                            navController = navController,
                            supabase = supabase,
                            studentId = currentUserId
                        )
                    }
                    composable("teacheractive") {
                        TeacherActiveSessionPage(
                            navController = navController,
                            supabase = supabase,
                            sessionId = "",
                            classId = "" ,
                            //className = ""
                        )
                    }
                }
            }
        }
    }
}
