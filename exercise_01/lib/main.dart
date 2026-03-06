import 'dart:io';
import 'package:excel/excel.dart';

// Requirement: Data Model with OOP logic (Slide 17) [cite: 178]
class Student {
  final String name;
  final int? score; // Nullable type (Slide 2) [cite: 19]

  Student(this.name, this.score);

  // Requirement: Expression Body syntax (Slide 5, 18) [cite: 69, 186]
  // Uses Elvis operator (Slide 2) [cite: 21]
  String get grade => _calculateGrade(score ?? 0);

  String _calculateGrade(int s) {
    if (s >= 90) return "A";
    if (s >= 80) return "B";
    if (s >= 70) return "C";
    if (s >= 60) return "D";
    return "F";
  }
}

void main() async {
  stdout.write("Enter the name of your Excel file: ");
  String? filename = stdin.readLineSync();

  if (filename == null || !File(filename).existsSync()) {
    print("Error: File not found!");
    return;
  }

  var bytes = File(filename).readAsBytesSync();
  var excel = Excel.decodeBytes(bytes);

  // Requirement: Use Collections to store objects (Slide 11)
  List<Student> students = [];

  for (var table in excel.tables.keys) {
    var rows = excel.tables[table]!.rows;
    for (int i = 1; i < rows.length; i++) {
      var row = rows[i];
      String name = row[0]?.value?.toString() ?? "Unknown";
      var val = row[1]?.value;
      int? mark = (val is IntCellValue) ? val.value : int.tryParse(val.toString());

      students.add(Student(name, mark));
    }
  }


  var failingStudents = students.where((s) => (s.score ?? 0) < 50).toList();


  var allGrades = students.map((s) => s.grade).toList();


  print("\n--- GRADE PREVIEW (Milestone 2) ---");
  students.forEach((s) => print("${s.name.padRight(15)} | Mark: ${s.score ?? 'N/A'} | Grade: ${s.grade}"));


  if (failingStudents.isNotEmpty) {
    print("\n--- STUDENTS NEEDING ATTENTION ---");
    failingStudents.forEach((s) => print("ALERT: ${s.name} failed with ${s.score}"));
  }

  // Save results
  _saveToExcel(students);
}

// Requirement: Function with Parameters (Slide 4) [cite: 51]
void _saveToExcel(List<Student> students) {
  var resultExcel = Excel.createExcel();
  Sheet sheet = resultExcel['Sheet1'];
  sheet.appendRow([TextCellValue("Name"), TextCellValue("Mark"), TextCellValue("Grade")]);

  // Using forEach for sheet generation
  students.forEach((s) {
    sheet.appendRow([
      TextCellValue(s.name),
      TextCellValue(s.score?.toString() ?? "N/A"),
      TextCellValue(s.grade)
    ]);
  });

  var fileBytes = resultExcel.save();
  if (fileBytes != null) {
    File("results.xlsx").writeAsBytesSync(fileBytes);
    print("\n[SUCCESS] 'results.xlsx' generated with Milestone 2 logic!");
  }
}