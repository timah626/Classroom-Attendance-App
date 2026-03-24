// ─────────────────────────────────────────────────────────────
//  main.dart  –  UI layer for SE 3242 Milestone 2
//  Two screens via BottomNavigationBar:
//    1. Home  – manual student entry + live list
//    2. Excel – import .xlsx, view table, export enriched file
// ─────────────────────────────────────────────────────────────

import 'dart:io';
import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'student_manager.dart';
import 'student.dart';

void main() => runApp(const GradeCalculatorApp());

// ── App root ─────────────────────────────────────────────────

class GradeCalculatorApp extends StatelessWidget {
  const GradeCalculatorApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ICT Grade Calculator',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF1A3A6B), // ICT University navy
          brightness: Brightness.light,
        ),
        useMaterial3: true,
      ),
      home: const AppShell(),
    );
  }
}

// ── Shell (bottom nav) ────────────────────────────────────────

class AppShell extends StatefulWidget {
  const AppShell({super.key});

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  int _selectedIndex = 0;

  // Single shared manager so both screens can see each other's data
  final StudentManager _manager = StudentManager();

  late final List<Widget> _screens;

  @override
  void initState() {
    super.initState();
    _screens = [
      HomeScreen(manager: _manager),
      ExcelScreen(manager: _manager),
    ];
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: _selectedIndex, children: _screens),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: (i) => setState(() => _selectedIndex = i),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.people_outline),
            selectedIcon: Icon(Icons.people),
            label: 'Students',
          ),
          NavigationDestination(
            icon: Icon(Icons.table_chart_outlined),
            selectedIcon: Icon(Icons.table_chart),
            label: 'Excel',
          ),
        ],
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
//  SCREEN 1 – Manual entry
// ═══════════════════════════════════════════════════════════════

class HomeScreen extends StatefulWidget {
  final StudentManager manager;
  const HomeScreen({super.key, required this.manager});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameCtrl = TextEditingController();
  final _gradeCtrl = TextEditingController();

  @override
  void dispose() {
    _nameCtrl.dispose();
    _gradeCtrl.dispose();
    super.dispose();
  }

  // ── Goal 1: wire up button with setState  ────────────────────
  void _handleAddStudent() {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      widget.manager.addStudent(
        _nameCtrl.text.trim(),
        double.parse(_gradeCtrl.text.trim()),
      );
    });

    _nameCtrl.clear();
    _gradeCtrl.clear();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final students = widget.manager.students;

    // Goal 3: average is derived fresh on every build – never stale
    final average = widget.manager.calculateAverage();

    return Scaffold(
      appBar: AppBar(
        title: const Text('ICT Grade Calculator'),
        backgroundColor: cs.primary,
        foregroundColor: cs.onPrimary,
        actions: [
          if (students.isNotEmpty)
            IconButton(
              tooltip: 'Clear all',
              icon: const Icon(Icons.delete_sweep_outlined),
              onPressed: () => setState(() => widget.manager.clearStudents()),
            ),
        ],
      ),
      body: Column(
        children: [
          // ── Stats bar ──────────────────────────────────────
          _StatsBar(manager: widget.manager, average: average),

          // ── Input form ─────────────────────────────────────
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
            child: Form(
              key: _formKey,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    flex: 5,
                    child: TextFormField(
                      controller: _nameCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Student name',
                        prefixIcon: Icon(Icons.person_outline),
                        border: OutlineInputBorder(),
                        isDense: true,
                      ),
                      textCapitalization: TextCapitalization.words,
                      validator: (v) =>
                      (v == null || v.trim().isEmpty) ? 'Required' : null,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    flex: 3,
                    child: TextFormField(
                      controller: _gradeCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Mark',
                        prefixIcon: Icon(Icons.grade_outlined),
                        border: OutlineInputBorder(),
                        isDense: true,
                      ),
                      keyboardType: const TextInputType.numberWithOptions(
                          decimal: true),
                      validator: (v) {
                        if (v == null || v.trim().isEmpty) return 'Required';
                        final n = double.tryParse(v.trim());
                        if (n == null) return 'Number';
                        if (n < 0 || n > 100) return '0-100';
                        return null;
                      },
                    ),
                  ),
                  const SizedBox(width: 8),
                  Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: FilledButton(
                      onPressed: _handleAddStudent,
                      child: const Icon(Icons.add),
                    ),
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 12),
          const Divider(height: 1),

          // ── Goal 2: ListView.builder ────────────────────────
          Expanded(
            child: students.isEmpty
                ? _EmptyState(
              icon: Icons.school_outlined,
              message: 'No students yet.\nAdd one above or import Excel.',
            )
                : ListView.builder(
              padding: const EdgeInsets.symmetric(vertical: 8),
              itemCount: students.length,
              itemBuilder: (context, i) {
                final s = students[i];
                return _StudentTile(student: s, index: i);
              },
            ),
          ),
        ],
      ),
    );
  }
}

// ── Reusable stats bar ────────────────────────────────────────

class _StatsBar extends StatelessWidget {
  final StudentManager manager;
  final double average;
  const _StatsBar({required this.manager, required this.average});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final students = manager.students;

    return Container(
      color: cs.primaryContainer,
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 20),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _StatChip(
              label: 'Total', value: '${students.length}', icon: Icons.people),
          _StatChip(
              label: 'Average',
              value: students.isEmpty ? '—' : average.toStringAsFixed(1),
              icon: Icons.bar_chart),
          _StatChip(
              label: 'Passed',
              value: '${manager.countPassing()}',
              icon: Icons.check_circle_outline,
              color: Colors.green.shade700),
          _StatChip(
              label: 'Failed',
              value: '${manager.countFailing()}',
              icon: Icons.cancel_outlined,
              color: Colors.red.shade700),
        ],
      ),
    );
  }
}

class _StatChip extends StatelessWidget {
  final String label, value;
  final IconData icon;
  final Color? color;
  const _StatChip(
      {required this.label,
        required this.value,
        required this.icon,
        this.color});

  @override
  Widget build(BuildContext context) {
    final c = color ?? Theme.of(context).colorScheme.primary;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 18, color: c),
        const SizedBox(height: 2),
        Text(value,
            style: TextStyle(
                fontWeight: FontWeight.bold, fontSize: 16, color: c)),
        Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
      ],
    );
  }
}

// ── Single student row ────────────────────────────────────────

class _StudentTile extends StatelessWidget {
  final Student student;
  final int index;
  const _StudentTile({required this.student, required this.index});

  Color _gradeColor(double? g) {
    if (g == null) return Colors.grey.shade500;
    if (g >= 80) return Colors.green.shade700;
    if (g >= 60) return Colors.blue.shade700;
    if (g >= 50) return Colors.orange.shade700;
    return Colors.red.shade700;
  }

  @override
  Widget build(BuildContext context) {
    final c = _gradeColor(student.grade);
    return ListTile(
      leading: CircleAvatar(
        backgroundColor: c.withOpacity(0.15),
        child: Text(
          student.letterGrade,
          style: TextStyle(
              color: c, fontWeight: FontWeight.bold, fontSize: 16),
        ),
      ),
      title: Text(student.name),
      subtitle: Text(student.status,
          style: TextStyle(color: c, fontSize: 12)),
      trailing: Chip(
        label: Text(
          student.hasGrade ? '${student.grade!.toStringAsFixed(1)}%' : 'N/A',
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
        backgroundColor: c.withOpacity(0.1),
        side: BorderSide(color: c.withOpacity(0.3)),
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════
//  SCREEN 2 – Excel import / view / export
// ═══════════════════════════════════════════════════════════════

class ExcelScreen extends StatefulWidget {
  final StudentManager manager;
  const ExcelScreen({super.key, required this.manager});

  @override
  State<ExcelScreen> createState() => _ExcelScreenState();
}

class _ExcelScreenState extends State<ExcelScreen> {
  bool _isLoading = false;
  String? _importedFileName;
  String? _exportedPath;

  // ── Import ──────────────────────────────────────────────────
  Future<void> _pickAndImport() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['xlsx'],
    );

    if (result == null || result.files.single.path == null) return;

    setState(() {
      _isLoading = true;
      _exportedPath = null;
    });

    try {
      final bytes = await File(result.files.single.path!).readAsBytes();
      setState(() {
        widget.manager.importFromExcelBytes(bytes);
        _importedFileName = result.files.single.name;
        _isLoading = false;
      });
    } catch (e) {
      setState(() => _isLoading = false);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Import failed: $e')),
        );
      }
    }
  }

  // ── Export ──────────────────────────────────────────────────
  Future<void> _export() async {
    if (widget.manager.students.isEmpty) return;

    setState(() => _isLoading = true);

    try {
      final result = widget.manager.exportToExcel();
      final dir = await getApplicationDocumentsDirectory();
      final path = '${dir.path}/${result.filename}';
      await File(path).writeAsBytes(result.bytes);

      setState(() {
        _exportedPath = path;
        _isLoading = false;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Saved: ${result.filename}'),
            action: SnackBarAction(label: 'OK', onPressed: () {}),
          ),
        );
      }
    } catch (e) {
      setState(() => _isLoading = false);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Export failed: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final students = widget.manager.students;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Excel Import / Export'),
        backgroundColor: cs.primary,
        foregroundColor: cs.onPrimary,
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : Column(
        children: [
          // ── Action bar ────────────────────────────────
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: _pickAndImport,
                    icon: const Icon(Icons.upload_file),
                    label: Text(_importedFileName == null
                        ? 'Import .xlsx'
                        : 'Re-import'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton.icon(
                    onPressed:
                    students.isNotEmpty ? _export : null,
                    icon: const Icon(Icons.download),
                    label: const Text('Export with Grades'),
                  ),
                ),
              ],
            ),
          ),

          // ── File info banner ─────────────────────────
          if (_importedFileName != null)
            Container(
              margin: const EdgeInsets.symmetric(horizontal: 16),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: cs.primaryContainer,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  Icon(Icons.table_chart,
                      size: 18, color: cs.primary),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Imported: $_importedFileName  •  '
                          '${students.length} rows',
                      style: const TextStyle(fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),

          if (_exportedPath != null)
            Container(
              margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: Colors.green.shade50,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.green.shade200),
              ),
              child: Row(
                children: [
                  Icon(Icons.check_circle,
                      size: 18, color: Colors.green.shade700),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Saved: $_exportedPath',
                      style: TextStyle(
                          fontSize: 12,
                          color: Colors.green.shade800),
                    ),
                  ),
                ],
              ),
            ),

          const SizedBox(height: 12),
          const Divider(height: 1),

          // ── Table view ───────────────────────────────
          Expanded(
            child: students.isEmpty
                ? _EmptyState(
              icon: Icons.upload_file,
              message:
              'Import an Excel file to see\nthe student data here.',
            )
                : SingleChildScrollView(
              scrollDirection: Axis.vertical,
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.all(16),
                child: _GradeTable(students: students),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Data table widget ─────────────────────────────────────────

class _GradeTable extends StatelessWidget {
  final List<Student> students;
  const _GradeTable({required this.students});

  Color _rowColor(String status) =>
      status == 'PASSED' ? Colors.green.shade50 : status == 'INCOMPLETE' ? Colors.orange.shade50 : Colors.red.shade50;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Table(
      border: TableBorder.all(color: Colors.grey.shade300, width: 0.5),
      defaultColumnWidth: const IntrinsicColumnWidth(),
      children: [
        // Header
        TableRow(
          decoration: BoxDecoration(color: cs.primary),
          children: ['#', 'Name', 'Mark', 'Letter Grade', 'Status']
              .map((h) => Padding(
            padding: const EdgeInsets.symmetric(
                horizontal: 16, vertical: 10),
            child: Text(h,
                style: TextStyle(
                    color: cs.onPrimary,
                    fontWeight: FontWeight.bold)),
          ))
              .toList(),
        ),
        // Data rows – map lambda (Slide 12)
        ...students.asMap().entries.map((entry) {
          final i = entry.key;
          final s = entry.value;
          return TableRow(
            decoration: BoxDecoration(color: _rowColor(s.status)),
            children: [
              _cell('${i + 1}', center: true),
              _cell(s.name),
              _cell(s.hasGrade ? s.grade!.toStringAsFixed(1) : 'N/A', center: true),
              _cell(s.letterGrade, center: true,
                  bold: true,
                  color: s.letterGrade == 'N/A'
                      ? Colors.grey.shade500
                      : s.letterGrade == 'F'
                      ? Colors.red.shade700
                      : Colors.green.shade700),
              _cell(s.status,
                  center: true,
                  bold: true,
                  color: s.status == 'PASSED'
                      ? Colors.green.shade700
                      : s.status == 'INCOMPLETE'
                      ? Colors.orange.shade700
                      : Colors.red.shade700),
            ],
          );
        }),
        // Average footer row
        TableRow(
          decoration: BoxDecoration(color: cs.secondaryContainer),
          children: [
            _cell('', center: true),
            _cell('CLASS AVERAGE', bold: true),
            _cell(
              students.isEmpty
                  ? '—'
                  : students.where((s) => s.hasGrade).isEmpty
                  ? '—'
                  : (students
                  .where((s) => s.hasGrade)
                  .map((s) => s.grade!)
                  .reduce((a, b) => a + b) /
                  students.where((s) => s.hasGrade).length)
                  .toStringAsFixed(1),
              center: true,
              bold: true,
            ),
            _cell('', center: true),
            _cell('', center: true),
          ],
        ),
      ],
    );
  }

  Widget _cell(String text,
      {bool center = false, bool bold = false, Color? color}) {
    return Padding(
      padding:
      const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Text(
        text,
        textAlign: center ? TextAlign.center : TextAlign.left,
        style: TextStyle(
          fontWeight: bold ? FontWeight.bold : FontWeight.normal,
          color: color,
        ),
      ),
    );
  }
}

// ── Shared empty state widget ─────────────────────────────────

class _EmptyState extends StatelessWidget {
  final IconData icon;
  final String message;
  const _EmptyState({required this.icon, required this.message});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 64, color: Colors.grey.shade300),
          const SizedBox(height: 12),
          Text(
            message,
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.grey.shade500, fontSize: 15),
          ),
        ],
      ),
    );
  }
}