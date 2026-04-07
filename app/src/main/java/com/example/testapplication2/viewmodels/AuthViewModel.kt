package com.example.testapplication2.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testapplication2.repositories.AuthRepository
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun loginUser(email: String, password: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val profile = repository.login(email, password)
                onResult(profile.role)
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
                val profile = repository.register(email, password, username, role)
                onResult(profile.role)
            } catch (e: RestException) {
                errorMessage = "Database error: ${e.error}"
            } catch (e: HttpRequestException) {
                errorMessage = "Network error. Please check your connection."
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Registration failed"
            } finally {
                isLoading = false
            }
        }
    }
}