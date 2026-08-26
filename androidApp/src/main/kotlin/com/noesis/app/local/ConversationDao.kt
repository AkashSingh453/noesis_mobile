package com.noesis.app.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE workspaceId = :workspaceId OR (workspaceId IS NULL AND :workspaceId IS NULL) ORDER BY updatedAt DESC")
    fun getConversations(workspaceId: String?): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)
    
    @Query("DELETE FROM conversations")
    suspend fun clearAll()
}
