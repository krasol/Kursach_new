package com.example.kursach

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kursach.NotificationManager
import com.example.kursach.adapter.MessageAdapter
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase
import com.example.kursach.databinding.ActivityMessengerBinding
import com.example.kursach.model.Meeting
import com.example.kursach.model.Message
import com.example.kursach.model.Trainer
import com.example.kursach.utils.MeetingUtils
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MessengerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessengerBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var trainer: Trainer
    private lateinit var chatId: String
    private lateinit var otherUserId: String
    
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            attachImage(it)
        }
    }
    
    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            attachFile(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessengerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fromAdmin = intent.getBooleanExtra("fromAdmin", false)
        
        val trainerFromIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("trainer", Trainer::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Trainer>("trainer")
        }
        if (trainerFromIntent == null) {
            Toast.makeText(this, "Ошибка загрузки тренера", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        trainer = trainerFromIntent

        val currentUser = UserManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Проверяем, не пытается ли пользователь писать самому себе (только если не админ)
        if (!fromAdmin) {
            val trainerUserId = trainer.userId.ifEmpty { trainer.id }
            if (currentUser.id == trainerUserId) {
                Toast.makeText(this, "Вы не можете писать самому себе", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            otherUserId = trainerUserId

            // Генерируем chatId используя userId для объединения чатов с одним пользователем
            chatId = JsonDatabase.generateChatId(currentUser.id, trainerUserId)
        } else {
            // Для админа получаем chatId из сообщений
            val messages = JsonDatabase.getAllMessages()
            val chatMessages = messages.filter { 
                val parts = it.chatId.split("_")
                parts.size == 2 && (parts[0] == trainer.userId.ifEmpty { trainer.id } || parts[1] == trainer.userId.ifEmpty { trainer.id })
            }
            if (chatMessages.isNotEmpty()) {
                chatId = chatMessages.first().chatId
                val parts = chatId.split("_")
                otherUserId = if (parts[0] == currentUser.id) parts[1] else parts[0]
            } else {
                Toast.makeText(this, "Чат не найден", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        setSupportActionBar(binding.toolbar)
        // Устанавливаем цвет троеточия сразу
        binding.toolbar.post {
            binding.toolbar.overflowIcon?.setTint(androidx.core.content.ContextCompat.getColor(this, android.R.color.white))
        }
        if (fromAdmin) {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            binding.toolbar.navigationIcon = null
            binding.toolbar.setOnClickListener(null)
            binding.messageEditText.isEnabled = false
            binding.sendButton.isEnabled = false
            binding.attachButton.isEnabled = false
        } else {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
            binding.toolbar.setOnClickListener {
                openPeerProfile()
            }
            binding.toolbar.setNavigationOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
        }
        supportActionBar?.title = trainer.name
        supportActionBar?.setDisplayUseLogoEnabled(true)
        applyPeerPhotoToToolbar()

        setupRecyclerView()
        setupSendButton()
        setupAttachButton()
        
        // Помечаем все непрочитанные сообщения в этом чате как прочитанные
        markChatMessagesAsRead()
        
        loadMessages()
    }

    override fun onResume() {
        super.onResume()
        if (::otherUserId.isInitialized) {
            applyPeerPhotoToToolbar()
        }
    }
    
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }
    
    override fun onPrepareOptionsMenu(menu: android.view.Menu?): Boolean {
        binding.toolbar.overflowIcon?.setTint(androidx.core.content.ContextCompat.getColor(this, android.R.color.white))
        return super.onPrepareOptionsMenu(menu)
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_delete_chat -> {
                showDeleteChatDialog()
                true
            }
            R.id.menu_report_chat -> {
                showReportChatDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showDeleteChatDialog() {
        AlertDialog.Builder(this)
            .setTitle("Удалить чат")
            .setMessage("Вы уверены, что хотите удалить этот чат? Сообщения будут скрыты только для вас.")
            .setPositiveButton("Удалить") { _, _ ->
                JsonDatabase.clearChatForUser(UserManager.getCurrentUser()?.id ?: return@setPositiveButton, chatId)
                Toast.makeText(this, "Чат удален", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun showReportChatDialog() {
        val currentUser = UserManager.getCurrentUser() ?: return
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_report, null)
        val reasonInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.reasonInput)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnSubmit = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSubmit)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Пожаловаться на чат")
            .create()
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnSubmit.setOnClickListener {
            val reason = reasonInput?.text?.toString()?.trim() ?: ""
            if (reason.isEmpty()) {
                Toast.makeText(this, "Укажите причину жалобы", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val report = com.example.kursach.model.Report(
                id = UUID.randomUUID().toString(),
                reporterId = currentUser.id,
                reporterName = currentUser.name,
                targetId = chatId,
                targetType = com.example.kursach.model.ReportTargetType.CHAT,
                targetName = trainer.name,
                reason = reason,
                status = com.example.kursach.model.ReportStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                chatType = "private"
            )
            
            JsonDatabase.createReport(report)
            Toast.makeText(this, "Жалоба отправлена администратору", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(
            emptyList(), 
            { meeting, accepted ->
            confirmMeeting(meeting, accepted)
        }, false, ::releasePaymentForMeeting, peerName = trainer.name,
        onEditMessage = { message ->
            showEditMessageDialog(message)
        },
        onDeleteMessage = { message ->
            deleteMessage(message)
        },
        onUserProfileClick = { userId ->
            openUserProfile(userId)
        }
        ) // isGroupChat = false
        binding.messagesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.messagesRecyclerView.adapter = adapter
    }
    
    private fun openUserProfile(userId: String) {
        val user = JsonDatabase.getUserById(userId)
        if (user != null) {
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("viewUserId", user.id)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Профиль пользователя не найден", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showEditMessageDialog(message: Message) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_message, null)
        val messageInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.messageInput)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)
        
        messageInput.setText(message.text)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnSave.setOnClickListener {
            val newText = messageInput.text?.toString()?.trim() ?: ""
            if (newText.isEmpty()) {
                Toast.makeText(this, "Сообщение не может быть пустым", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val allMessages = JsonDatabase.getAllMessages().toMutableList()
            val index = allMessages.indexOfFirst { it.id == message.id }
            if (index != -1) {
                allMessages[index] = message.copy(text = newText, isEdited = true)
                JsonDatabase.saveMessages(allMessages)
                loadMessages()
                Toast.makeText(this, "Сообщение отредактировано", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun deleteMessage(message: Message) {
        AlertDialog.Builder(this)
            .setTitle("Удалить сообщение")
            .setMessage("Вы уверены, что хотите удалить это сообщение?")
            .setPositiveButton("Удалить") { _, _ ->
                val allMessages = JsonDatabase.getAllMessages().toMutableList()
                val index = allMessages.indexOfFirst { it.id == message.id }
                if (index != -1) {
                    allMessages[index] = message.copy(text = "Сообщение удалено", isDeleted = true)
                    JsonDatabase.saveMessages(allMessages)
                    loadMessages()
                    Toast.makeText(this, "Сообщение удалено", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun setupSendButton() {
        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        binding.messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.sendButton.isEnabled = s?.toString()?.trim()?.isNotEmpty() == true
            }
        })
    }
    
    private fun setupAttachButton() {
        binding.attachButton.setOnClickListener {
            showAttachDialog()
        }
    }
    
    private fun showAttachDialog() {
        val options = arrayOf("Фото", "Файл")
        AlertDialog.Builder(this)
            .setTitle("Прикрепить")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> pickFileLauncher.launch("*/*")
                }
            }
            .show()
    }
    
    private fun attachImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val currentUser = UserManager.getCurrentUser() ?: return
            val trainerUserId = trainer.userId.ifEmpty { trainer.id }
            
            val attachmentId = UUID.randomUUID().toString()
            val attachmentFileName = "chat_image_${currentUser.id}_$attachmentId.jpg"
            val attachmentFile = File(filesDir, attachmentFileName)
            
            FileOutputStream(attachmentFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val message = Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                senderId = currentUser.id,
                receiverId = trainerUserId,
                text = "📷",
                timestamp = System.currentTimeMillis(),
                isRead = false,
                attachmentType = "image",
                attachmentPath = attachmentFileName
            )

            JsonDatabase.saveMessage(message)
            NotificationManager.showNotification(this, message)
            loadMessages()
            Toast.makeText(this, "Фото отправлено", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun attachFile(uri: Uri) {
        try {
            val currentUser = UserManager.getCurrentUser() ?: return
            val trainerUserId = trainer.userId.ifEmpty { trainer.id }
            
            val inputStream = contentResolver.openInputStream(uri)
            val attachmentId = UUID.randomUUID().toString()
            val originalFileName = getFileName(uri)
            val fileExtension = originalFileName.substringAfterLast(".", "file")
            val attachmentFileName = "chat_file_${currentUser.id}_$attachmentId.$fileExtension"
            val attachmentFile = File(filesDir, attachmentFileName)
            
            FileOutputStream(attachmentFile).use { out ->
                inputStream?.copyTo(out)
            }
            inputStream?.close()

            val message = Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                senderId = currentUser.id,
                receiverId = trainerUserId,
                text = "📎 $originalFileName",
                timestamp = System.currentTimeMillis(),
                isRead = false,
                attachmentType = "file",
                attachmentPath = attachmentFileName
            )

            JsonDatabase.saveMessage(message)
            NotificationManager.showNotification(this, message)
            loadMessages()
            Toast.makeText(this, "Файл отправлен", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки файла", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "file"
    }

    private fun sendMessage() {
        val text = binding.messageEditText.text.toString().trim()
        if (text.isEmpty()) return

        val currentUser = UserManager.getCurrentUser() ?: return
        
        // Проверяем, не пытается ли пользователь писать самому себе
        val trainerUserId = trainer.userId.ifEmpty { trainer.id }
        if (currentUser.id == trainerUserId) {
            Toast.makeText(this, "Вы не можете писать самому себе", Toast.LENGTH_SHORT).show()
            return
        }

        val message = Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = currentUser.id,
            receiverId = trainerUserId,
            text = text,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )

        JsonDatabase.saveMessage(message)
        
        // Показываем уведомление получателю
        NotificationManager.showNotification(this, message)
        
        // Убрано автоматическое сообщение для тренера
        
        binding.messageEditText.text?.clear()
        loadMessages()
    }

    private fun markChatMessagesAsRead() {
        val currentUser = UserManager.getCurrentUser() ?: return
        val allMessages = JsonDatabase.getAllMessages().toMutableList()
        var hasChanges = false
        
        // Помечаем все непрочитанные сообщения в этом чате как прочитанные
        for (i in allMessages.indices) {
            val message = allMessages[i]
            if (!message.isGroupMessage && 
                message.chatId == chatId && 
                message.receiverId == currentUser.id && 
                !message.isRead) {
                allMessages[i] = message.copy(isRead = true)
                hasChanges = true
            }
        }
        
        if (hasChanges) {
            JsonDatabase.saveMessages(allMessages)
        }
    }
    
    private fun loadMessages() {
        val messages = JsonDatabase.getMessages(chatId)
        adapter.messages = messages
        adapter.notifyDataSetChanged()
        if (messages.isNotEmpty()) {
            binding.messagesRecyclerView.scrollToPosition(adapter.itemCount - 1)
        }
        
        // Проверяем запросы на запись для тренера
        val currentUser = UserManager.getCurrentUser()
        if (currentUser != null && currentUser.id == trainer.id) {
            checkPendingMeetings()
        }
    }
    
    private fun checkPendingMeetings() {
        // Убрана автоматическая проверка - теперь тренер подтверждает вручную через кнопки
    }
    
    private fun confirmMeeting(meeting: Meeting, accepted: Boolean) {
        val currentUser = UserManager.getCurrentUser() ?: return
        
        // Обновляем статус записи
        val status = if (accepted) "confirmed" else "rejected"
        val updatedMeeting = meeting.copy(
            status = status,
            trainerSelectedDate = if (accepted) meeting.date else null,
            trainerSelectedTime = if (accepted) meeting.time else null,
            isPaid = if (accepted) meeting.isPaid else false,
            amountPaid = if (accepted) meeting.amountPaid else 0
        )
        
        val allMeetings = JsonDatabase.getAllMeetings().toMutableList()
        val index = allMeetings.indexOfFirst { it.id == meeting.id }
        if (index != -1) {
            allMeetings[index] = updatedMeeting
            JsonDatabase.saveMeetings(allMeetings)
        }
        
        // Отправляем сообщение пользователю с meetingId для правильного отображения
        var statusText = if (accepted) {
            "✅ Запись принята!\nДата: ${meeting.date}\nВремя: ${meeting.time}"
        } else {
            "❌ Запись отклонена\nДата: ${meeting.date}\nВремя: ${meeting.time}"
        }

        if (!accepted && meeting.isPaid && meeting.amountPaid > 0) {
            val refundedUser = JsonDatabase.getUserById(meeting.userId)
            if (refundedUser != null) {
                val updatedUser = JsonDatabase.updateUserBalance(refundedUser.id, refundedUser.balance + meeting.amountPaid)
                if (updatedUser != null && UserManager.getCurrentUser()?.id == updatedUser.id) {
                    UserManager.setCurrentUser(updatedUser)
                }
            }
            statusText += "\nОплата возвращена на баланс: +${meeting.amountPaid} ₽"
        }
        
        val message = Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = "system", // Системное сообщение
            receiverId = meeting.userId,
            text = statusText,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            meetingId = meeting.id // Include meetingId so it shows as meeting request with proper color
        )
        JsonDatabase.saveMessage(message)
        loadMessages()
    }

    private fun releasePaymentForMeeting(meeting: Meeting) {
        val currentUser = UserManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentUser.id != meeting.userId) {
            Toast.makeText(this, "Только клиент может подтвердить оплату", Toast.LENGTH_SHORT).show()
            return
        }

        if (!MeetingUtils.isMeetingInPast(meeting)) {
            Toast.makeText(this, "Оплату можно подтвердить после завершения занятия", Toast.LENGTH_SHORT).show()
            return
        }

        val result = JsonDatabase.releasePaymentForMeeting(meeting.id)
        if (result != null) {
            val (updatedMeeting, updatedTrainer) = result
            updatedTrainer?.let {
                if (UserManager.getCurrentUser()?.id == it.id) {
                    UserManager.setCurrentUser(it)
                }
            }

            val trainerOwnerId = trainer.userId.ifEmpty { trainer.id }
            val confirmationMessage = Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                senderId = currentUser.id,
                receiverId = trainerOwnerId,
                text = "💰 Клиент подтвердил оплату за занятие: ${updatedMeeting.amountPaid} ₽",
                timestamp = System.currentTimeMillis(),
                isRead = false,
                meetingId = meeting.id
            )
            JsonDatabase.saveMessage(confirmationMessage)
            NotificationManager.showNotification(this, confirmationMessage)

            Toast.makeText(this, getString(R.string.release_payment_success), Toast.LENGTH_SHORT).show()
            loadMessages()
        } else {
            Toast.makeText(this, getString(R.string.release_payment_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyPeerPhotoToToolbar() {
        // Проверяем аватарку пользователя
        val user = JsonDatabase.getUserById(otherUserId)
        val avatarPath = user?.avatar?.takeIf { it.isNotEmpty() }
        
        val bitmap = if (avatarPath != null) {
            val avatarFile = File(filesDir, avatarPath)
            if (avatarFile.exists()) {
                BitmapFactory.decodeFile(avatarFile.absolutePath)
            } else {
                null
            }
        } else {
            // Fallback на старый способ
            val photoFile = File(filesDir, "profile_photo_${otherUserId}.jpg")
            if (photoFile.exists()) {
                BitmapFactory.decodeFile(photoFile.absolutePath)
            } else {
                null
            }
        }
        
        if (bitmap == null) {
            binding.toolbar.logo = null
            return
        }
        
        val size = resources.getDimensionPixelSize(R.dimen.toolbar_logo_size)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
        
        // Создаем круглый bitmap
        val circularBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(circularBitmap)
        val paint = android.graphics.Paint()
        paint.isAntiAlias = true
        
        // Рисуем круглую маску
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
        
        binding.toolbar.logo = BitmapDrawable(resources, circularBitmap)
    }

    private fun openPeerProfile() {
        val user = JsonDatabase.getUserById(otherUserId)
        if (user != null) {
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("viewUserId", user.id)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Профиль пользователя не найден", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val fromAdmin = intent.getBooleanExtra("fromAdmin", false)
        if (fromAdmin) {
            // Возвращаемся в админ панель
            val intent = Intent(this, AdminActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        val fromAdmin = intent.getBooleanExtra("fromAdmin", false)
        if (fromAdmin) {
            // Возвращаемся в админ панель
            val intent = Intent(this, AdminActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
            return true
        }
        finish()
        return true
    }
}


