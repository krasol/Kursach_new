package com.example.kursach.model

data class ChatInfo(
    val chatId: String,
    val trainerId: String,
    val trainerName: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int = 0,
    val isGroupChat: Boolean = false,
    val groupChatId: String? = null
)







