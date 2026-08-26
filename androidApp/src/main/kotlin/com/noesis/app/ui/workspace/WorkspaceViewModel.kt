package com.noesis.app.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noesis.app.network.CreateWorkspacePayload
import com.noesis.app.network.JoinWorkspacePayload
import com.noesis.app.network.NoesisApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

data class WorkspaceItem(
    val id: String,
    val name: String,
    val slug: String,
    val role: String,
)

data class WorkspaceUiState(
    val workspaces: List<WorkspaceItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val userRole: String? = null,   // global role (personal / member / manager / owner)
    val canInviteUsers: Boolean = false,
    val successMessage: String? = null,
)

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val api: NoesisApiService,
    private val supabase: SupabaseClient,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val token = getToken() ?: return@launch
                // Fetch workspaces and me in parallel
                val wsResponse = api.getWorkspaces("Bearer $token")
                val meResponse = api.getMe("Bearer $token")

                val workspaces = if (wsResponse.isSuccessful) {
                    val body = wsResponse.body()?.string() ?: return@launch
                    json.parseToJsonElement(body)
                        .jsonObject["workspaces"]?.jsonArray
                        ?.map { el ->
                            val obj = el.jsonObject
                            WorkspaceItem(
                                id   = obj["id"]?.jsonPrimitive?.content ?: "",
                                name = obj["name"]?.jsonPrimitive?.content ?: "",
                                slug = obj["slug"]?.jsonPrimitive?.content ?: "",
                                role = obj["role"]?.jsonPrimitive?.content ?: "",
                            )
                        } ?: emptyList()
                } else emptyList()

                val (role, canInvite) = if (meResponse.isSuccessful) {
                    val body = meResponse.body()?.string() ?: "{}"
                    val obj = json.parseToJsonElement(body).jsonObject
                    val r = obj["role"]?.jsonPrimitive?.content ?: "personal"
                    val ci = obj["canInviteUsers"]?.jsonPrimitive?.content == "true"
                    r to ci
                } else "personal" to false

                _uiState.update {
                    it.copy(workspaces = workspaces, userRole = role, canInviteUsers = canInvite, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun joinWorkspace(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            try {
                val token = getToken() ?: return@launch
                val response = api.joinWorkspace("Bearer $token", JoinWorkspacePayload(code))
                if (response.isSuccessful) {
                    _uiState.update { it.copy(successMessage = "Successfully joined workspace!") }
                    refresh()
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    val msg = runCatching {
                        json.parseToJsonElement(errBody).jsonObject["error"]?.jsonPrimitive?.content
                    }.getOrNull() ?: "Failed to join workspace"
                    _uiState.update { it.copy(isLoading = false, error = msg) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun createWorkspace(name: String) {
        if (name.isBlank()) return
        val slug = name.lowercase().replace(Regex("[^a-z0-9]"), "-").trim('-')
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val token = getToken() ?: return@launch
                val response = api.createWorkspace("Bearer $token", CreateWorkspacePayload(name, slug))
                if (response.isSuccessful) {
                    _uiState.update { it.copy(successMessage = "Workspace created!") }
                    refresh()
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to create workspace") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun generateInvite(workspaceId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val token = getToken() ?: return@launch
                val response = api.generateInvite("Bearer $token", workspaceId)
                if (response.isSuccessful) {
                    val body = response.body()?.string() ?: "{}"
                    val code = json.parseToJsonElement(body).jsonObject["code"]?.jsonPrimitive?.content ?: ""
                    onResult(code)
                }
            } catch (_: Exception) {}
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }

    private suspend fun getToken(): String? =
        supabase.auth.currentSessionOrNull()?.accessToken
}
