import 'package:flutter/material.dart';
import 'student_manager.dart'; // Linking our logic

void main() => runApp(const StudentApp());

class StudentApp extends StatelessWidget {
  const StudentApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData(primarySwatch: Colors.blue),
      home: const StudentListScreen(),
    );
  }
}

class StudentListScreen extends StatefulWidget {
  const StudentListScreen({super.key});
  @override
  State<StudentListScreen> createState() => _StudentListScreenState();
}

class _StudentListScreenState extends State<StudentListScreen> {
  final StudentManager manager = StudentManager();
  final TextEditingController nameController = TextEditingController();
  final TextEditingController gradeController = TextEditingController();

  void _handleButtonPress() {
    if (nameController.text.isNotEmpty && gradeController.text.isNotEmpty) {
      setState(() {
        manager.addStudent(
          nameController.text,
          double.parse(gradeController.text),
        );
        nameController.clear();
        gradeController.clear();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Student Grade Tracker")),
      body: Column(
        children: [
          // Header Summary Card
          Container(
            padding: const EdgeInsets.all(20),
            color: Colors.blue.shade50,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text("Class Average:", style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                Text("${manager.calculateAverage().toStringAsFixed(1)}%",
                    style: const TextStyle(fontSize: 22, color: Colors.blue, fontWeight: FontWeight.bold)),
              ],
            ),
          ),
          // Input Fields
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              children: [
                TextField(controller: nameController, decoration: const InputDecoration(labelText: "Student Name")),
                TextField(controller: gradeController, decoration: const InputDecoration(labelText: "Grade"), keyboardType: TextInputType.number),
                const SizedBox(height: 10),
                ElevatedButton(onPressed: _handleButtonPress, child: const Text("Add to List")),
              ],
            ),
          ),
          // Scrollable List of Students
          Expanded(
            child: ListView.builder(
              itemCount: manager.students.length,
              itemBuilder: (context, index) {
                final student = manager.students[index];
                return ListTile(
                  leading: CircleAvatar(child: Text(student.letterGrade)),
                  title: Text(student.name),
                  subtitle: Text("Grade: ${student.grade}"),
                  trailing: Text(student.status,
                      style: TextStyle(color: student.grade >= 50 ? Colors.green : Colors.red, fontWeight: FontWeight.bold)),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}