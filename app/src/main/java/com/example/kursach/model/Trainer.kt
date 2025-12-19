package com.example.kursach.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Trainer(
    val id: String,
    val userId: String = "", // ID пользователя, который создал эту анкету
    val name: String,
    val category: String,
    val hobbyName: String = "",
    val description: String,
    val price: Int,
    val rating: Float,
    val gender: Gender? = null,
    val imageUrl: String = "",
    val avatar: String = "", // Avatar photo file path
    val availableTime: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val availableDays: List<Int> = emptyList(),
    val photos: List<String> = emptyList() // List of photo file paths
) : Parcelable


