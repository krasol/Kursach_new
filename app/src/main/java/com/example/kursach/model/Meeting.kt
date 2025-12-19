package com.example.kursach.model

data class Meeting(
    val id: String,
    val trainerId: String,
    val userId: String,
    val date: String,
    val time: String,
    val status: String,
    val createdAt: Long = System.currentTimeMillis(),
    val trainerSelectedDate: String? = null,
    val trainerSelectedTime: String? = null,
    val isPaid: Boolean = false,
    val amountPaid: Int = 0,
    val isPaymentReleased: Boolean = false
)





