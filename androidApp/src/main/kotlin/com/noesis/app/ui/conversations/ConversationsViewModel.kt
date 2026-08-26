package com.noesis.app.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noesis.app.network.CreateConversationPayload
import com.noesis.app.network.NoesisApiService
import com.noesis.app.network.RenameConversationPayload
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
import com.noesis.app.local.ConversationDao
import com.noesis.app.local.ConversationEntity
import java.time.Instant
import kotlinx.coroutines.flow.collectLatest

data class ConversationItem(
    val id: String,
    val title: String,
    val updatedAt: String,
)

data class ConversationsUiState(
    val conversations: List<ConversationItem> = emptyList(),
    val filtered: List<ConversationItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val api: NoesisApiService,
    private val supabase: SupabaseClient,
    private val json: Json,
    private val conversationDao: ConversationDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    private var currentWorkspaceId: String? = null

    fun load(workspaceId: String?) {
        currentWorkspaceId = workspaceId
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        // 1. Observe local DB
        viewModelScope.launch {
            conversationDao.getConversations(workspaceId).collectLatest { localList ->
                val list = localList.map { entity ->
                    ConversationItem(
                        id = entity.id,
                        title = entity.title,
                        updatedAt = Instant.ofEpochMilli(entity.updatedAt).toString()
                    )
                }
                _uiState.update { state -> 
                    state.copy(
                        conversations = list, 
                        filtered = if (state.searchQuery.isBlank()) list else list.filter { it.title.contains(state.searchQuery, ignoreCase = true) }, 
                        isLoading = false
                    ) 
                }
            }
        }
        
        // 2. Sync from API
        viewModelScope.launch {
            try {
                val token = getToken() ?: return@launch
                val response = api.getConversations("Bearer $token", workspaceId)
                if (response.isSuccessful) {
                    val body = response.body()?.string() ?: "[]"
                    val entities = json.parseToJsonElement(body)
                        .jsonObject["conversations"]?.jsonArray
                        ?.mapNotNull { el ->
                            val obj = el.jsonObject
                            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            ConversationEntity(
                                id = id,
                                title = obj["title"]?.jsonPrimitive?.content ?: "New Conversation",
                                workspaceId = workspaceId,
                                updatedAt = System.currentTimeMillis() // Or parse from API if format matches
                            )
                        } ?: emptyList()
                    conversationDao.insertConversations(entities)
                } else {
                    _uiState.update { it.copy(error = "Failed to sync conversations") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Network error: ${e.message}") }
            }
        }
    }

    fun search(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filtered = if (query.isBlank()) state.conversations
                else state.conversations.filter { it.title.contains(query, ignoreCase = true) },
            )
        }
    }

    fun createConversation(onCreated: (id: String) -> Unit) {
        viewModelScope.launch {
            try {
                val token = getToken() ?: return@launch
                val response = api.createConversation(
                    "Bearer $token",
                    CreateConversationPayload(title = "New Conversation", workspaceId = currentWorkspaceId),
                )
                if (response.isSuccessful) {
                    val body = response.body()?.string() ?: "{}"
                    val id = json.parseToJsonElement(body).jsonObject["id"]?.jsonPrimitive?.content ?: ""
                    load(currentWorkspaceId)
                    onCreated(id)
                }
            } catch (_: Exception) {}
        }
    }

    fun rename(conversationId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            try {
                // Optimistic update in DB (partial update isn't directly in DAO, but we can re-insert)
                val current = _uiState.value.conversations.find { it.id == conversationId }
                if (current != null) {
                    conversationDao.insertConversations(listOf(
                        ConversationEntity(id = conversationId, title = newTitle, workspaceId = currentWorkspaceId, updatedAt = System.currentTimeMillis())
                    ))
                }
                
                val token = getToken() ?: return@launch
                api.renameConversation("Bearer $token", conversationId, RenameConversationPayload(newTitle))
                load(currentWorkspaceId) // Trigger remote sync
            } catch (_: Exception) {}
        }
    }

    fun delete(conversationId: String) {
        viewModelScope.launch {
            try {
                // Optimistic delete from DB
                conversationDao.deleteConversation(conversationId)
                
                val token = getToken() ?: return@launch
                api.deleteConversation("Bearer $token", conversationId)
                load(currentWorkspaceId) // Trigger remote sync
            } catch (_: Exception) {}
        }
    }

    private suspend fun getToken(): String? =
        supabase.auth.currentSessionOrNull()?.accessToken
}
