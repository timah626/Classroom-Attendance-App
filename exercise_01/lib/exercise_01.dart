import 'dart:io';
import 'package:excel/excel.dart';

class Student {
  final String name;
  final int? score;

  Student(this.name, this.score);

  // Logic using the Elvis operator (??) as requested in your notes [cite: 213, 217]
  String get grade {
    int s = score ?? 0;
    if (s >= 90) return "A";
    if (s >= 80) return "B";
    if (s >= 70) return "C";
    if (s >= 60) return "D";
    return "F";
  }
}

void main() async {
  // 1. Ask for the file path
  stdout.write("Enter the name/path of your Excel file (e.g., students.xlsx): ");
  String? path = stdin.readLineSync();

  if (path == null || !File(path).existsSync()) {
    print("Error: File not found. Make sure you typed the name correctly!");
    return;
  }

  var bytes = File(path).readAsBytesSync();
  var excel = Excel.decodeBytes(bytes);
  var resultExcel = Excel.createExcel();
  Sheet sheet = resultExcel['Sheet1'];

  sheet.appendRow([TextCellValue("Name"), TextCellValue("Mark"), TextCellValue("Grade")]);

  print("\n--- PREVIEW OF GENERATED GRADES ---");
  print("${'Name'.padRight(15)} | ${'Mark'.padRight(5)} | ${'Grade'}");
  print("-" * 30);

  for (var table in excel.tables.keys) {
    var rows = excel.tables[table]!.rows;
    for (int i = 1; i < rows.length; i++) {
      var row = rows[i];
      String name = row[0]?.value.toString() ?? "Unknown";
      var val = row[1]?.value;
      int? mark = (val is int) ? val : int.tryParse(val.toString());

      var s = Student(name, mark);

      // Console Preview
      print("${s.name.padRight(15)} | ${(s.score?.toString() ?? 'N/A').padRight(5)} | ${s.grade}");

      // Add to new Excel
      sheet.appendRow([
        TextCellValue(s.name),
        TextCellValue(s.score?.toString() ?? "N/A"),
        TextCellValue(s.grade)
      ]);
    }
  }

  // 3. Save the file
  var fileBytes = resultExcel.save();
  if (fileBytes != null) {
    File("results.xlsx").writeAsBytesSync(fileBytes);
    print("\n[SUCCESS] 'results.xlsx' created! You can now open it in Office 2021.");
  }
}