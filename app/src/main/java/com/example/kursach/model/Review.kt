package com.example.kursach.model

data class Review(
    val id: String,
    val trainerId: String,
    val userId: String,
    val userName: String,
    val rating: Float,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)














