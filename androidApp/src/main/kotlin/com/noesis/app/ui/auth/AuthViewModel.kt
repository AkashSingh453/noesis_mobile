package com.noesis.app.ui.auth

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    suspend fun signUp(emailField: String, passwordField: String, fullNameField: String) {
        supabaseClient.auth.signUpWith(Email) {
            email = emailField
            password = passwordField
            data = buildJsonObject {
                put("full_name", JsonPrimitive(fullNameField))
            }
        }
    }

    suspend fun signIn(emailField: String, passwordField: String) {
        supabaseClient.auth.signInWith(Email) {
            email = emailField
            password = passwordField
        }
    }
}
