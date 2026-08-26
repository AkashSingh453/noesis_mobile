package com.noesis.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noesis.app.BuildConfig
import com.noesis.app.network.NoesisApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.UUID
import javax.inject.Inject
import com.noesis.app.local.MessageDao
import com.noesis.app.local.MessageEntity
import com.noesis.app.local.NoesisDatabase
import kotlinx.coroutines.flow.collectLatest

// ---------------------------------------------------------------------------
// Data models
// ---------------------------------------------------------------------------

enum class MessageRole { USER, ASSISTANT }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val isStreaming: Boolean = false,
)

data class ModelOption(val id: String, val name: String)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isThinking: Boolean = false,
    val isStreaming: Boolean = false,
    val errorMessage: String? = null,
    val userEmail: String? = null,
    val userRole: String? = null,
    // Model selection
    val availableModels: List<ModelOption> = emptyList(),
    val selectedModel: ModelOption? = null,
    val canChooseModel: Boolean = false,
    // Feature gating
    val canAttachFiles: Boolean = false,
    val canUsePromptLibrary: Boolean = false,
    // Usage
    val dailyMessages: Int = 0,
    val dailyQuota: Int = 10,
)

@Serializable
private data class ChatRequestPayload(
    val message: String,
    val conversationId: String? = null,
    val workspaceId: String? = null,
    val model: String = "llama-3.1-8b-instant",
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val okHttpClient: OkHttpClient,
    private val api: NoesisApiService,
    private val jsonParser: Json,
    private val messageDao: MessageDao,
    private val database: NoesisDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamingJob: Job? = null

    /** Call after navigation to set up conversation context + load role/models */
    fun init(workspaceId: String?, conversationId: String?) {
        loadUserInfo()
        loadModels()
        loadHistory(workspaceId, conversationId)
    }

    private fun loadHistory(workspaceId: String?, conversationId: String?) {
        // 1. Observe local DB
        viewModelScope.launch {
            messageDao.getMessages(conversationId).collectLatest { localMessages ->
                val loadedMessages = localMessages.map { entity ->
                    val parsedRole = if (entity.role == "user") MessageRole.USER else MessageRole.ASSISTANT
                    ChatMessage(id = entity.id, role = parsedRole, content = entity.content)
                }
                
                _uiState.update { s ->
                    // Merge correctly: we shouldn't overwrite in-progress streaming messages.
                    // This is simple replacement, assuming we aren't streaming while loading history.
                    if (!s.isStreaming && !s.isThinking) {
                        s.copy(messages = loadedMessages)
                    } else {
                        s
                    }
                }
            }
        }
        
        // 2. Sync from API
        viewModelScope.launch {
            try {
                val token = getToken() ?: return@launch
                val response = api.getHistory(
                    authorization = "Bearer $token",
                    workspaceId = workspaceId,
                    conversationId = conversationId
                )
                if (response.isSuccessful) {
                    val body = response.body()?.string() ?: return@launch
                    val obj = jsonParser.parseToJsonElement(body).jsonObject
                    val entities = obj["messages"]?.jsonArray?.mapNotNull { el ->
                        val m = el.jsonObject
                        val id = m["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
                        val roleStr = m["role"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val contentStr = m["content"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val createdAt = m["createdAt"]?.jsonPrimitive?.content ?: "" // can parse time, using current for now
                        MessageEntity(
                            id = id,
                            conversationId = conversationId,
                            role = roleStr,
                            content = contentStr,
                            createdAt = System.currentTimeMillis() // simplified
                        )
                    } ?: emptyList()

                    messageDao.insertMessages(entities)
                }
            } catch (e: Exception) {
                // Ignore errors silently for now, as history might be empty
            }
        }
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            try {
                val token = getToken() ?: return@launch
                val meResponse = api.getMe("Bearer $token")
                if (meResponse.isSuccessful) {
                    val body = meResponse.body()?.string() ?: return@launch
                    val obj  = jsonParser.parseToJsonElement(body).jsonObject
                    _uiState.update { s ->
                        s.copy(
                            userEmail          = supabaseClient.auth.currentSessionOrNull()?.user?.email,
                            userRole           = obj["role"]?.jsonPrimitive?.content,
                            canAttachFiles     = obj["canAttachFiles"]?.jsonPrimitive?.content == "true",
                            canUsePromptLibrary = obj["canUsePromptLibrary"]?.jsonPrimitive?.content == "true",
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            try {
                val token = getToken() ?: return@launch
                val modelsResponse = api.getModels("Bearer $token")
                if (modelsResponse.isSuccessful) {
                    val body = modelsResponse.body()?.string() ?: return@launch
                    val obj  = jsonParser.parseToJsonElement(body).jsonObject
                    val models = obj["models"]?.jsonArray?.map { el ->
                        val o = el.jsonObject
                        ModelOption(
                            id   = o["id"]?.jsonPrimitive?.content ?: "",
                            name = o["name"]?.jsonPrimitive?.content ?: "",
                        )
                    } ?: emptyList()
                    val canChoose = obj["canChooseModel"]?.jsonPrimitive?.content == "true"
                    _uiState.update { s ->
                        s.copy(
                            availableModels = models,
                            selectedModel   = models.firstOrNull(),
                            canChooseModel  = canChoose,
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onModelSelected(model: ModelOption) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun sendMessage(workspaceId: String? = null, conversationId: String? = null) {
        val message = _uiState.value.inputText.trim()
        if (message.isBlank() || _uiState.value.isStreaming) return

        val userMessage = ChatMessage(role = MessageRole.USER, content = message)
        
        // Save user message locally
        viewModelScope.launch {
            messageDao.insertMessage(
                MessageEntity(
                    id = userMessage.id,
                    conversationId = conversationId,
                    role = "user",
                    content = message,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        _uiState.update { state ->
            state.copy(
                messages     = state.messages + userMessage,
                inputText    = "",
                isThinking   = true,
                isStreaming   = false,
                errorMessage = null,
            )
        }

        streamingJob = viewModelScope.launch {
            val assistantId = UUID.randomUUID().toString()

            try {
                val token = getToken() ?: throw IllegalStateException("Not authenticated")
                val selectedModel = _uiState.value.selectedModel?.id ?: "llama-3.1-8b-instant"

                val payload = jsonParser.encodeToString(
                    ChatRequestPayload.serializer(),
                    ChatRequestPayload(
                        message        = message,
                        conversationId = conversationId,
                        workspaceId    = workspaceId,
                        model          = selectedModel,
                    ),
                )

                val request = Request.Builder()
                    .url("${BuildConfig.BACKEND_URL.trimEnd('/')}/api/chat/stream")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                var firstTokenReceived = false

                streamSse(request).collect { sseEvent ->
                    when (sseEvent.type) {
                        "stream_start" -> { /* wait */ }
                        "token" -> {
                            if (!firstTokenReceived) {
                                firstTokenReceived = true
                                _uiState.update { state ->
                                    state.copy(
                                        isThinking  = false,
                                        isStreaming  = true,
                                        messages     = state.messages + ChatMessage(
                                            id          = assistantId,
                                            role        = MessageRole.ASSISTANT,
                                            content     = "",
                                            isStreaming = true,
                                        ),
                                    )
                                }
                            }
                            val tokenJson = jsonParser.parseToJsonElement(sseEvent.data)
                            val token2    = tokenJson.jsonObject["token"]?.jsonPrimitive?.content ?: ""
                            _uiState.update { state ->
                                state.copy(
                                    messages = state.messages.map { msg ->
                                        if (msg.id == assistantId) msg.copy(content = msg.content + token2)
                                        else msg
                                    },
                                )
                            }
                        }
                        "stream_end" -> {
                            val state = _uiState.value
                            val finalMsg = state.messages.find { it.id == assistantId }
                            if (finalMsg != null) {
                                // Save assistant message locally
                                viewModelScope.launch {
                                    messageDao.insertMessage(
                                        MessageEntity(
                                            id = assistantId,
                                            conversationId = conversationId,
                                            role = "assistant",
                                            content = finalMsg.content,
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                            
                            _uiState.update { state ->
                                state.copy(
                                    isThinking  = false,
                                    isStreaming  = false,
                                    messages     = state.messages.map { msg ->
                                        if (msg.id == assistantId) msg.copy(isStreaming = false)
                                        else msg
                                    },
                                    dailyMessages = state.dailyMessages + 1,
                                )
                            }
                        }
                        "quota_exceeded" -> {
                            _uiState.update {
                                it.copy(
                                    isThinking   = false,
                                    isStreaming   = false,
                                    errorMessage = "Daily message quota exceeded. Upgrade your plan.",
                                )
                            }
                        }
                        "error" -> {
                            val errorJson = runCatching {
                                jsonParser.parseToJsonElement(sseEvent.data)
                                    .jsonObject["error"]?.jsonPrimitive?.content
                            }.getOrNull() ?: sseEvent.data

                            _uiState.update { state ->
                                state.copy(
                                    isThinking   = false,
                                    isStreaming   = false,
                                    errorMessage = errorJson,
                                    messages     = if (!firstTokenReceived) {
                                        state.messages.filter { it.id != assistantId }
                                    } else {
                                        state.messages.map { msg ->
                                            if (msg.id == assistantId) msg.copy(isStreaming = false)
                                            else msg
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                
                // If we exit the collect loop normally, ensure streaming state is reset
                _uiState.update { state ->
                    state.copy(
                        isThinking  = false,
                        isStreaming  = false,
                        messages     = state.messages.map { msg ->
                            if (msg.id == assistantId) msg.copy(isStreaming = false)
                            else msg
                        },
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update { state ->
                    state.copy(
                        isThinking = false,
                        isStreaming = false,
                        messages    = state.messages.map { msg ->
                            if (msg.id == assistantId) msg.copy(isStreaming = false) else msg
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isThinking   = false,
                        isStreaming   = false,
                        errorMessage = e.message ?: "Connection failed",
                        messages     = state.messages.filter { it.id != assistantId || it.content.isNotBlank() },
                    )
                }
            }
        }
    }

    private fun streamSse(request: Request): Flow<SseEvent> = callbackFlow {
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                trySend(SseEvent(type ?: "message", data))
            }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                val errorMsg = t?.message ?: "SSE stream failed with code ${response?.code}"
                if (t != null) close(t) else close(RuntimeException(errorMsg))
            }
            override fun onClosed(eventSource: EventSource) { close() }
        }
        val factory     = EventSources.createFactory(okHttpClient)
        val eventSource = factory.newEventSource(request, listener)
        awaitClose { eventSource.cancel() }
    }

    data class SseEvent(val type: String, val data: String)

    fun stopGenerating() {
        streamingJob?.cancel()
        streamingJob = null
        _uiState.update { state ->
            state.copy(
                isThinking = false,
                isStreaming = false,
                messages = state.messages.map { msg ->
                    if (msg.isStreaming) msg.copy(isStreaming = false) else msg
                }
            )
        }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }

    fun signOut() {
        viewModelScope.launch {
            try { 
                database.messageDao().clearAll()
                database.conversationDao().clearAll()
                supabaseClient.auth.signOut() 
            } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
    }

    private suspend fun getToken(): String? =
        supabaseClient.auth.currentSessionOrNull()?.accessToken
}
