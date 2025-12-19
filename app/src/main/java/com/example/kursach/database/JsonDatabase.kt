package com.example.kursach.database

import android.content.Context
import com.example.kursach.model.ClearedChatEntry
import com.example.kursach.model.GroupChat
import com.example.kursach.model.Meeting
import com.example.kursach.model.Message
import com.example.kursach.model.Review
import com.example.kursach.model.Report
import com.example.kursach.model.ReportStatus
import com.example.kursach.model.ReportTargetType
import com.example.kursach.model.TechnicalAdmin
import com.example.kursach.model.User
import com.example.kursach.model.UserType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.UUID

object JsonDatabase {
    private lateinit var context: Context
    private val gson = Gson()
    
    private val usersFile: File
        get() = File(context.filesDir, "users.json")
    
    private val messagesFile: File
        get() = File(context.filesDir, "messages.json")

    private val meetingsFile: File
        get() = File(context.filesDir, "meetings.json")
    
    private val reviewsFile: File
        get() = File(context.filesDir, "reviews.json")
    
    private val trainersFile: File
        get() = File(context.filesDir, "trainers.json")
    
    private val groupChatsFile: File
        get() = File(context.filesDir, "group_chats.json")

    private val technicalAdminsFile: File
        get() = File(context.filesDir, "technical_admins.json")

    private val reportsFile: File
        get() = File(context.filesDir, "reports.json")

    private val clearedChatsFile: File
        get() = File(context.filesDir, "cleared_chats.json")

    private val messagesResetFlagFile: File
        get() = File(context.filesDir, "messages_reset_v2.flag")

    fun initialize(context: Context) {
        this.context = context

        // Создаем файлы если их нет
        if (!usersFile.exists()) {
            usersFile.writeText("[]")
        }
        if (!messagesFile.exists()) {
            messagesFile.writeText("[]")
        }
        if (!meetingsFile.exists()) {
            meetingsFile.writeText("[]")
        }
        if (!reviewsFile.exists()) {
            reviewsFile.writeText("[]")
        }
        if (!trainersFile.exists()) {
            trainersFile.writeText("[]")
        }
        if (!groupChatsFile.exists()) {
            groupChatsFile.writeText("[]")
        }

        if (!technicalAdminsFile.exists()) {
            technicalAdminsFile.writeText("[]")
        }

        if (!reportsFile.exists()) {
            reportsFile.writeText("[]")
        }

        if (!clearedChatsFile.exists()) {
            clearedChatsFile.writeText("[]")
        }

        resetMessagesIfNeeded()

        // Создаем дефолтного технического администратора, если его нет
        initializeDefaultAdmin()
    }

    private fun initializeDefaultAdmin() {
        val admins = getTechnicalAdmins()
        if (admins.isEmpty()) {
            val defaultAdmin = TechnicalAdmin(
                id = java.util.UUID.randomUUID().toString(),
                name = "Администратор",
                email = "admin@admin.com",
                password = "admin123"
            )
            upsertTechnicalAdmin(defaultAdmin)
        }
    }

    private fun resetMessagesIfNeeded() {
        if (!messagesResetFlagFile.exists()) {
            saveMessages(emptyList())
            messagesResetFlagFile.writeText(System.currentTimeMillis().toString())
        }
    }
    
    // Users
    fun registerUser(name: String, email: String, password: String, userType: UserType, gender: com.example.kursach.model.Gender? = null): User? {
        val users = getUsers()
        
        if (users.any { it.email == email }) {
            return null // Email уже существует
        }
        
        val user = User(
            id = UUID.randomUUID().toString(),
            name = name,
            email = email,
            password = password,
            userType = userType,
            gender = gender,
            description = "",
            balance = 0,
            galleryPhotos = emptyList(),
            avatar = "",
            isBanned = false
        )
        
        users.add(user)
        saveUsers(users)
        return user
    }
    
    fun getUserByEmail(email: String): User? {
        return getUsers().find { it.email == email }
    }

    fun getTechnicalAdminByEmail(email: String): TechnicalAdmin? {
        return getTechnicalAdmins().find { it.email.equals(email, ignoreCase = true) }
    }

    fun getTechnicalAdminById(id: String): TechnicalAdmin? {
        return getTechnicalAdmins().find { it.id == id }
    }

    fun getTechnicalAdminAsUser(id: String): User? {
        return getTechnicalAdminById(id)?.let { technicalAdminToUser(it) }
    }
    
    fun getUserById(id: String): User? {
        val user = getUsers().find { it.id == id }
        if (user != null) {
            return user
        }
        return getTechnicalAdminById(id)?.let { technicalAdminToUser(it) }
    }
    
    fun updateUser(userId: String, newName: String, newDescription: String): Boolean {
        val users = getUsers()
        val userIndex = users.indexOfFirst { it.id == userId }
        
        if (userIndex == -1) {
            return false // Пользователь не найден
        }
        
        val user = users[userIndex]
        val updatedUser = user.copy(name = newName, description = newDescription)
        users[userIndex] = updatedUser
        saveUsers(users)

        updateTrainerNamesForUser(userId, newName)
        
        // Обновляем текущего пользователя, если это он
        val currentUser = com.example.kursach.data.UserManager.getCurrentUser()
        if (currentUser?.id == userId) {
            com.example.kursach.data.UserManager.setCurrentUser(updatedUser)
        }
        
        return true
    }
    
    fun updateUserAvatar(userId: String, avatarPath: String): User? {
        val users = getUsers()
        val userIndex = users.indexOfFirst { it.id == userId }
        
        if (userIndex == -1) {
            return null // Пользователь не найден
        }
        
        val user = users[userIndex]
        val updatedUser = user.copy(avatar = avatarPath)
        users[userIndex] = updatedUser
        saveUsers(users)
        
        // Обновляем текущего пользователя, если это он
        val currentUser = com.example.kursach.data.UserManager.getCurrentUser()
        if (currentUser?.id == userId) {
            com.example.kursach.data.UserManager.setCurrentUser(updatedUser)
        }
        
        return updatedUser
    }

    private fun updateTrainerNamesForUser(userId: String, newName: String) {
        val trainers = getCustomTrainers()
        var changed = false
        trainers.replaceAll { trainer ->
            if (trainer.userId == userId || (trainer.userId.isEmpty() && trainer.id == userId)) {
                changed = true
                trainer.copy(name = newName)
            } else {
                trainer
            }
        }
        if (changed) {
            saveCustomTrainers(trainers)
        }
    }
    
    private fun getUsers(): MutableList<User> {
        if (!usersFile.exists()) return mutableListOf()
        return try {
            val jsonContent = usersFile.readText()
            val users = parseUsersJson(jsonContent)
            if (users.isNotEmpty()) {
                saveUsers(users)
            }
            users.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun parseUsersJson(jsonContent: String): List<User> {
        if (jsonContent.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val usersList = gson.fromJson<List<Map<String, Any?>>>(jsonContent, type) ?: emptyList()
            usersList.mapNotNull { userMap ->
                try {
                    val userTypeStr = userMap["userType"]?.toString() ?: "USER"
                    val userType = when {
                        userTypeStr.contains("TRAINER", ignoreCase = true) -> UserType.TRAINER
                        else -> UserType.USER
                    }
                    val balanceValue = (userMap["balance"] as? Number)?.toInt() ?: 0
                    val galleryPhotos = (userMap["galleryPhotos"] as? List<*>)
                        ?.mapNotNull { it?.toString() }
                        ?: emptyList()
                    val isBanned = (userMap["isBanned"] as? Boolean) ?: false
                    val id = userMap["id"]?.toString().orEmpty().ifEmpty { UUID.randomUUID().toString() }
                    val genderStr = userMap["gender"]?.toString()
                    val gender = try {
                        if (genderStr != null) com.example.kursach.model.Gender.valueOf(genderStr) else null
                    } catch (e: Exception) {
                        null
                    }
                    val avatar = userMap["avatar"]?.toString() ?: ""
                    User(
                        id = id,
                        name = userMap["name"]?.toString() ?: "",
                        email = userMap["email"]?.toString() ?: "",
                        password = userMap["password"]?.toString() ?: "",
                        userType = userType,
                        gender = gender,
                        description = userMap["description"]?.toString() ?: "",
                        balance = balanceValue,
                        galleryPhotos = galleryPhotos,
                        avatar = avatar,
                        isBanned = isBanned
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun saveUsers(users: List<User>) {
        FileWriter(usersFile).use { writer ->
            gson.toJson(users, writer)
        }
    }

    private fun getTechnicalAdmins(): MutableList<TechnicalAdmin> {
        if (!technicalAdminsFile.exists()) return mutableListOf()
        val type = object : TypeToken<MutableList<TechnicalAdmin>>() {}.type
        return gson.fromJson(FileReader(technicalAdminsFile), type) ?: mutableListOf()
    }

    private fun saveTechnicalAdmins(admins: List<TechnicalAdmin>) {
        FileWriter(technicalAdminsFile).use { writer ->
            gson.toJson(admins, writer)
        }
    }

    fun upsertTechnicalAdmin(admin: TechnicalAdmin) {
        val admins = getTechnicalAdmins()
        val index = admins.indexOfFirst { it.id == admin.id }
        if (index != -1) {
            admins[index] = admin
        } else {
            admins.add(admin)
        }
        saveTechnicalAdmins(admins)
    }

    private fun technicalAdminToUser(admin: TechnicalAdmin): User {
        return User(
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
    }

    fun updateUserBalance(userId: String, newBalance: Int): User? {
        val users = getUsers()
        val index = users.indexOfFirst { it.id == userId }
        if (index == -1) return null
        val updatedBalance = newBalance.coerceAtLeast(0)
        val updatedUser = users[index].copy(balance = updatedBalance)
        users[index] = updatedUser
        saveUsers(users)
        return updatedUser
    }

    fun addUserGalleryPhoto(userId: String, photoPath: String): User? {
        val users = getUsers()
        val index = users.indexOfFirst { it.id == userId }
        if (index == -1) return null
        val existingPhotos = users[index].galleryPhotos.toMutableList()
        if (!existingPhotos.contains(photoPath)) {
            existingPhotos.add(photoPath)
        }
        val updatedUser = users[index].copy(galleryPhotos = existingPhotos)
        users[index] = updatedUser
        saveUsers(users)
        return updatedUser
    }

    fun removeUserGalleryPhoto(userId: String, photoPath: String): User? {
        val users = getUsers()
        val index = users.indexOfFirst { it.id == userId }
        if (index == -1) return null
        val updatedPhotos = users[index].galleryPhotos.filterNot { it == photoPath }
        val updatedUser = users[index].copy(galleryPhotos = updatedPhotos)
        users[index] = updatedUser
        saveUsers(users)
        return updatedUser
    }

    fun updateMeeting(meeting: Meeting): Meeting? {
        val meetings = getMeetings()
        val index = meetings.indexOfFirst { it.id == meeting.id }
        if (index == -1) return null
        meetings[index] = meeting
        saveMeetings(meetings)
        return meeting
    }

    fun getMeetingById(meetingId: String): Meeting? {
        return getMeetings().find { it.id == meetingId }
    }

    fun releasePaymentForMeeting(meetingId: String): Pair<Meeting, User?>? {
        val meetings = getMeetings()
        val index = meetings.indexOfFirst { it.id == meetingId }
        if (index == -1) return null
        val meeting = meetings[index]
        if (!meeting.isPaid || meeting.isPaymentReleased || meeting.amountPaid <= 0) {
            return null
        }

        val trainerProfile = getTrainerById(meeting.trainerId)
        val trainerOwnerId = trainerProfile?.userId?.ifEmpty { trainerProfile.id } ?: meeting.trainerId

        val updatedMeeting = meeting.copy(isPaymentReleased = true)
        meetings[index] = updatedMeeting
        saveMeetings(meetings)

        val updatedTrainer = updateUserBalance(trainerOwnerId, (getUserById(trainerOwnerId)?.balance ?: 0) + meeting.amountPaid)

        return updatedMeeting to updatedTrainer
    }
    
    // Messages
    fun saveMessage(message: Message) {
        val messages = getMessages()
        messages.add(message)
        saveMessages(messages)

        if (message.isGroupMessage) {
            val groupChat = getGroupChatById(message.receiverId)
            val affected = mutableListOf(message.senderId)
            if (groupChat != null) {
                affected.addAll(groupChat.members.filter { it != message.senderId })
            }
            removeClearedChatEntries(message.chatId, affected)
        } else {
            removeClearedChatEntries(message.chatId, listOf(message.senderId, message.receiverId))
        }
        
        // Показываем уведомление для получателя
        if (!message.isRead && message.senderId != message.receiverId) {
            // Проверяем, не является ли это групповым сообщением
            if (message.isGroupMessage) {
                val groupChat = getGroupChatById(message.receiverId)
                groupChat?.members?.forEach { memberId ->
                    if (memberId != message.senderId) {
                        // Уведомление будет показано через проверку в MainActivity
                    }
                }
            } else {
                // Для обычных сообщений показываем уведомление получателю
                // Уведомление будет показано через проверку в MainActivity
            }
        }
    }
    
    fun saveMessages(messages: List<Message>) {
        FileWriter(messagesFile).use { writer ->
            gson.toJson(messages, writer)
        }
    }

    fun clearChatForUser(userId: String, chatId: String) {
        val entries = getClearedChatEntriesInternal()
        entries.removeAll { it.userId == userId && it.chatId == chatId }
        entries.add(ClearedChatEntry(userId = userId, chatId = chatId, clearedAt = System.currentTimeMillis()))
        saveClearedChatEntries(entries)
    }

    fun getClearedChatEntry(userId: String, chatId: String): ClearedChatEntry? {
        return getClearedChatEntriesInternal().find { it.userId == userId && it.chatId == chatId }
    }

    fun removeClearedChatEntries(chatId: String, userIds: Collection<String>) {
        if (userIds.isEmpty()) return
        val entries = getClearedChatEntriesInternal()
        val removed = entries.removeAll { it.chatId == chatId && userIds.contains(it.userId) }
        if (removed) {
            saveClearedChatEntries(entries)
        }
    }

    private fun getClearedChatEntriesInternal(): MutableList<ClearedChatEntry> {
        if (!clearedChatsFile.exists()) return mutableListOf()
        val type = object : TypeToken<MutableList<ClearedChatEntry>>() {}.type
        return gson.fromJson(FileReader(clearedChatsFile), type) ?: mutableListOf()
    }

    private fun saveClearedChatEntries(entries: List<ClearedChatEntry>) {
        FileWriter(clearedChatsFile).use { writer ->
            gson.toJson(entries, writer)
        }
    }
    
    fun getMessages(chatId: String): List<Message> {
        return getMessages().filter { it.chatId == chatId }.sortedBy { it.timestamp }
    }
    
    fun getAllMessages(): List<Message> {
        return getMessages()
    }
    
    private fun getMessages(): MutableList<Message> {
        if (!messagesFile.exists()) return mutableListOf()
        val type = object : TypeToken<MutableList<Message>>() {}.type
        return gson.fromJson(FileReader(messagesFile), type) ?: mutableListOf()
    }
    
    fun generateChatId(userId: String, trainerId: String): String {
        val ids = listOf(userId, trainerId).sorted()
        return "${ids[0]}_${ids[1]}"
    }
    
    fun getTrainerFromChatId(chatId: String, currentUserId: String): com.example.kursach.model.Trainer? {
        // chatId имеет формат "userId1_userId2" где IDs отсортированы
        val parts = chatId.split("_")
        if (parts.size != 2) return null
        
        val otherUserId = if (parts[0] == currentUserId) parts[1] else parts[0]
        
        // Ищем тренера по userId
        val trainers = getTrainers()
        return trainers.find { 
            it.userId.ifEmpty { it.id } == otherUserId 
        }
    }
    
    fun getUserFromChatId(chatId: String, currentUserId: String): User? {
        // chatId имеет формат "userId1_userId2" где IDs отсортированы
        val parts = chatId.split("_")
        if (parts.size != 2) return null
        
        val otherUserId = if (parts[0] == currentUserId) parts[1] else parts[0]
        
        // Если не найден тренер, возвращаем пользователя
        return getUserById(otherUserId)
    }
    
    fun getChatsForUser(userId: String): List<com.example.kursach.model.ChatInfo> {
        val messages = getAllMessages()
        val chatIds = messages
            .filter { !it.isGroupMessage && (it.senderId == userId || it.receiverId == userId) } // Исключаем групповые сообщения
            .map { it.chatId }
            .distinct()
        
        val trainers = getTrainers()
        val chats = mutableListOf<com.example.kursach.model.ChatInfo>()
        
        for (chatId in chatIds) {
            if (getClearedChatEntry(userId, chatId) != null) {
                continue
            }
            val chatMessages = getMessages(chatId).sortedByDescending { it.timestamp }
            if (chatMessages.isEmpty()) continue
            
            val lastMessage = chatMessages.first()
            val otherUserId = if (lastMessage.senderId == userId) {
                lastMessage.receiverId
            } else {
                lastMessage.senderId
            }
            
            // Ищем тренера по userId (для объединения чатов с одним пользователем)
            val trainer = trainers.find { 
                it.userId.ifEmpty { it.id } == otherUserId 
            }
            val trainerName = if (trainer != null) {
                trainer.name
            } else {
                // Если не найден тренер, возможно это пользователь - получаем его имя
                val user = getUserById(otherUserId)
                user?.name ?: "Пользователь"
            }
            
            val unreadCount = chatMessages.count { 
                it.receiverId == userId && !it.isRead 
            }
            
            // Проверяем, не является ли это групповым чатом
            val isGroupChat = lastMessage.isGroupMessage
            val groupChatId = if (isGroupChat) otherUserId else null
            
            chats.add(com.example.kursach.model.ChatInfo(
                chatId = chatId,
                trainerId = otherUserId,
                trainerName = trainerName,
                lastMessage = lastMessage.text,
                lastMessageTime = lastMessage.timestamp,
                unreadCount = unreadCount,
                isGroupChat = isGroupChat,
                groupChatId = groupChatId
            ))
        }
        
        // Добавляем групповые чаты
        val groupChats = getGroupChatsForUser(userId)
        for (groupChat in groupChats) {
            val groupChatId = groupChat.id
            val groupChatEntryId = "group_$groupChatId"
            if (getClearedChatEntry(userId, groupChatEntryId) != null) {
                continue
            }
            val groupMessages = getAllMessages()
                .filter { it.isGroupMessage && it.receiverId == groupChatId }
                .sortedByDescending { it.timestamp }
            
            val lastMessage = groupMessages.firstOrNull()
            val unreadCount = groupMessages.count { 
                it.senderId != userId && !it.isRead 
            }
            
            chats.add(com.example.kursach.model.ChatInfo(
                chatId = groupChatEntryId,
                trainerId = groupChatId,
                trainerName = groupChat.name,
                lastMessage = lastMessage?.text ?: "Группа создана",
                lastMessageTime = lastMessage?.timestamp ?: groupChat.createdAt,
                unreadCount = unreadCount,
                isGroupChat = true,
                groupChatId = groupChatId
            ))
        }
        
        return chats.sortedByDescending { it.lastMessageTime }
    }
    
    fun getTrainerById(id: String): com.example.kursach.model.Trainer? {
        val trainers = getTrainers()
        return trainers.find { it.id == id }
    }
    
    fun getTrainers(): List<com.example.kursach.model.Trainer> {
        return try {
            // Получаем всех пользователей-тренеров
            val allUsers = getUsers()
            val trainerUsers = allUsers.filter { it.userType == com.example.kursach.model.UserType.TRAINER }
            
            // Получаем кастомные анкеты тренеров
            val customTrainers = getCustomTrainers()
            
            // Создаем тренеров только из пользователей, у которых есть анкеты
            // Исключаем тренеров из DataSource, у которых нет профилей пользователей
            val trainersFromUsers = trainerUsers.flatMap { user ->
                try {
                    val userTrainers = customTrainers.filter { it.userId == user.id || (it.userId.isEmpty() && it.id == user.id) }
                    if (userTrainers.isNotEmpty()) {
                        userTrainers
                    } else {
                        // Если у пользователя нет анкеты, создаем базовую анкету
                        listOf(
                            com.example.kursach.model.Trainer(
                                id = user.id,
                                userId = user.id,
                                name = user.name,
                                category = "Тренер",
                                hobbyName = "",
                                description = user.description ?: "",
                                price = 0,
                                rating = 0f,
                                imageUrl = "",
                                availableTime = "",
                                address = "",
                                latitude = null,
                                longitude = null,
                                availableDays = emptyList(),
                                photos = emptyList()
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Пропускаем пользователей с ошибками
                    emptyList()
                }
            }
            
            // Возвращаем только тренеров, у которых есть профили пользователей
            // Исключаем тренеров из DataSource без профилей
            trainersFromUsers.distinctBy { it.id }
        } catch (e: Exception) {
            // В случае ошибки возвращаем пустой список
            emptyList()
        }
    }
    
    fun saveTrainer(trainer: com.example.kursach.model.Trainer) {
        val trainers = getCustomTrainers()
        val index = trainers.indexOfFirst { it.id == trainer.id }
        if (index != -1) {
            trainers[index] = trainer
        } else {
            trainers.add(trainer)
        }
        saveCustomTrainers(trainers)
    }
    
    fun deleteTrainer(trainerId: String) {
        val trainers = getCustomTrainers()
        trainers.removeAll { it.id == trainerId }
        saveCustomTrainers(trainers)
    }
    
    fun getTrainerByUserId(userId: String): com.example.kursach.model.Trainer? {
        // Возвращаем первую найденную анкету пользователя (для обратной совместимости)
        return getCustomTrainers().find { it.userId == userId || it.id == userId }
    }
    
    fun getTrainersByUserId(userId: String): List<com.example.kursach.model.Trainer> {
        // Возвращаем все анкеты пользователя
        return getCustomTrainers().filter { it.userId == userId || (it.userId.isEmpty() && it.id == userId) }
    }
    
    private fun getCustomTrainers(): MutableList<com.example.kursach.model.Trainer> {
        if (!trainersFile.exists()) return mutableListOf()
        return try {
            val jsonContent = trainersFile.readText()
            val trainers = parseTrainersJson(jsonContent)
            if (trainers.isNotEmpty()) {
                saveCustomTrainers(trainers)
            }
            trainers.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }
    
    private fun parseTrainersJson(jsonContent: String): List<com.example.kursach.model.Trainer> {
        if (jsonContent.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val trainersList = gson.fromJson<List<Map<String, Any?>>>(jsonContent, type) ?: emptyList()
            trainersList.mapNotNull { trainerMap ->
                try {
                    val id = trainerMap["id"]?.toString().orEmpty().ifEmpty { UUID.randomUUID().toString() }
                    val userId = trainerMap["userId"]?.toString().orEmpty().ifEmpty { id }
                    val photos = (trainerMap["photos"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                    val availableDays = (trainerMap["availableDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.distinct()?.sorted() ?: emptyList()
                    val latitude = trainerMap["latitude"]?.toString()?.toDoubleOrNull()
                    val longitude = trainerMap["longitude"]?.toString()?.toDoubleOrNull()
                    val genderStr = trainerMap["gender"]?.toString()
                    val gender = try {
                        if (genderStr != null) com.example.kursach.model.Gender.valueOf(genderStr) else null
                    } catch (e: Exception) {
                        null
                    }
                    val avatar = trainerMap["avatar"]?.toString() ?: ""
                    com.example.kursach.model.Trainer(
                        id = id,
                        userId = userId,
                        name = trainerMap["name"]?.toString().orEmpty(),
                        category = trainerMap["category"]?.toString().orEmpty(),
                        hobbyName = trainerMap["hobbyName"]?.toString().orEmpty(),
                        description = trainerMap["description"]?.toString().orEmpty(),
                        price = (trainerMap["price"] as? Number)?.toInt() ?: 0,
                        rating = (trainerMap["rating"] as? Number)?.toFloat() ?: 0f,
                        gender = gender,
                        imageUrl = trainerMap["imageUrl"]?.toString().orEmpty(),
                        avatar = avatar,
                        availableTime = trainerMap["availableTime"]?.toString().orEmpty(),
                        address = trainerMap["address"]?.toString().orEmpty(),
                        latitude = latitude,
                        longitude = longitude,
                        availableDays = availableDays,
                        photos = photos
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun saveCustomTrainers(trainers: List<com.example.kursach.model.Trainer>) {
        FileWriter(trainersFile).use { writer ->
            gson.toJson(trainers, writer)
        }
    }
    
    // Meetings
    fun saveMeeting(meeting: Meeting) {
        val meetings = getMeetings()
        meetings.add(meeting)
        saveMeetings(meetings)
    }
    
    fun getMeetingsForUser(userId: String): List<Meeting> {
        return getMeetings().filter { it.userId == userId }.sortedBy { it.date }
    }
    
    fun getMeetingsForTrainer(trainerUserId: String): List<Meeting> {
        val meetings = getMeetings()
        val trainerProfiles = getTrainersByUserId(trainerUserId).map { it.id }
        return meetings.filter { meeting ->
            meeting.trainerId == trainerUserId || trainerProfiles.contains(meeting.trainerId)
        }.sortedBy { it.date }
    }
    
    fun getAllMeetings(): List<Meeting> {
        return getMeetings()
    }
    
    private fun getMeetings(): MutableList<Meeting> {
        if (!meetingsFile.exists()) return mutableListOf()
        try {
            val jsonContent = meetingsFile.readText()
            val type = object : TypeToken<MutableList<Map<String, Any>>>() {}.type
            val meetingsList = gson.fromJson<List<Map<String, Any>>>(jsonContent, type) ?: emptyList()
            
            val meetings = mutableListOf<Meeting>()
            for (meetingMap in meetingsList) {
                try {
                    val meeting = Meeting(
                        id = meetingMap["id"]?.toString() ?: "",
                        trainerId = meetingMap["trainerId"]?.toString() ?: "",
                        userId = meetingMap["userId"]?.toString() ?: "",
                        date = meetingMap["date"]?.toString() ?: "",
                        time = meetingMap["time"]?.toString() ?: "",
                        status = meetingMap["status"]?.toString() ?: "pending",
                        createdAt = (meetingMap["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        trainerSelectedDate = meetingMap["trainerSelectedDate"]?.toString(),
                        trainerSelectedTime = meetingMap["trainerSelectedTime"]?.toString(),
                        isPaid = when (val paidValue = meetingMap["isPaid"]) {
                            is Boolean -> paidValue
                            is Number -> paidValue.toInt() != 0
                            else -> false
                        },
                        amountPaid = (meetingMap["amountPaid"] as? Number)?.toInt() ?: 0,
                        isPaymentReleased = when (val released = meetingMap["isPaymentReleased"]) {
                            is Boolean -> released
                            is Number -> released.toInt() != 0
                            else -> false
                        }
                    )
                    meetings.add(meeting)
                } catch (e: Exception) {
                    // Пропускаем некорректные записи
                }
            }
            return meetings
        } catch (e: Exception) {
            // Если ошибка десериализации, возвращаем пустой список
            return mutableListOf()
        }
    }
    
    fun saveMeetings(meetings: List<Meeting>) {
        FileWriter(meetingsFile).use { writer ->
            gson.toJson(meetings, writer)
        }
    }
    
    // Reviews
    fun saveReview(review: Review) {
        val reviews = getReviews()
        reviews.add(review)
        saveReviews(reviews)
    }
    
    fun getReviewsForTrainer(trainerId: String): List<Review> {
        return getReviews().filter { it.trainerId == trainerId }.sortedByDescending { it.createdAt }
    }
    
    fun getAllReviews(): List<Review> {
        return getReviews()
    }
    
    private fun getReviews(): MutableList<Review> {
        if (!reviewsFile.exists()) return mutableListOf()
        val type = object : TypeToken<MutableList<Review>>() {}.type
        return gson.fromJson(FileReader(reviewsFile), type) ?: mutableListOf()
    }
    
    fun saveReviews(reviews: List<Review>) {
        FileWriter(reviewsFile).use { writer ->
            gson.toJson(reviews, writer)
        }
    }
    
    // Group Chats
    fun saveGroupChat(groupChat: GroupChat) {
        val groupChats = getGroupChats()
        val index = groupChats.indexOfFirst { it.id == groupChat.id }
        if (index != -1) {
            groupChats[index] = groupChat
        } else {
            groupChats.add(groupChat)
        }
        saveGroupChats(groupChats)
    }
    
    fun getGroupChatById(id: String): GroupChat? {
        return getGroupChats().find { it.id == id }
    }
    
    fun getGroupChatsForUser(userId: String): List<GroupChat> {
        return getGroupChats().filter { it.members.contains(userId) }
    }
    
    fun getAllGroupChats(): List<GroupChat> {
        return getGroupChats()
    }

    fun leaveGroupChat(groupId: String, userId: String): Boolean {
        val groupChats = getGroupChats()
        val index = groupChats.indexOfFirst { it.id == groupId }
        if (index == -1) return false
        val group = groupChats[index]
        if (group.createdBy == userId) {
            return false
        }
        if (!group.members.contains(userId)) {
            return false
        }
        val updatedGroup = group.copy(members = group.members.filterNot { it == userId })
        groupChats[index] = updatedGroup
        saveGroupChats(groupChats)
        clearChatForUser(userId, "group_$groupId")
        return true
    }

    fun deleteGroupChat(groupId: String): Boolean {
        val groupChats = getGroupChats()
        val removed = groupChats.removeAll { it.id == groupId }
        if (!removed) return false
        saveGroupChats(groupChats)

        val allMessages = getMessages()
        val filteredMessages = allMessages.filterNot { it.isGroupMessage && it.receiverId == groupId }
        if (filteredMessages.size != allMessages.size) {
            saveMessages(filteredMessages)
        }
        val entries = getClearedChatEntriesInternal()
        val cleaned = entries.removeAll { it.chatId == "group_$groupId" }
        if (cleaned) {
            saveClearedChatEntries(entries)
        }
        return true
    }
    
    private fun getGroupChats(): MutableList<GroupChat> {
        if (!groupChatsFile.exists()) return mutableListOf()
        val type = object : TypeToken<MutableList<GroupChat>>() {}.type
        return gson.fromJson(FileReader(groupChatsFile), type) ?: mutableListOf()
    }
    
    private fun saveGroupChats(groupChats: List<GroupChat>) {
        FileWriter(groupChatsFile).use { writer ->
            gson.toJson(groupChats, writer)
        }
    }

    // Reports
    fun createReport(report: Report): Report {
        val reports = getReportsInternal()
        reports.add(report)
        saveReports(reports)
        return report
    }

    fun getReports(status: ReportStatus? = null): List<Report> {
        val reports = getReportsInternal()
        return if (status != null) {
            reports.filter { it.status == status }
        } else {
            reports
        }.sortedByDescending { it.createdAt }
    }

    fun getReportById(reportId: String): Report? {
        return getReportsInternal().find { it.id == reportId }
    }

    fun updateReport(report: Report): Report? {
        val reports = getReportsInternal()
        val index = reports.indexOfFirst { it.id == report.id }
        if (index == -1) return null
        reports[index] = report
        saveReports(reports)
        return report
    }

    private fun getReportsInternal(): MutableList<Report> {
        if (!reportsFile.exists()) return mutableListOf()
        val type = object : TypeToken<MutableList<Report>>() {}.type
        return gson.fromJson(FileReader(reportsFile), type) ?: mutableListOf()
    }

    private fun saveReports(reports: List<Report>) {
        FileWriter(reportsFile).use { writer ->
            gson.toJson(reports, writer)
        }
    }

    fun banUser(userId: String): User? {
        val users = getUsers()
        val index = users.indexOfFirst { it.id == userId }
        if (index == -1) return null
        val user = users[index]
        val updatedUser = user.copy(isBanned = true)
        users[index] = updatedUser
        saveUsers(users)
        
        // Удаляем все анкеты пользователя
        val userTrainers = getTrainersByUserId(userId)
        userTrainers.forEach { trainer ->
            deleteTrainer(trainer.id)
        }
        
        // Удаляем все записи пользователя (как клиента)
        val userMeetings = getMeetingsForUser(userId)
        val allMeetings = getMeetings()
        allMeetings.removeAll { meeting -> userMeetings.any { it.id == meeting.id } }
        
        // Удаляем все записи пользователя (как тренера)
        val trainerMeetings = getMeetingsForTrainer(userId)
        allMeetings.removeAll { meeting -> trainerMeetings.any { it.id == meeting.id } }
        saveMeetings(allMeetings)
        
        // Удаляем все сообщения с пользователем
        val allMessages = getAllMessages()
        val filteredMessages = allMessages.filterNot { message ->
            message.senderId == userId || 
            message.receiverId == userId ||
            (message.isGroupMessage && getGroupChatById(message.receiverId)?.members?.contains(userId) == true)
        }
        saveMessages(filteredMessages)
        
        // Удаляем пользователя из групповых чатов
        val allGroupChats = getAllGroupChats()
        val updatedGroupChats = allGroupChats.mapNotNull { groupChat ->
            if (groupChat.members.contains(userId)) {
                val updatedMembers = groupChat.members.filterNot { it == userId }
                // Если создатель группы забанен, удаляем группу полностью
                if (groupChat.createdBy == userId) {
                    null
                } else {
                    groupChat.copy(members = updatedMembers)
                }
            } else {
                groupChat
            }
        }
        saveGroupChats(updatedGroupChats)
        
        // Удаляем сообщения из удаленных групп
        val deletedGroupIds = allGroupChats.filter { it.createdBy == userId }.map { it.id }
        val messagesWithoutDeletedGroups = filteredMessages.filterNot { message ->
            message.isGroupMessage && deletedGroupIds.contains(message.receiverId)
        }
        saveMessages(messagesWithoutDeletedGroups)
        
        // Если забаненный пользователь сейчас залогинен, разлогиниваем его
        val currentUserId = com.example.kursach.data.UserManager.getCurrentUser()?.id
        if (currentUserId == userId) {
            com.example.kursach.data.UserManager.logout()
        }
        
        return updatedUser
    }

    fun unbanUser(userId: String): User? {
        val users = getUsers()
        val index = users.indexOfFirst { it.id == userId }
        if (index == -1) return null
        val user = users[index]
        val updatedUser = user.copy(isBanned = false)
        users[index] = updatedUser
        saveUsers(users)
        return updatedUser
    }
}


