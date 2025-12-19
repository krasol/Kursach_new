package com.example.kursach.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.kursach.ChatsListActivity
import com.example.kursach.MainActivity
import com.example.kursach.MeetingsActivity
import com.example.kursach.R
import com.example.kursach.UserProfileActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

object BottomNavigationHelper {

    fun setup(bottomNavigationView: BottomNavigationView, activity: AppCompatActivity, selectedItemId: Int) {
        bottomNavigationView.selectedItemId = selectedItemId

        bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) {
                return@setOnItemSelectedListener true
            }

            when (item.itemId) {
                R.id.navigation_home -> {
                    navigate(activity, MainActivity::class.java)
                    true
                }
                R.id.navigation_chats -> {
                    navigate(activity, ChatsListActivity::class.java)
                    true
                }
                R.id.navigation_calendar -> {
                    navigate(activity, MeetingsActivity::class.java)
                    true
                }
                R.id.navigation_profile -> {
                    navigate(activity, UserProfileActivity::class.java)
                    true
                }
                else -> false
            }
        }
    }

    private fun navigate(activity: AppCompatActivity, destination: Class<out AppCompatActivity>) {
        if (activity::class.java == destination) return
        val intent = Intent(activity, destination).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        activity.startActivity(intent)
        activity.overridePendingTransition(0, 0)
        activity.finish()
    }
}
