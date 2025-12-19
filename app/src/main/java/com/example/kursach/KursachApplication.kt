package com.example.kursach

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase

class KursachApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        JsonDatabase.initialize(this)
        UserManager.initialize(this)
        NotificationManager.initialize(this)
        
        // Инициализируем тему приложения
        val sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}