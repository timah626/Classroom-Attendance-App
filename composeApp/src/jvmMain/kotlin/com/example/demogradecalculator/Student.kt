package com.example.demogradecalculator

class Student(
    val name: String,
    val exam_mark: Int? = null,
    val ca_mark: Int? = null
) {

    val score: Int
        get() = (ca_mark ?: 0) + (exam_mark ?: 0)

    val grade: String
        get() {
            val s = score
            return when {
                s >= 90 -> "A"
                s >= 80 -> "B+"
                s >= 70 -> "B"
                s >= 60 -> "C+"
                s >= 50 -> "C"
                s >= 40 -> "D"
                else -> "F"
            }
        }
}