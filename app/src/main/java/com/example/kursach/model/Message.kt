package com.example.kursach.model

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val receiverId: String, // Для групповых чатов это groupChatId
    val text: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val meetingId: String? = null,
    val attachmentType: String? = null, // "image" or "file"
    val attachmentPath: String? = null, // Path to the attached file
    val isGroupMessage: Boolean = false, // Флаг группового сообщения
    val isEdited: Boolean = false, // Флаг редактирования сообщения
    val isDeleted: Boolean = false // Флаг удаления сообщения
)





