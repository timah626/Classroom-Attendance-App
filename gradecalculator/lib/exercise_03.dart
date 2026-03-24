

void main() {
  // Requirement: Sample Data from your second photo
  List<String> words = ["apple", "cat", "banana", "dog", "elephant"];


  Map<String, int> wordMap = {for (var w in words) w: w.length};

  print("--- Word Length Transformation ---");


  wordMap.entries
      .where((entry) => entry.value > 4) // Filter lengths > 4
      .forEach((entry) => print("${entry.key} has length ${entry.value}"));


}