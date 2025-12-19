package com.example.kursach.model

data class ClearedChatEntry(
    val userId: String,
    val chatId: String,
    val clearedAt: Long = System.currentTimeMillis()
)

