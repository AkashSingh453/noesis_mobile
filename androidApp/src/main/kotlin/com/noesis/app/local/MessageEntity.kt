package com.noesis.app.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String?,
    val role: String,
    val content: String,
    val createdAt: Long
)
