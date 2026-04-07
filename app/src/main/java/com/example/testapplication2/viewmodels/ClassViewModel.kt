package com.example.testapplication2.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testapplication2.models.SessionRow
import com.example.testapplication2.repositories.SessionRepository
import com.example.testapplication2.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Data classes ───────────────────────────────────────────────────────────

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

// ── ViewModel ──────────────────────────────────────────────────────────────

class ClassViewModel : ViewModel() {

    private val sessionRepository = SessionRepository()

    var isLoading         by mutableStateOf(false)
    var isStartingSession by mutableStateOf(false)
    var errorMessage      by mutableStateOf<String?>(null)
    var classes           by mutableStateOf<List<ClassRecord>>(emptyList())
    var selectedClass     by mutableStateOf<ClassDetail?>(null)
    var studentCount      by mutableStateOf(0)
    var sessionCount      by mutableStateOf(0)
    var avgAttendance     by mutableStateOf(0)

    // ── Start session ──────────────────────────────────────────────────────
    fun startSession(
        classId: String,
        onSuccess: (SessionRow) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            isStartingSession = true
            val result = sessionRepository.startSession(classId, 10)
            isStartingSession = false
            result.fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Failed to start session") }
            )
        }
    }

    // ── Create class ───────────────────────────────────────────────────────
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

    // ── Fetch teacher's classes ────────────────────────────────────────────
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

    // ── Fetch class detail + stats ─────────────────────────────────────────
    fun fetchClassDetail(classId: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                selectedClass = supabase.from("classes")
                    .select(Columns.ALL) { filter { eq("id", classId) } }
                    .decodeSingle<ClassDetail>()

                val enrollments = supabase.from("enrollments")
                    .select(Columns.ALL) { filter { eq("class_id", classId) } }
                    .decodeList<EnrollmentCount>()
                studentCount = enrollments.size

                val sessions = supabase.from("sessions")
                    .select(Columns.ALL) { filter { eq("class_id", classId) } }
                    .decodeList<SessionRecord>()
                sessionCount = sessions.size

                if (sessions.isNotEmpty()) {
                    val sessionIds = sessions.map { it.id }
                    val attendanceRecords = supabase.from("attendance")
                        .select(Columns.ALL) { filter { isIn("session_id", sessionIds) } }
                        .decodeList<AttendanceRecord>()

                    val presentCount  = attendanceRecords.count { it.status == "present" }
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