package com.example.demogradecalculator

class Student(
    val name: String,
   // val exammark: Int? = null, //added
    //val ca_mark: Int? = null,//added
    val score: Int? = null,
   // val score: Int
   // get() = (ca ?: 0) + (exam ?: 0)
) {
    val grade: String
        get() {
            val s = score ?: 0
            return when {
                s >= 90 -> "A"
                s >= 80 -> "B"
                s >= 70 -> "C"
                s >= 60 -> "D"
                else -> "F"
            }
        }
}