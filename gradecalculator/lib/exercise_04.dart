
class Person {
  final String name;
  final int age;

  Person(this.name, this.age);
}

void main() {

  List<Person> people = [
    Person("Alice", 25),
    Person("Bob", 30),
    Person("Charlie", 35),
    Person("Anna", 22),
    Person("Ben", 28),
  ];

  print("--- Complex Data Processing: Average Age ---");

  // Step 1: Filter names starting with 'A' or 'B'
  var filtered = people.where((p) =>
  p.name.startsWith('A') || p.name.startsWith('B')).toList();

  // Step 2 & 3: Extract ages and calculate average
  if (filtered.isNotEmpty) {
    // Extracting ages (mapping)
    var ages = filtered.map((p) => p.age);

    // Summing them up
    int totalAge = ages.reduce((value, element) => value + element);

    double average = totalAge / filtered.length;

    // Step 4: Format to one decimal place and print
    print("Filtered names: ${filtered.map((p) => p.name).join(", ")}");
    print("Average age: ${average.toStringAsFixed(1)}");
  } else {
    print("No matching people found.");
  }
}