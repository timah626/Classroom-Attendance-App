package com.example.demogradecalculator


import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

fun readStudentsFromExcel(filePath: String): List<Student> {
    val students = mutableListOf<Student>()
    val workbook = XSSFWorkbook(FileInputStream(File(filePath)))
    val sheet = workbook.getSheetAt(0)

    for (i in 1 until sheet.physicalNumberOfRows) {
        val row = sheet.getRow(i) ?: continue
        val name = when (row.getCell(0)?.cellType) {
            CellType.STRING -> row.getCell(0).stringCellValue
            CellType.NUMERIC -> row.getCell(0).numericCellValue.toInt().toString()
            else -> "Unknown"
        }
        val score = when (row.getCell(1)?.cellType) {
            CellType.NUMERIC -> row.getCell(1).numericCellValue.toInt()
            CellType.STRING -> row.getCell(1).stringCellValue.toIntOrNull()
            else -> null
        }
        students.add(Student(name, score))
    }

    workbook.close()
    return students
}

fun writeResultsToExcel(students: List<Student>, outputPath: String) {
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet("Results")

    val header = sheet.createRow(0)
    header.createCell(0).setCellValue("Name")
    header.createCell(1).setCellValue("Score")
    header.createCell(2).setCellValue("Grade")

    students.forEachIndexed { index, student ->
        val row = sheet.createRow(index + 1)
        row.createCell(0).setCellValue(student.name)
        row.createCell(1).setCellValue(student.score?.toString() ?: "N/A")
        row.createCell(2).setCellValue(student.grade)
    }

    val out = FileOutputStream(File(outputPath))
    workbook.write(out)
    out.close()
    workbook.close()
}