/**
 * Student Data Model

 */
class Student(
    val name: String,
    val score: Int? = null
) {
    /**
     
     */
    val grade: String
        get() {
            val s = score ?: 0  // If null, use 0
            return when {
                s >= 90 -> "A"
                s >= 80 -> "B"
                s >= 70 -> "C"
                s >= 60 -> "D"
                else -> "F"
            }
        }

    override fun toString(): String {
        return "Student(name='$name', score=$score, grade='$grade')"
    }
}
