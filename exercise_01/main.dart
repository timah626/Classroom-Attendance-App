import 'dart:io';
import 'package:excel/excel.dart';

// Requirement: Data Model (Matches Milestone 1 from slides) [cite: 238, 261]
class Student {
  final String name;
  final int? score; // Nullable type to handle missing data [cite: 18, 204]

  Student(this.name, this.score);

  // Logic using the Elvis operator (??) as requested in your notes [cite: 18, 213]
  String get grade {
    int s = score ?? 0; // If null, default to 0 [cite: 213]
    if (s >= 90) return "A";
    if (s >= 80) return "B";
    if (s >= 70) return "C";
    if (s >= 60) return "D";
    return "F";
  }
}

void main() async {
  stdout.write("Enter the name of your Excel file (e.g., students.xlsx): ");
  String? filename = stdin.readLineSync();

  if (filename == null || !File(filename).existsSync()) {
    print("Error: File '$filename' not found in this folder!");
    return;
  }

  var bytes = File(filename).readAsBytesSync();
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

      // Safe call style handling for name [cite: 198]
      String name = row[0]?.value?.toString() ?? "Unknown";

      // Fix for the Type Assignment Error
      var val = row[1]?.value;
      int? mark;
      if (val is IntCellValue) {
        mark = val.value;
      } else if (val != null) {
        mark = int.tryParse(val.toString());
      }

      var s = Student(name, mark);

      // Console Preview using string templates [cite: 64]
      print("${s.name.padRight(15)} | ${(s.score?.toString() ?? 'N/A').padRight(5)} | ${s.grade}");

      sheet.appendRow([
        TextCellValue(s.name),
        TextCellValue(s.score?.toString() ?? "N/A"),
        TextCellValue(s.grade)
      ]);
    }
  }

  var fileBytes = resultExcel.save();
  if (fileBytes != null) {
    File("results.xlsx").writeAsBytesSync(fileBytes);
    print("\n[SUCCESS] 'results.xlsx' created!");
  }
}