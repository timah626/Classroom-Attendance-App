package com.example.testapplication2


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.HttpRequestException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthViewModel : ViewModel() {
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun loginUser(email: String, pass: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                // 1. Log in to Supabase Auth
                supabase.auth.signInWith(Email) {
                    this.email = email
                    password = pass
                }

                // 2. Fetch the user's role from your 'profiles' table
                val user = supabase.auth.currentUserOrNull()
                if (user != null) {
                    val profile = supabase.from("profiles")
                        .select { filter { eq("id", user.id) } }
                        .decodeSingle<UserProfile>() // We'll define this data class below

                    onResult(profile.role) // Returns "teacher" or "student"
                }
            } catch (e: RestException) {
                errorMessage = "Database error: ${e.error}"
            } catch (e: HttpRequestException) {
                errorMessage = "Network error. Please check your connection."
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Login failed"
            } finally {
                isLoading = false
            }
        }
    }



    fun registerUser(
        email: String,
        password: String,
        username: String,
        role: String,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
            /*   // 1. Sign up the user in Supabase Auth
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                } */


                supabase.auth.signUpWith(Email) {
                    this.email = email.trim()
                    this.password = password.trim()
                    data = buildJsonObject {
                        put("username", username.trim())
                        put("role", role.lowercase())
                    }
                }












                // 2. Get the newly created user
                val user = supabase.auth.currentUserOrNull()

                if (user != null) {
                    // 3. Insert their profile into the 'profiles' table
                    supabase.from("profiles").insert(
                        mapOf(
                            "id" to user.id,
                            "email" to email,
                            "username" to username,
                            "role" to role.lowercase() // stores "teacher" or "student"
                        )
                    )
                    onResult(role.lowercase())
                }
            } catch (e: RestException) {
                errorMessage = "RestException: ${e.message} | error: ${e.error} | desc: ${e.description}"
            } catch (e: HttpRequestException) {
                errorMessage = "HttpException: ${e.message} | cause: ${e.cause?.message}"
            } catch (e: Exception) {
                errorMessage = "Exception: ${e.javaClass.simpleName}: ${e.message} | cause: ${e.cause?.message}"
            }finally {
                isLoading = false
            }
        }
    }
}

// Simple data class to match your Supabase table columns
@kotlinx.serialization.Serializable
data class UserProfile(val role: String)
