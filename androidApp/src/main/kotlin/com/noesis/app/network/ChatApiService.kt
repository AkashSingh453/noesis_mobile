package com.noesis.app.network

import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

// ---------------------------------------------------------------------------
// Request/Response models
// ---------------------------------------------------------------------------

@Serializable
data class ChatRequestPayload(
    val message: String,
    val conversationId: String? = null,
    val workspaceId: String? = null,
    val model: String = "openai/gpt-oss-120b"
)

@Serializable
data class CreateConversationPayload(
    val title: String = "New Conversation",
    val workspaceId: String? = null,
)

@Serializable
data class RenameConversationPayload(val title: String)

@Serializable
data class JoinWorkspacePayload(val code: String)

@Serializable
data class CreateWorkspacePayload(val name: String, val slug: String)

@Serializable
data class UpdateRolePayload(val userId: String, val role: String)

@Serializable
data class CreatePromptPayload(val title: String, val content: String)

// ---------------------------------------------------------------------------
// API service interface
// ---------------------------------------------------------------------------

interface NoesisApiService {

    // --- Chat ---
    @Streaming
    @POST("api/chat/stream")
    suspend fun streamChat(
        @Header("Authorization") authorization: String,
        @Body payload: ChatRequestPayload
    ): Response<ResponseBody>

    @GET("api/chat/models")
    suspend fun getModels(@Header("Authorization") authorization: String): Response<ResponseBody>

    @GET("api/chat/usage")
    suspend fun getUsage(@Header("Authorization") authorization: String): Response<ResponseBody>

    @GET("api/chat/history")
    suspend fun getHistory(
        @Header("Authorization") authorization: String,
        @Query("workspaceId") workspaceId: String? = null,
        @Query("conversationId") conversationId: String? = null,
    ): Response<ResponseBody>

    // --- Me ---
    @GET("api/me")
    suspend fun getMe(@Header("Authorization") authorization: String): Response<ResponseBody>

    // --- Conversations ---
    @GET("api/conversations")
    suspend fun getConversations(
        @Header("Authorization") authorization: String,
        @Query("workspaceId") workspaceId: String? = null,
    ): Response<ResponseBody>

    @POST("api/conversations")
    suspend fun createConversation(
        @Header("Authorization") authorization: String,
        @Body payload: CreateConversationPayload,
    ): Response<ResponseBody>

    @PATCH("api/conversations/{id}")
    suspend fun renameConversation(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body payload: RenameConversationPayload,
    ): Response<ResponseBody>

    @DELETE("api/conversations/{id}")
    suspend fun deleteConversation(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    ): Response<ResponseBody>

    // --- Workspaces ---
    @GET("api/workspaces")
    suspend fun getWorkspaces(@Header("Authorization") authorization: String): Response<ResponseBody>

    @POST("api/workspaces")
    suspend fun createWorkspace(
        @Header("Authorization") authorization: String,
        @Body payload: CreateWorkspacePayload,
    ): Response<ResponseBody>

    @POST("api/workspaces/join")
    suspend fun joinWorkspace(
        @Header("Authorization") authorization: String,
        @Body payload: JoinWorkspacePayload,
    ): Response<ResponseBody>

    @POST("api/workspaces/{id}/invites")
    suspend fun generateInvite(
        @Header("Authorization") authorization: String,
        @Path("id") workspaceId: String,
    ): Response<ResponseBody>

    @GET("api/workspaces/{id}/members")
    suspend fun getWorkspaceMembers(
        @Header("Authorization") authorization: String,
        @Path("id") workspaceId: String,
    ): Response<ResponseBody>

    @PUT("api/workspaces/{id}/members/role")
    suspend fun updateMemberRole(
        @Header("Authorization") authorization: String,
        @Path("id") workspaceId: String,
        @Body payload: UpdateRolePayload,
    ): Response<ResponseBody>

    @DELETE("api/workspaces/{id}/members/{userId}")
    suspend fun removeMember(
        @Header("Authorization") authorization: String,
        @Path("id") workspaceId: String,
        @Path("userId") userId: String,
    ): Response<ResponseBody>

    // --- Prompts ---
    @GET("api/workspaces/{workspaceId}/prompts")
    suspend fun getPrompts(
        @Header("Authorization") authorization: String,
        @Path("workspaceId") workspaceId: String,
    ): Response<ResponseBody>

    @POST("api/workspaces/{workspaceId}/prompts")
    suspend fun createPrompt(
        @Header("Authorization") authorization: String,
        @Path("workspaceId") workspaceId: String,
        @Body payload: CreatePromptPayload,
    ): Response<ResponseBody>

    @DELETE("api/workspaces/{workspaceId}/prompts/{promptId}")
    suspend fun deletePrompt(
        @Header("Authorization") authorization: String,
        @Path("workspaceId") workspaceId: String,
        @Path("promptId") promptId: String,
    ): Response<ResponseBody>
}
