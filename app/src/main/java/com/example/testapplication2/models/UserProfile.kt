package com.example.testapplication2.models



@kotlinx.serialization.Serializable
data class UserProfile(
    val id: String = "",
    val email: String = "",
    val username: String = "",
    val role: String = ""
)