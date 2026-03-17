import 'student.dart';

class StudentManager {
  // Our collection of students
  List<Student> _students = [];

  List<Student> get students => _students;

  // Add a new student to the list
  void addStudent(String name, double grade) {
    _students.add(Student(name: name, grade: grade));
  }

  // Calculate Average using Milestone 2 Functional Programming
  double calculateAverage() {
    if (_students.isEmpty) return 0.0;
    // Map objects to grades, then reduce to sum them up (Slide 12)
    double total = _students.map((s) => s.grade).reduce((a, b) => a + b);
    return total / _students.length;
  }
}