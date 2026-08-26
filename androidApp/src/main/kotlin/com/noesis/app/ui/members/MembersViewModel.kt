package com.noesis.app.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noesis.app.network.NoesisApiService
import com.noesis.app.network.UpdateRolePayload
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

data class MemberItem(
    val userId: String,
    val email: String,
    val role: String,
)

data class MembersUiState(
    val members: List<MemberItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val api: NoesisApiService,
    private val supabase: SupabaseClient,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MembersUiState())
    val uiState: StateFlow<MembersUiState> = _uiState.asStateFlow()

    private var currentWorkspaceId: String? = null

    fun load(workspaceId: String) {
        currentWorkspaceId = workspaceId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val token = getToken() ?: return@launch
                val response = api.getWorkspaceMembers("Bearer $token", workspaceId)
                if (response.isSuccessful) {
                    val body = response.body()?.string() ?: "[]"
                    val list = json.parseToJsonElement(body)
                        .jsonObject["members"]?.jsonArray
                        ?.map { el ->
                            val obj = el.jsonObject
                            MemberItem(
                                userId = obj["userId"]?.jsonPrimitive?.content ?: "",
                                email  = obj["email"]?.jsonPrimitive?.content ?: "",
                                role   = obj["role"]?.jsonPrimitive?.content ?: "",
                            )
                        } ?: emptyList()
                    _uiState.update { it.copy(members = list, isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load members") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateRole(userId: String, newRole: String) {
        val wsId = currentWorkspaceId ?: return
        viewModelScope.launch {
            try {
                val token = getToken() ?: return@launch
                val response = api.updateMemberRole("Bearer $token", wsId, UpdateRolePayload(userId, newRole))
                if (response.isSuccessful) {
                    load(wsId)
                }
            } catch (_: Exception) {}
        }
    }

    fun removeMember(userId: String) {
        val wsId = currentWorkspaceId ?: return
        viewModelScope.launch {
            try {
                val token = getToken() ?: return@launch
                val response = api.removeMember("Bearer $token", wsId, userId)
                if (response.isSuccessful) {
                    load(wsId)
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun getToken(): String? =
        supabase.auth.currentSessionOrNull()?.accessToken
}
