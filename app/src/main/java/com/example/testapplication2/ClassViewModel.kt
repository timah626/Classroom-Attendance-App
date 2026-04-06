package com.example.testapplication2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Data classes matching your Supabase schema ──────────────────
@Serializable
data class ClassRecord(
    val id: String = "",
    @SerialName("teacher_id") val teacherId: String = "",
    val name: String = "",
    val description: String? = null,
    val department: String? = null,
    @SerialName("academic_year") val academicYear: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class ClassDetail(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val department: String? = null,
    @SerialName("academic_year") val academicYear: String = "",
    @SerialName("teacher_id") val teacherId: String = ""
)

@Serializable
data class EnrollmentCount(
    @SerialName("class_id") val classId: String = ""
)

@Serializable
data class SessionRecord(
    val id: String = "",
    @SerialName("class_id") val classId: String = "",
    val status: String = ""
)

@Serializable
data class AttendanceRecord(
    val id: String = "",
    @SerialName("session_id") val sessionId: String = "",
    val status: String = ""
)
class ClassViewModel : ViewModel() {

    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var classes by mutableStateOf<List<ClassRecord>>(emptyList())

    // ── Create a new class ──────────────────────────────────────
    fun createClass(
        name: String,
        description: String,
        department: String,
        academicYear: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val user = supabase.auth.currentUserOrNull()
                    ?: throw Exception("Not logged in")

                supabase.from("classes").insert(
                    mapOf(
                        "teacher_id"    to user.id,
                        "name"          to name,
                        "description"   to description.ifBlank { null },
                        "department"    to department.ifBlank { null },
                        "academic_year" to academicYear
                    )
                )
                onSuccess()
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to create class"
            } finally {
                isLoading = false
            }
        }
    }

    // ── Fetch all classes for the logged-in teacher ─────────────
    fun fetchMyClasses() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val user = supabase.auth.currentUserOrNull()
                    ?: throw Exception("Not logged in")

                classes = supabase.from("classes")
                    .select(Columns.ALL) {
                        filter { eq("teacher_id", user.id) }
                    }
                    .decodeList<ClassRecord>()

            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to load classes"
            } finally {
                isLoading = false
            }
        }
    }

    var selectedClass by mutableStateOf<ClassDetail?>(null)
    var studentCount  by mutableStateOf(0)
    var sessionCount  by mutableStateOf(0)
    var avgAttendance by mutableStateOf(0)

    fun fetchClassDetail(classId: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                // 1. Fetch class info
                selectedClass = supabase.from("classes")
                    .select(Columns.ALL) {
                        filter { eq("id", classId) }
                    }
                    .decodeSingle<ClassDetail>()

                // 2. Count enrolled students
                val enrollments = supabase.from("enrollments")
                    .select(Columns.ALL) {
                        filter { eq("class_id", classId) }
                    }
                    .decodeList<EnrollmentCount>()
                studentCount = enrollments.size

                // 3. Count sessions
                val sessions = supabase.from("sessions")
                    .select(Columns.ALL) {
                        filter { eq("class_id", classId) }
                    }
                    .decodeList<SessionRecord>()
                sessionCount = sessions.size

                // 4. Calculate avg attendance across all sessions
                if (sessions.isNotEmpty()) {
                    val sessionIds = sessions.map { it.id }
                    val attendanceRecords = supabase.from("attendance")
                        .select(Columns.ALL) {
                            filter { isIn("session_id", sessionIds) }
                        }
                        .decodeList<AttendanceRecord>()

                    val presentCount = attendanceRecords.count { it.status == "present" }
                    val totalExpected = sessionCount * studentCount

                    avgAttendance = if (totalExpected > 0)
                        ((presentCount.toFloat() / totalExpected) * 100).toInt()
                    else 0
                }

            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to load class details"
            } finally {
                isLoading = false
            }
        }
    }
}