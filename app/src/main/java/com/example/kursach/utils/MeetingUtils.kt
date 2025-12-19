package com.example.kursach.utils

import com.example.kursach.model.Meeting
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MeetingUtils {
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun isMeetingInPast(meeting: Meeting): Boolean {
        val datePart = meeting.trainerSelectedDate ?: meeting.date
        val timePart = meeting.trainerSelectedTime ?: meeting.time
        return try {
            val parsedDate = dateFormat.parse("$datePart $timePart")
            parsedDate != null && parsedDate.before(Date())
        } catch (e: Exception) {
            false
        }
    }
}









