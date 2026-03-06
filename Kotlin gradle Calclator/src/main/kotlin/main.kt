import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Grade Calculator Application
 * 
 * This application:
 * 1. Reads student names and scores from an Excel file
 * 2. Calculates letter grades for each student
 * 3. Outputs results to a new Excel file (results.xlsx)
 * 4. Displays a formatted preview in the console
 */

fun main() {
    // Step 1: Ask user for Excel file path
    print("Enter the name of your Excel file (e.g., students.xlsx): ")
    val filename = readLine() ?: return
    
    // Step 2: Validate file exists
    val file = File(filename)
    if (!file.exists()) {
        println("Error: File '$filename' not found in this folder!")
        return
    }
    
    // Step 3: Read Excel file
    val inputStream = FileInputStream(file)
    val workbook = XSSFWorkbook(inputStream)
    val sheet = workbook.getSheetAt(0)
    
    // Step 4: Create result Excel file
    val resultWorkbook = XSSFWorkbook()
    val resultSheet = resultWorkbook.createSheet("Sheet1")
    
    // Add header row to result
    val headerRow = resultSheet.createRow(0)
    headerRow.createCell(0).setCellValue("Name")
    headerRow.createCell(1).setCellValue("Mark")
    headerRow.createCell(2).setCellValue("Grade")
    
    // Step 5: Display console header
    println("\n--- PREVIEW OF GENERATED GRADES ---")
    println("${"Name".padEnd(15)} | ${"Mark".padEnd(5)} | Grade")
    println("-".repeat(30))
    
    // Step 6: Process each row from input Excel
    var resultRowNum = 1
    for (i in 1 until sheet.physicalNumberOfRows) {
        val row = sheet.getRow(i) ?: continue
        
        // Extract name from first column
        val nameCell = row.getCell(0)
        val name = getCellStringValue(nameCell) ?: "Unknown"
        
        // Extract score from second column
        val scoreCell = row.getCell(1)
        val score = getCellIntValue(scoreCell)
        
        // Create Student object (grade is calculated automatically)
        val student = Student(name, score)
        
        // Display in console with formatting
        val scoreDisplay = score?.toString() ?: "N/A"
        println("${student.name.padEnd(15)} | ${scoreDisplay.padEnd(5)} | ${student.grade}")
        
        // Write to result Excel file
        val resultRow = resultSheet.createRow(resultRowNum)
        resultRow.createCell(0).setCellValue(student.name)
        resultRow.createCell(1).setCellValue(scoreDisplay)
        resultRow.createCell(2).setCellValue(student.grade)
        
        resultRowNum++
    }
    
    // Step 7: Save result Excel file
    val resultFile = File("results.xlsx")
    val outputStream = FileOutputStream(resultFile)
    resultWorkbook.write(outputStream)
    outputStream.close()
    resultWorkbook.close()
    
    // Step 8: Close input workbook
    inputStream.close()
    workbook.close()
    
    println("\n[SUCCESS] 'results.xlsx' created!")
}

/**

 */
fun getCellStringValue(cell: Cell?): String? {
    if (cell == null) return null
    
    return when (cell.cellType) {
        CellType.STRING -> cell.stringCellValue
        CellType.NUMERIC -> cell.numericCellValue.toInt().toString()
        else -> cell.toString()
    }
}

/**
 */
fun getCellIntValue(cell: Cell?): Int? {
    if (cell == null) return null
    
    return when (cell.cellType) {
        CellType.NUMERIC -> cell.numericCellValue.toInt()
        CellType.STRING -> cell.stringCellValue.toIntOrNull()
        else -> null
    }
}
