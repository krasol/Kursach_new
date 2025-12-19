package com.example.kursach.data

import android.content.Context
import android.content.SharedPreferences
import com.example.kursach.database.JsonDatabase
import com.example.kursach.model.User
import com.example.kursach.model.UserType

object UserManager {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_TYPE = "user_type"
    
    private lateinit var prefs: SharedPreferences
    
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun login(email: String, password: String): User? {
        val user = JsonDatabase.getUserByEmail(email)
        if (user != null && user.password == password) {
            if (user.isBanned) {
                return null
            }
            saveCurrentUser(user.id, user.userType)
            return user
        }

        val admin = JsonDatabase.getTechnicalAdminByEmail(email)
        if (admin != null && admin.password == password) {
            val adminUser = com.example.kursach.model.User(
                id = admin.id,
                name = admin.name,
                email = admin.email,
                password = admin.password,
                userType = UserType.TECH_ADMIN,
                gender = null,
                description = "Технический администратор",
                balance = 0,
                galleryPhotos = emptyList(),
                avatar = "",
                isBanned = false
            )
            saveCurrentUser(adminUser.id, adminUser.userType)
            return adminUser
        }

        return null
    }
    
    fun register(name: String, email: String, password: String, userType: UserType, gender: com.example.kursach.model.Gender? = null): User? {
        val user = JsonDatabase.registerUser(name, email, password, userType, gender)
        if (user != null) {
            saveCurrentUser(user.id, user.userType)
        }
        return user
    }
    
    fun getCurrentUser(): User? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val typeValue = prefs.getString(KEY_USER_TYPE, UserType.USER.name) ?: UserType.USER.name
        val userType = runCatching { UserType.valueOf(typeValue) }.getOrElse { UserType.USER }
        val user = if (userType == UserType.TECH_ADMIN) {
            JsonDatabase.getTechnicalAdminAsUser(userId)
        } else {
            JsonDatabase.getUserById(userId)
        }
        // Если пользователь забанен, разлогиниваем его
        if (user != null && user.isBanned) {
            logout()
            return null
        }
        return user
    }
    
    fun logout() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USER_TYPE)
            .apply()
    }
    
    fun isLoggedIn(): Boolean {
        return prefs.contains(KEY_USER_ID)
    }
    
    fun setCurrentUser(user: User) {
        saveCurrentUser(user.id, user.userType)
    }
    
    private fun saveCurrentUser(userId: String, userType: UserType) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_TYPE, userType.name)
            .apply()
    }
}


