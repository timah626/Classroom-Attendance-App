package com.example.demogradecalculator

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


fun readStudentsFromExcel(filePath: String): List<Student> {
    val students = mutableListOf<Student>()
    val workbook = XSSFWorkbook(FileInputStream(File(filePath)))
    val sheet = workbook.getSheetAt(0)

    if (sheet.physicalNumberOfRows < 2) {
        workbook.close()
        throw Exception("The Excel file has no student data.")
    }

    val headerRow = sheet.getRow(0)
    if (headerRow == null || headerRow.physicalNumberOfCells < 3) {
        workbook.close()
        throw Exception("Invalid format. Expected columns: Name, CA Mark, Exam Mark.")
    }

    for (i in 1 until sheet.physicalNumberOfRows) {
        val row = sheet.getRow(i) ?: continue

        val name = when (row.getCell(0)?.cellType) {
            CellType.STRING -> row.getCell(0).stringCellValue
            CellType.NUMERIC -> row.getCell(0).numericCellValue.toInt().toString()
            else -> throw Exception("Row ${i + 1}: Name is missing or invalid.")
        }

        val caMark = when (row.getCell(1)?.cellType) {
            CellType.NUMERIC -> row.getCell(1).numericCellValue.toInt().also {
                if (it !in 0..30) throw Exception("Row ${i + 1}: CA mark must be between 0 and 30.")
            }
            CellType.STRING -> row.getCell(1).stringCellValue.toIntOrNull()
            else -> null
        }

        val examMark = when (row.getCell(2)?.cellType) {
            CellType.NUMERIC -> row.getCell(2).numericCellValue.toInt().also {
                if (it !in 0..70) throw Exception("Row ${i + 1}: Exam mark must be between 0 and 70.")
            }
            CellType.STRING -> row.getCell(2).stringCellValue.toIntOrNull()
            else -> null
        }

        students.add(Student(name, examMark, caMark))
    }

    workbook.close()
    return students
}
fun writeResultsToExcel(students: List<Student>, outputPath: String) {
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet("Results")

    val header = sheet.createRow(0)
    header.createCell(0).setCellValue("Name")
    header.createCell(1).setCellValue("CA Mark (/30)")
    header.createCell(2).setCellValue("Exam Mark (/70)")
    header.createCell(3).setCellValue("Total Score (/100)")
    header.createCell(4).setCellValue("Grade")

    students.forEachIndexed { index, student ->
        val row = sheet.createRow(index + 1)
        row.createCell(0).setCellValue(student.name)
        row.createCell(1).setCellValue(student.ca_mark?.toString() ?: "N/A")
        row.createCell(2).setCellValue(student.exam_mark?.toString() ?: "N/A")
        row.createCell(3).setCellValue(student.score.toString())
        row.createCell(4).setCellValue(student.grade)
    }

    val out = FileOutputStream(File(outputPath))
    workbook.write(out)
    out.close()
    workbook.close()
}