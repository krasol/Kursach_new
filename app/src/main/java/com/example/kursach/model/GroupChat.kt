package com.example.kursach.model

data class GroupChat(
    val id: String,
    val name: String,
    val createdBy: String, // userId создателя
    val members: List<String>, // List of userIds
    val createdAt: Long = System.currentTimeMillis(),
    val photoPath: String? = ""
)



