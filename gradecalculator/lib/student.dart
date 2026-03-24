// ─────────────────────────────────────────────────────────────
//  student.dart  –  Data model for SE 3242 Milestone 2
//  Concepts used (per Class02 slides):
//    • Null safety / nullable types  (Recap slide – String?, ?., ?:)
//    • Expression-body getters       (Slide 5 – arrow syntax)
//    • Extension methods             (Slide 22 – Dart extension methods)
// ─────────────────────────────────────────────────────────────

class Student {
  String name;

  // Nullable – null means the student has no mark recorded yet
  // Demonstrates Kotlin/Dart null safety concept from Week 1 recap
  double? grade;

  Student({required this.name, this.grade});

  // ── Expression-body getters (Slide 5) ──────────────────────

  /// Whether a mark has been recorded
  bool get hasGrade => grade != null;

  /// Pass / Fail / Incomplete – uses null check (Recap slide)
  String get status {
    if (!hasGrade) return 'INCOMPLETE';
    return grade! >= 50 ? 'PASSED' : 'FAILED';
  }

  /// Letter grade – N/A when no mark is recorded
  String get letterGrade {
    if (!hasGrade) return 'N/A';
    if (grade! >= 80) return 'A';
    if (grade! >= 70) return 'B';
    if (grade! >= 60) return 'C';
    if (grade! >= 50) return 'D';
    return 'F';
  }
}

// ── Extension methods on List<Student>  (Slide 22) ──────────

extension StudentListExtensions on List<Student> {
  /// Only students who have a grade recorded
  List<Student> withGrades() => where((s) => s.hasGrade).toList();

  /// Higher-order filter (Slide 25 – Exercise 1)
  List<Student> passing() =>
      where((s) => s.hasGrade && s.grade! >= 50).toList();

  List<Student> failing() =>
      where((s) => s.hasGrade && s.grade! < 50).toList();

  List<Student> incomplete() => where((s) => !s.hasGrade).toList();

  /// Average only over students who have a grade (Slide 12 – map / fold)
  double average() {
    final graded = withGrades();
    if (graded.isEmpty) return 0.0;
    return graded.map((s) => s.grade!).reduce((a, b) => a + b) /
        graded.length;
  }

  /// fold to build a summary string  (Slide 16 – fold)
  String summary() => fold(
    'Results:\n',
        (acc, s) =>
    '$acc  • ${s.name}: ${s.hasGrade ? s.grade : "No mark"}'
        ' (${s.letterGrade} – ${s.status})\n',
  );
}