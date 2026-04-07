package com.example.testapplication2.repositories



import com.example.testapplication2.models.UserProfile
import com.example.testapplication2.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository {

    suspend fun login(email: String, password: String): UserProfile {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        val user = supabase.auth.currentUserOrNull()
            ?: error("Login succeeded but no user found")

        return supabase.from("profiles")
            .select { filter { eq("id", user.id) } }
            .decodeSingle<UserProfile>()
    }

    suspend fun register(
        email: String,
        password: String,
        username: String,
        role: String
    ): UserProfile {
        supabase.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password.trim()
            data = buildJsonObject {
                put("username", username.trim())
                put("role", role.lowercase())
            }
        }
        val user = supabase.auth.currentUserOrNull()
            ?: error("Registration succeeded but no user found")

        supabase.from("profiles").insert(
            mapOf(
                "id"       to user.id,
                "email"    to email.trim(),
                "username" to username.trim(),
                "role"     to role.lowercase()
            )
        )
        return UserProfile(
            id       = user.id,
            email    = email.trim(),
            username = username.trim(),
            role     = role.lowercase()
        )
    }
}