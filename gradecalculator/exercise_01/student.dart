class Student {
  String name;
  double grade;

  Student({required this.name, required this.grade});

  // Milestone 2 Logic: Pass/Fail Status
  String get status => grade >= 50 ? "PASSED" : "FAILED";

  // Logic for Letter Grade
  String get letterGrade {
    if (grade >= 80) return 'A';
    if (grade >= 70) return 'B';
    if (grade >= 60) return 'C';
    if (grade >= 50) return 'D';
    return 'F';
  }
}