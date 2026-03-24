abstract class Animal(val name: String) {
    abstract val legs: Int
    abstract fun makeSound(): String
}
class Dog(name: String) : Animal(name) {
    override val legs: Int = 4
    override fun makeSound(): String {
        return "Woof! Woof!"
    }
}
class Cat(name: String) : Animal(name) {
    override val legs: Int = 4
    override fun makeSound(): String {
        return "Meow! Meow!"
    }
}
fun main() {
    // Creeating the animals here
    val dog = Dog("Buddy")
    val cat = Cat("Whiskers")

    // Create a list of animals
    val animals: List<Animal> = listOf(dog, cat)

    // Iterate and print
    for (animal in animals) {
        println("${animal.name} (${animal.legs} legs): ${animal.makeSound()}")
    }
}
