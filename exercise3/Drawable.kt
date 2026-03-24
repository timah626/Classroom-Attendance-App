
interface Drawable {
    fun draw()
}

// Circle class implementing Drawabl
class Circle(val radius: Int) : Drawable {
    override fun draw() {
        println("Circle with radius $radius:")
        when (radius) {
            1 -> {
                println(" * ")
            }
            2 -> {
                println("  *  ")
                println(" * * ")
                println("  *  ")
            }
            else -> {
                println(" *** ")
                println(" * * ")
                println(" *** ")
            }
        }
        println()
    }
}

// Square class implementing Drawable
class Square(val sideLength: Int) : Drawable {
    override fun draw() {
        println("Square with side length $sideLength:")
        repeat(sideLength) {
            repeat(sideLength) {
                print("* ")
            }
            println()
        }
        println()
    }
}


fun main() {
    val shapes: List<Drawable> = listOf(
        Circle(1),
        Circle(2),
        Circle(3),
        Square(3),
        Square(5)
    )
    
    println("Drawing all shapes:")
    println("=".repeat(30))
    
    shapes.forEach { it.draw() }
}
