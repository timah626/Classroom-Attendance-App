
List<int> processList(List<int> numbers, bool Function(int) predicate) {
  List<int> result = [];
  for (var num in numbers) {
    // If the element satisfies the predicate (lambda), add to new list
    if (predicate(num)) {
      result.add(num);
    }
  }
  return result;
}

void main() {
  // Requirement: Test with specific data from slide
  final nums = [1, 2, 3, 4, 5, 6];

  // Using a lambda to filter even numbers: it % 2 == 0
  final even = processList(nums, (it) => it % 2 == 0);

  print("--- Custom Higher-Order Function ---");
  print("Original List: $nums");
  print("Filtered (Even) List: $even"); // Expected output: [2, 4, 6]
}