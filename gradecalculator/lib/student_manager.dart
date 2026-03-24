// ─────────────────────────────────────────────────────────────
//  student_manager.dart  –  Business logic for SE 3242 Milestone 2
//  Concepts used (per Class02 slides):
//    • Higher-order functions  (Slide 15)
//    • map, filter, fold / reduce  (Slide 16)
//    • Lambdas passed as arguments  (Slide 14)
//    • Default & named arguments  (Slide 21)
//    • Null safety                  (Week 1 recap)
// ─────────────────────────────────────────────────────────────

import 'package:excel/excel.dart';
import 'student.dart';

class StudentManager {
  // ── Internal mutable collection  (Slide 11 – mutable list) ──
  final List<Student> _students = [];

  List<Student> get students => List.unmodifiable(_students);

  // ── Basic CRUD ───────────────────────────────────────────────

  void addStudent(String name, double grade) {
    _students.add(Student(name: name, grade: grade));
  }

  void clearStudents() => _students.clear();

  // ── Higher-order query functions  (Slide 15) ─────────────────

  List<Student> filterStudents(bool Function(Student) predicate) =>
      _students.where(predicate).toList();

  List<T> transformStudents<T>(T Function(Student) transform) =>
      _students.map(transform).toList();

  // ── Aggregate calculations ────────────────────────────────────
  // Only graded students count toward the average

  double calculateAverage() {
    final graded = _students.where((s) => s.hasGrade).toList();
    if (graded.isEmpty) return 0.0;
    final double total =
    graded.map((s) => s.grade!).reduce((a, b) => a + b);
    return total / graded.length;
  }

  int countPassing() =>
      filterStudents((s) => s.hasGrade && s.grade! >= 50).length;

  int countFailing() =>
      filterStudents((s) => s.hasGrade && s.grade! < 50).length;

  int countIncomplete() =>
      filterStudents((s) => !s.hasGrade).length;

  List<Student> topStudents({int n = 3}) {
    final sorted = [..._students.where((s) => s.hasGrade)]
      ..sort((a, b) => b.grade!.compareTo(a.grade!));
    return sorted.take(n).toList();
  }

  // ── Excel import  ────────────────────────────────────────────
  // Students with an empty/missing mark cell are kept as incomplete
  // (grade = null) rather than being dropped entirely.

  List<Student> importFromExcelBytes(List<int> bytes) {
    clearStudents();

    final excel = Excel.decodeBytes(bytes);
    final sheet = excel.tables.values.first;

    // fold over rows, skipping the header  (Slide 16 – fold)
    final imported = sheet.rows.skip(1).fold<List<Student>>([], (acc, row) {
      if (row.isEmpty) return acc;

      final nameCel = row[0]?.value;
      if (nameCel == null) return acc;

      final name = nameCel.toString().trim();
      if (name.isEmpty) return acc;

      // Grade is optional – null means the cell was blank
      final gradeCel = row.length > 1 ? row[1]?.value : null;
      final grade = gradeCel != null
          ? double.tryParse(gradeCel.toString().trim())
          : null;

      acc.add(Student(name: name, grade: grade));
      return acc;
    });

    imported.forEach((s) => _students.add(s));
    return imported;
  }

  // ── Excel export  ────────────────────────────────────────────
  // Returns both the byte data AND a suggested filename with a
  // timestamp so the file is never blocked by a previous open copy.

  ({List<int> bytes, String filename}) exportToExcel() {
    final excel = Excel.createExcel();

    const sheetName = 'Results';
    final sheet = excel[sheetName];
    excel.setDefaultSheet(sheetName);
    excel.delete('Sheet1');

    // ── Column widths ────────────────────────────────────────
    sheet.setColumnWidth(0, 22); // Name
    sheet.setColumnWidth(1, 10); // Mark
    sheet.setColumnWidth(2, 14); // Letter Grade
    sheet.setColumnWidth(3, 14); // Status

    // ── Reusable style builders ───────────────────────────────

    // Navy header style
    CellStyle headerStyle() => CellStyle(
      bold: true,
      fontColorHex: ExcelColor.fromHexString('#FFFFFF'),
      backgroundColorHex: ExcelColor.fromHexString('#1A3A6B'),
      horizontalAlign: HorizontalAlign.Center,
      verticalAlign: VerticalAlign.Center,
      leftBorder: Border(borderStyle: BorderStyle.Thin),
      rightBorder: Border(borderStyle: BorderStyle.Thin),
      topBorder: Border(borderStyle: BorderStyle.Thin),
      bottomBorder: Border(borderStyle: BorderStyle.Thin),
    );

    // Standard data cell
    CellStyle dataStyle({
      bool center = false,
      bool bold = false,
      String? bgHex,
      String? fgHex,
    }) =>
        CellStyle(
          bold: bold,
          fontColorHex: fgHex != null
              ? ExcelColor.fromHexString(fgHex)
              : ExcelColor.fromHexString('#000000'),
          backgroundColorHex: bgHex != null
              ? ExcelColor.fromHexString(bgHex)
              : ExcelColor.fromHexString('#FFFFFF'),
          horizontalAlign:
          center ? HorizontalAlign.Center : HorizontalAlign.Left,
          verticalAlign: VerticalAlign.Center,
          leftBorder: Border(borderStyle: BorderStyle.Thin),
          rightBorder: Border(borderStyle: BorderStyle.Thin),
          topBorder: Border(borderStyle: BorderStyle.Thin),
          bottomBorder: Border(borderStyle: BorderStyle.Thin),
        );

    // Footer / average row style
    CellStyle footerStyle() => CellStyle(
      bold: true,
      backgroundColorHex: ExcelColor.fromHexString('#D9E1F2'),
      fontColorHex: ExcelColor.fromHexString('#1A3A6B'),
      horizontalAlign: HorizontalAlign.Center,
      verticalAlign: VerticalAlign.Center,
      leftBorder: Border(borderStyle: BorderStyle.Medium),
      rightBorder: Border(borderStyle: BorderStyle.Medium),
      topBorder: Border(borderStyle: BorderStyle.Medium),
      bottomBorder: Border(borderStyle: BorderStyle.Medium),
    );

    // ── Helper: write a styled cell ──────────────────────────
    void writeCell(Sheet s, int row, int col, CellValue value,
        CellStyle style) {
      final cell = s.cell(CellIndex.indexByColumnRow(
          columnIndex: col, rowIndex: row));
      cell.value = value;
      cell.cellStyle = style;
    }

    // ── Row 0: Header ─────────────────────────────────────────
    const headers = ['Name', 'Mark', 'Letter Grade', 'Status'];
    for (var c = 0; c < headers.length; c++) {
      writeCell(sheet, 0, c, TextCellValue(headers[c]), headerStyle());
    }

    // ── Rows 1…N: Student data  (map lambda – Slide 12) ──────
    _students.asMap().forEach((i, s) {
      final row = i + 1;

      // Row background: green tint = passed, red = failed, yellow = incomplete
      final String rowBg = s.status == 'PASSED'
          ? '#E2EFDA'
          : s.status == 'INCOMPLETE'
          ? '#FFF2CC'
          : '#FCE4D6';

      // Status text colour
      final String statusFg = s.status == 'PASSED'
          ? '#375623'
          : s.status == 'INCOMPLETE'
          ? '#7F6000'
          : '#9C0006';

      // Letter grade colour
      final String gradeFg = s.letterGrade == 'N/A' || s.letterGrade == 'F'
          ? '#9C0006'
          : '#375623';

      writeCell(sheet, row, 0, TextCellValue(s.name),
          dataStyle(bgHex: rowBg));
      writeCell(
          sheet,
          row,
          1,
          s.hasGrade ? DoubleCellValue(s.grade!) : TextCellValue('N/A'),
          dataStyle(center: true, bgHex: rowBg));
      writeCell(sheet, row, 2, TextCellValue(s.letterGrade),
          dataStyle(center: true, bold: true, bgHex: rowBg, fgHex: gradeFg));
      writeCell(sheet, row, 3, TextCellValue(s.status),
          dataStyle(center: true, bold: true, bgHex: rowBg, fgHex: statusFg));
    });

    // ── Footer: class average  (fold – Slide 16) ─────────────
    final footerRow = _students.length + 1;
    final graded = _students.where((s) => s.hasGrade).length;
    writeCell(sheet, footerRow, 0,
        TextCellValue('CLASS AVERAGE  (\$graded/\${_students.length} graded)'),
        footerStyle());
    writeCell(
        sheet,
        footerRow,
        1,
        DoubleCellValue(
            double.parse(calculateAverage().toStringAsFixed(2))),
        footerStyle());
    writeCell(sheet, footerRow, 2, TextCellValue(''), footerStyle());
    writeCell(sheet, footerRow, 3, TextCellValue(''), footerStyle());

    // ── Timestamp filename → never clashes with open file ────
    final ts = DateTime.now();
    final stamp =
        '\${ts.year}\${_pad(ts.month)}\${_pad(ts.day)}_\${_pad(ts.hour)}\${_pad(ts.minute)}\${_pad(ts.second)}';
    final filename = 'grades_results_\$stamp.xlsx';

    return (bytes: excel.encode() ?? [], filename: filename);
  }

  String _pad(int n) => n.toString().padLeft(2, '0');
}