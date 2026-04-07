package com.example.testapplication2.repositories

import com.example.testapplication2.models.*

import android.content.Context
import android.net.Uri
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStreamReader

class EnrollmentRepository(
    private val supabase: SupabaseClient,
    private val context: Context
) {

    /**
     * Reads a CSV or Excel file from the given [uri] and extracts name + email columns.
     * Returns [ParseResult.Success] with a list of [StudentEntry], or [ParseResult.Error].
     *
     * Dependency required in build.gradle (for Excel support):
     *   implementation("org.apache.poi:poi-ooxml:5.2.3")
     */
    fun parseStudentFile(uri: Uri): ParseResult {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: ""
        val fileName = uri.lastPathSegment ?: ""

        return try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: return ParseResult.Error("Could not open file. Please try again.")

            val students: List<StudentEntry> = when {

                // ── CSV ──────────────────────────────────────────────────────
                mimeType.contains("csv", ignoreCase = true) ||
                        fileName.endsWith(".csv", ignoreCase = true) -> {

                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val lines = reader.readLines().filter { it.isNotBlank() }

                    if (lines.isEmpty()) return ParseResult.Error("The file is empty.")

                    val header = lines.first().split(",").map { it.trim().lowercase() }
                    val nameIndex = header.indexOfFirst { it == "name" }
                    val emailIndex = header.indexOfFirst { it == "email" }

                    if (nameIndex == -1 || emailIndex == -1) {
                        return ParseResult.Error(
                            "Missing columns. File must have 'name' and 'email' headers."
                        )
                    }

                    val dataRows = lines.drop(1)
                    if (dataRows.isEmpty()) {
                        return ParseResult.Error("File has headers but no student data.")
                    }

                    dataRows.mapIndexedNotNull { _, line ->
                        val cols = line.split(",").map { it.trim() }
                        if (cols.size <= maxOf(nameIndex, emailIndex)) {
                            null
                        } else {
                            val name = cols[nameIndex]
                            val email = cols[emailIndex]
                            if (name.isBlank() || email.isBlank()) null
                            else StudentEntry(name = name, email = email)
                        }
                    }
                }

                // ── Excel (.xlsx / .xls) ──────────────────────────────────────
                mimeType.contains("spreadsheet", ignoreCase = true) ||
                        mimeType.contains("excel", ignoreCase = true) ||
                        fileName.endsWith(".xlsx", ignoreCase = true) ||
                        fileName.endsWith(".xls", ignoreCase = true) -> {

                    val workbook = WorkbookFactory.create(inputStream)
                    val sheet = workbook.getSheetAt(0)
                    val rows = sheet.toList()

                    if (rows.isEmpty()) return ParseResult.Error("The Excel file is empty.")

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
        students: List<StudentEntry>,
        classId: String
    ): Result<EnrollmentSummary> {
        return try {
            var enrolledCount = 0
            var pendingCount = 0
            var skippedCount = 0

            for (student in students) {
                val email = student.email.lowercase().trim()

                // ── 1. Check if user exists in public.users ───────────────────
                val existingUser = supabase.postgrest["users"]
                    .select(Columns.list("id", "email")) {
                        filter { eq("email", email) }
                    }
                    .decodeList<UserRow>()
                    .firstOrNull()

                if (existingUser != null) {
                    // ── 2a. Check if already enrolled by student_id ───────────
                    val alreadyEnrolledById = supabase.postgrest["enrollments"]
                        .select(Columns.list("student_id", "pending_email")) {
                            filter {
                                eq("class_id", classId)
                                eq("student_id", existingUser.id)
                            }
                        }
                        .decodeList<ExistingEnrollment>()

                    // ── 2b. Check if already enrolled as pending email ─────────
                    // Handles the case where the same email was uploaded before
                    // the student had an account
                    val alreadyEnrolledByEmail = supabase.postgrest["enrollments"]
                        .select(Columns.list("student_id", "pending_email")) {
                            filter {
                                eq("class_id", classId)
                                eq("pending_email", email)
                            }
                        }
                        .decodeList<ExistingEnrollment>()

                    when {
                        alreadyEnrolledById.isNotEmpty() -> {
                            // Already properly enrolled — skip
                            skippedCount++
                        }
                        alreadyEnrolledByEmail.isNotEmpty() -> {
                            // Was pending before — upgrade to real enrollment
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
                        }
                        else -> {
                            // Not enrolled at all — enroll with real student_id
                            supabase.postgrest["enrollments"].insert(
                                EnrollmentRow(
                                    class_id      = classId,
                                    student_id    = existingUser.id,
                                    pending_email = null
                                )
                            )
                            enrolledCount++
                        }
                    }

                } else {
                    // ── 3a. Check for existing pending enrollment ─────────────
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

                    // ── 3b. Insert pending enrollment row ─────────────────────
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
}

/**
 * Top-level wrapper for UploadDocumentPage
 */
fun parseStudentFile(context: Context, uri: Uri): ParseResult {
    // Note: EnrollmentRepository currently needs a SupabaseClient which we don't have here easily
    // but the parse function only uses the context.
    // For a quick fix, we'll instantiate it with a dummy client or just use a helper.
    // Ideally, the parse logic should be moved to a helper or the repository should be injected.
    return EnrollmentRepository(com.example.testapplication2.supabase, context).parseStudentFile(uri)
}

/**
 * Top-level wrapper for UploadDocumentPage
 */
suspend fun bulkEnrollStudents(
    supabase: SupabaseClient,
    students: List<StudentEntry>,
    classId: String
): Result<EnrollmentSummary> {
    // This is just a dummy context, the bulkEnroll function doesn't use it.
    // In a real app, you'd pass the actual context or use a proper DI.
    val dummyContext = com.example.testapplication2.MainActivity() 
    return EnrollmentRepository(supabase, dummyContext).bulkEnrollStudents(students, classId)
}
