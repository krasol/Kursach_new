package com.example.kursach.model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val password: String,
    val userType: UserType,
    val gender: Gender? = null,
    val description: String = "",
    val balance: Int = 0,
    val galleryPhotos: List<String> = emptyList(),
    val avatar: String = "",
    val isBanned: Boolean = false
)


