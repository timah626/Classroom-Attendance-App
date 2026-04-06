package com.example.testapplication2



import android.content.Context
import android.net.Uri
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStreamReader

// ─────────────────────────────────────────────────────────────────────────────
// Models
// ─────────────────────────────────────────────────────────────────────────────

data class StudentEntry(
    val name: String,
    val email: String
)

@Serializable
data class UserRow(
    val id: String,
    val email: String
)

@Serializable
data class EnrollmentRow(
    val class_id: String,
    val student_id: String? = null,
    val pending_email: String? = null
)

@Serializable
data class ExistingEnrollment(
    val student_id: String? = null,
    val pending_email: String? = null
)

sealed class ParseResult {
    data class Success(val students: List<StudentEntry>) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

data class EnrollmentSummary(
    val enrolled: Int,      // linked to existing user
    val pending: Int,       // stored with pending_email (no account yet)
    val skipped: Int        // already enrolled, duplicate skipped
)

// ─────────────────────────────────────────────────────────────────────────────
// Function 1 — Parse CSV or Excel file
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reads a CSV or Excel file from the given [uri] and extracts [name] + [email] columns.
 * Returns [ParseResult.Success] with a list of [StudentEntry], or [ParseResult.Error].
 *
 * Dependency required in build.gradle (for Excel support):
 *   implementation("org.apache.poi:poi-ooxml:5.2.3")
 */
fun parseStudentFile(context: Context, uri: Uri): ParseResult {
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri) ?: ""
    val fileName = uri.lastPathSegment ?: ""

    return try {
        val inputStream = contentResolver.openInputStream(uri)
            ?: return ParseResult.Error("Could not open file. Please try again.")

        val students: List<StudentEntry> = when {

            // ── CSV ──────────────────────────────────────────────────────────
            mimeType.contains("csv", ignoreCase = true) ||
                    fileName.endsWith(".csv", ignoreCase = true) -> {

                val reader = BufferedReader(InputStreamReader(inputStream))
                val lines = reader.readLines().filter { it.isNotBlank() }

                if (lines.isEmpty()) {
                    return ParseResult.Error("The file is empty.")
                }

                // Detect header row
                val header = lines.first().split(",").map { it.trim().lowercase() }
                val nameIndex = header.indexOfFirst { it == "name" }
                val emailIndex = header.indexOfFirst { it == "email" }

                if (nameIndex == -1 || emailIndex == -1) {
                    return ParseResult.Error(
                        "Missing columns. File must have 'name' and 'email' headers."
                    )
                }

                val dataRows = lines.drop(1) // skip header
                if (dataRows.isEmpty()) {
                    return ParseResult.Error("File has headers but no student data.")
                }

                dataRows.mapIndexedNotNull { index, line ->
                    val cols = line.split(",").map { it.trim() }
                    if (cols.size <= maxOf(nameIndex, emailIndex)) {
                        null // skip malformed rows silently
                    } else {
                        val name = cols[nameIndex]
                        val email = cols[emailIndex]
                        if (name.isBlank() || email.isBlank()) null
                        else StudentEntry(name = name, email = email)
                    }
                }
            }

            // ── Excel (.xlsx / .xls) ─────────────────────────────────────────
            mimeType.contains("spreadsheet", ignoreCase = true) ||
                    mimeType.contains("excel", ignoreCase = true) ||
                    fileName.endsWith(".xlsx", ignoreCase = true) ||
                    fileName.endsWith(".xls", ignoreCase = true) -> {

                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)
                val rows = sheet.toList()

                if (rows.isEmpty()) {
                    return ParseResult.Error("The Excel file is empty.")
                }

                val headerRow = rows.first()
                val nameIndex = (0 until headerRow.lastCellNum).indexOfFirst {
                    headerRow.getCell(it)?.stringCellValue?.trim()?.lowercase() == "student name"
                }
                val emailIndex = (0 until headerRow.lastCellNum).indexOfFirst {
                    headerRow.getCell(it)?.stringCellValue?.trim()?.lowercase() == "email"
                }

                if (nameIndex == -1 || emailIndex == -1) {
                    return ParseResult.Error(
                        "Missing columns. File must have 'name' and 'email' headers."
                    )
                }

                rows.drop(1).mapNotNull { row ->
                    val name = row.getCell(nameIndex)?.stringCellValue?.trim() ?: ""
                    val email = row.getCell(emailIndex)?.stringCellValue?.trim() ?: ""
                    if (name.isBlank() || email.isBlank()) null
                    else StudentEntry(name = name, email = email)
                }
            }

            else -> return ParseResult.Error(
                "Unsupported file type. Please upload a .csv or .xlsx file."
            )
        }

        inputStream.close()

        if (students.isEmpty()) {
            ParseResult.Error("No valid student rows found in the file.")
        } else {
            ParseResult.Success(students)
        }

    } catch (e: Exception) {
        ParseResult.Error("Failed to read file: ${e.message}")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Function 2 — Bulk enroll students into a class
// ─────────────────────────────────────────────────────────────────────────────

/**
 * For each student in [students]:
 *  - Checks if they already exist in the `users` table (by email)
 *  - If yes → enrolls them with their real student_id
 *  - If no  → creates a pending enrollment row using pending_email
 *  - Skips duplicates in either case
 *
 * Returns an [EnrollmentSummary] with counts of enrolled / pending / skipped.
 */


suspend fun bulkEnrollStudents(
    supabase: SupabaseClient,
    students: List<StudentEntry>,
    classId: String
): Result<EnrollmentSummary> {
    return try {
        var enrolledCount = 0
        var pendingCount = 0
        var skippedCount = 0

        for (student in students) {
            val email = student.email.lowercase().trim()

            // ── 1. Check if user exists in public.users ───────────────────────
            val existingUsers = supabase.postgrest["users"]
                .select(Columns.list("id", "email")) {
                    filter { eq("email", email) }
                }
                .decodeList<UserRow>()

            val existingUser = existingUsers.firstOrNull()

            if (existingUser != null) {
                // ── 2a. User exists — check if already enrolled by student_id ─
                val alreadyEnrolledById = supabase.postgrest["enrollments"]
                    .select(Columns.list("student_id", "pending_email")) {
                        filter {
                            eq("class_id", classId)
                            eq("student_id", existingUser.id)
                        }
                    }
                    .decodeList<ExistingEnrollment>()

                // ── 2b. Also check if already enrolled as pending email ────────
                // This handles the case where the same email was uploaded before
                // the student had an account
                val alreadyEnrolledByEmail = supabase.postgrest["enrollments"]
                    .select(Columns.list("student_id", "pending_email")) {
                        filter {
                            eq("class_id", classId)
                            eq("pending_email", email)
                        }
                    }
                    .decodeList<ExistingEnrollment>()

                if (alreadyEnrolledById.isNotEmpty()) {
                    // Already properly enrolled — skip
                    skippedCount++
                    continue
                }

                if (alreadyEnrolledByEmail.isNotEmpty()) {
                    // Was pending before — now upgrade to real enrollment
                    supabase.postgrest["enrollments"]
                        .update(
                            mapOf(
                                "student_id"    to existingUser.id,
                                "pending_email" to null
                            )
                        ) {
                            filter {
                                eq("class_id", classId)
                                eq("pending_email", email)
                            }
                        }
                    enrolledCount++
                    continue
                }

                // ── 2c. Not enrolled at all — enroll with real student_id ──────
                supabase.postgrest["enrollments"].insert(
                    EnrollmentRow(
                        class_id      = classId,
                        student_id    = existingUser.id,
                        pending_email = null
                    )
                )
                enrolledCount++

            } else {
                // ── 3a. No account yet — check for existing pending enrollment ─
                val alreadyPending = supabase.postgrest["enrollments"]
                    .select(Columns.list("student_id", "pending_email")) {
                        filter {
                            eq("class_id", classId)
                            eq("pending_email", email)
                        }
                    }
                    .decodeList<ExistingEnrollment>()

                if (alreadyPending.isNotEmpty()) {
                    skippedCount++
                    continue
                }

                // ── 3b. Insert pending enrollment row ─────────────────────────
                supabase.postgrest["enrollments"].insert(
                    EnrollmentRow(
                        class_id      = classId,
                        student_id    = null,
                        pending_email = email
                    )
                )
                pendingCount++
            }
        }

        Result.success(
            EnrollmentSummary(
                enrolled = enrolledCount,
                pending  = pendingCount,
                skipped  = skippedCount
            )
        )

    } catch (e: Exception) {
        Result.failure(Exception("Enrollment failed: ${e.message}"))
    }
}





/*suspend fun bulkEnrollStudents(
    supabase: SupabaseClient,
    students: List<StudentEntry>,
    classId: String
): Result<EnrollmentSummary> {
    return try {
        var enrolledCount = 0
        var pendingCount = 0
        var skippedCount = 0

        for (student in students) {
            val email = student.email.lowercase().trim()

            // ── 1. Check if user exists ───────────────────────────────────────
            val existingUsers = supabase.postgrest["users"]
                .select(Columns.list("id", "email")) {
                    filter {
                        eq("email", email)
                    }
                }
                .decodeList<UserRow>()

            val existingUser = existingUsers.firstOrNull()

            if (existingUser != null) {
                // ── 2a. User exists — check if already enrolled ───────────────
                val alreadyEnrolled = supabase.postgrest["enrollments"]
                    .select(Columns.list("student_id", "pending_email")) {
                        filter {
                            eq("class_id", classId)
                            eq("student_id", existingUser.id)
                        }
                    }
                    .decodeList<ExistingEnrollment>()

                if (alreadyEnrolled.isNotEmpty()) {
                    skippedCount++
                    continue
                }

                // ── 2b. Enroll with real student_id ───────────────────────────
                supabase.postgrest["enrollments"].insert(
                    EnrollmentRow(
                        class_id = classId,
                        student_id = existingUser.id,
                        pending_email = null
                    )
                )
                enrolledCount++

            } else {
                // ── 3a. No account yet — check for existing pending enrollment ─
                val alreadyPending = supabase.postgrest["enrollments"]
                    .select(Columns.list("student_id", "pending_email")) {
                        filter {
                            eq("class_id", classId)
                            eq("pending_email", email)
                        }
                    }
                    .decodeList<ExistingEnrollment>()

                if (alreadyPending.isNotEmpty()) {
                    skippedCount++
                    continue
                }

                // ── 3b. Insert pending enrollment row ─────────────────────────
                supabase.postgrest["enrollments"].insert(
                    EnrollmentRow(
                        class_id = classId,
                        student_id = null,
                        pending_email = email
                    )
                )
                pendingCount++
            }
        }

        Result.success(
            EnrollmentSummary(
                enrolled = enrolledCount,
                pending = pendingCount,
                skipped = skippedCount
            )
        )

    } catch (e: Exception) {
        Result.failure(Exception("Enrollment failed: ${e.message}"))
    }
} */