package com.example.kursach

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kursach.adapter.MessageAdapter
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase
import com.example.kursach.databinding.ActivityMessengerBinding
import com.example.kursach.model.GroupChat
import com.example.kursach.model.Message
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class GroupMessengerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessengerBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var groupChat: GroupChat
    private lateinit var groupChatId: String
    private lateinit var currentUserId: String
    private var fromAdmin: Boolean = false
    
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

    private val pickGroupPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            updateGroupPhoto(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessengerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val groupChatIdFromIntent = intent.getStringExtra("groupChatId")
        if (groupChatIdFromIntent == null) {
            Toast.makeText(this, "Ошибка загрузки группы", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        groupChatId = groupChatIdFromIntent
        fromAdmin = intent.getBooleanExtra("fromAdmin", false)

        val currentUser = UserManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        currentUserId = currentUser.id

        groupChat = JsonDatabase.getGroupChatById(groupChatId) ?: run {
            Toast.makeText(this, "Группа не найдена", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Для тех админа пропускаем проверку участника
        if (!fromAdmin) {
            // Проверяем, является ли пользователь участником группы
            if (!groupChat.members.contains(currentUser.id)) {
                Toast.makeText(this, "Вы не являетесь участником этой группы", Toast.LENGTH_SHORT).show()
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
            supportActionBar?.setDisplayShowHomeEnabled(false)
            binding.toolbar.navigationIcon = null
        } else {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
        }
        supportActionBar?.title = groupChat.name
        supportActionBar?.setDisplayUseLogoEnabled(true)
        applyGroupPhotoToToolbar()
        
        // Для тех админа отключаем отправку сообщений
        if (fromAdmin) {
            binding.sendButton.isEnabled = false
            binding.messageEditText.isEnabled = false
            binding.attachButton.isEnabled = false
        }

        setupRecyclerView()
        setupSendButton()
        setupAttachButton()
        
        // Помечаем все непрочитанные сообщения в этой группе как прочитанные
        markGroupMessagesAsRead()
        
        loadMessages()
    }

    override fun onResume() {
        super.onResume()
        applyGroupPhotoToToolbar()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.group_chat_menu, menu)
        // Для тех админа скрываем все действия
        if (fromAdmin) {
            menu?.findItem(R.id.menu_change_photo)?.isVisible = false
            menu?.findItem(R.id.menu_delete_group)?.isVisible = false
            menu?.findItem(R.id.menu_leave_group)?.isVisible = false
            menu?.findItem(R.id.menu_invite_member)?.isVisible = false
            menu?.findItem(R.id.menu_report_chat)?.isVisible = false
        } else {
            menu?.findItem(R.id.menu_change_photo)?.isVisible = currentUserId == groupChat.createdBy
            menu?.findItem(R.id.menu_delete_group)?.isVisible = currentUserId == groupChat.createdBy
            menu?.findItem(R.id.menu_leave_group)?.isVisible = currentUserId != groupChat.createdBy
        }
        return true
    }
    
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        binding.toolbar.overflowIcon?.setTint(androidx.core.content.ContextCompat.getColor(this, android.R.color.white))
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_invite_member -> {
                showInviteMemberDialog()
                true
            }
            R.id.menu_change_photo -> {
                pickGroupPhotoLauncher.launch("image/*")
                true
            }
            R.id.menu_leave_group -> {
                showLeaveGroupDialog()
                true
            }
            R.id.menu_delete_group -> {
                showDeleteGroupDialog()
                true
            }
            R.id.menu_report_chat -> {
                showReportChatDialog()
                true
            }
            R.id.menu_edit_group_name -> {
                showEditGroupNameDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showLeaveGroupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Выйти из группы")
            .setMessage("Вы уверены, что хотите выйти из группы?")
            .setPositiveButton("Выйти") { _, _ ->
                if (JsonDatabase.leaveGroupChat(groupChat.id, currentUserId)) {
                    Toast.makeText(this, "Вы вышли из группы", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Не удалось выйти из группы", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun showDeleteGroupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Удалить группу")
            .setMessage("Вы уверены, что хотите удалить эту группу? Все сообщения будут удалены.")
            .setPositiveButton("Удалить") { _, _ ->
                if (JsonDatabase.deleteGroupChat(groupChat.id)) {
                    Toast.makeText(this, "Группа удалена", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Не удалось удалить группу", Toast.LENGTH_SHORT).show()
                }
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
                id = java.util.UUID.randomUUID().toString(),
                reporterId = currentUser.id,
                reporterName = currentUser.name,
                targetId = groupChat.id,
                targetType = com.example.kursach.model.ReportTargetType.CHAT,
                targetName = groupChat.name,
                reason = reason,
                status = com.example.kursach.model.ReportStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                chatType = "group"
            )
            
            JsonDatabase.createReport(report)
            Toast.makeText(this, "Жалоба отправлена администратору", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showEditGroupNameDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_group_name, null)
        val nameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.nameInput)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)
        
        nameInput.setText(groupChat.name)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Изменить название группы")
            .create()
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnSave.setOnClickListener {
            val newName = nameInput.text?.toString()?.trim() ?: ""
            if (newName.isEmpty()) {
                Toast.makeText(this, "Название не может быть пустым", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            groupChat = groupChat.copy(name = newName)
            JsonDatabase.saveGroupChat(groupChat)
            supportActionBar?.title = newName
            Toast.makeText(this, "Название группы изменено", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(
            emptyList(), 
            null, 
            true, // isGroupChat = true
            null,
            null,
            onEditMessage = { message ->
                showEditMessageDialog(message)
            },
            onDeleteMessage = { message ->
                deleteMessage(message)
            },
            onUserProfileClick = { userId ->
                openUserProfile(userId)
            }
        )
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
            
            val attachmentId = UUID.randomUUID().toString()
            val attachmentFileName = "group_chat_image_${currentUser.id}_$attachmentId.jpg"
            val attachmentFile = File(filesDir, attachmentFileName)
            
            FileOutputStream(attachmentFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val message = Message(
                id = UUID.randomUUID().toString(),
                chatId = "group_$groupChatId",
                senderId = currentUser.id,
                receiverId = groupChatId,
                text = "📷",
                timestamp = System.currentTimeMillis(),
                isRead = false,
                attachmentType = "image",
                attachmentPath = attachmentFileName,
                isGroupMessage = true
            )

            JsonDatabase.saveMessage(message)
            loadMessages()
            Toast.makeText(this, "Фото отправлено", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateGroupPhoto(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                val existingPath = groupChat.photoPath.orEmpty()
                val fileName = if (existingPath.isNotEmpty()) {
                    existingPath
                } else {
                    "group_photo_${groupChat.id}.jpg"
                }
                val photoFile = File(filesDir, fileName)
                FileOutputStream(photoFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                groupChat = groupChat.copy(photoPath = fileName)
                JsonDatabase.saveGroupChat(groupChat)
                applyGroupPhotoToToolbar()
                Toast.makeText(this, "Фото группы обновлено", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyGroupPhotoToToolbar() {
        val photoPath = groupChat.photoPath?.takeIf { it.isNotBlank() }
        if (photoPath == null) {
            binding.toolbar.logo = null
            return
        }
        val photoFile = File(filesDir, photoPath)
        if (!photoFile.exists()) {
            binding.toolbar.logo = null
            return
        }
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath) ?: run {
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
    
    private fun attachFile(uri: Uri) {
        try {
            val currentUser = UserManager.getCurrentUser() ?: return
            
            val inputStream = contentResolver.openInputStream(uri)
            val attachmentId = UUID.randomUUID().toString()
            val originalFileName = getFileName(uri)
            val fileExtension = originalFileName.substringAfterLast(".", "file")
            val attachmentFileName = "group_chat_file_${currentUser.id}_$attachmentId.$fileExtension"
            val attachmentFile = File(filesDir, attachmentFileName)
            
            FileOutputStream(attachmentFile).use { out ->
                inputStream?.copyTo(out)
            }
            inputStream?.close()

            val message = Message(
                id = UUID.randomUUID().toString(),
                chatId = "group_$groupChatId",
                senderId = currentUser.id,
                receiverId = groupChatId,
                text = "📎 $originalFileName",
                timestamp = System.currentTimeMillis(),
                isRead = false,
                attachmentType = "file",
                attachmentPath = attachmentFileName,
                isGroupMessage = true
            )

            JsonDatabase.saveMessage(message)
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

        val message = Message(
            id = UUID.randomUUID().toString(),
            chatId = "group_$groupChatId",
            senderId = currentUser.id,
            receiverId = groupChatId,
            text = text,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            isGroupMessage = true
        )

        JsonDatabase.saveMessage(message)
        
        // Показываем уведомление получателям
        NotificationManager.showNotification(this, message)
        
        binding.messageEditText.text?.clear()
        loadMessages()
    }

    private fun markGroupMessagesAsRead() {
        val currentUser = UserManager.getCurrentUser() ?: return
        val allMessages = JsonDatabase.getAllMessages().toMutableList()
        var hasChanges = false
        
        // Помечаем все непрочитанные сообщения в этой группе как прочитанные
        for (i in allMessages.indices) {
            val message = allMessages[i]
            if (message.isGroupMessage && 
                message.receiverId == groupChatId && 
                message.senderId != currentUser.id && 
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
        val messages = JsonDatabase.getAllMessages()
            .filter { it.isGroupMessage && it.receiverId == groupChatId }
            .sortedBy { it.timestamp }
        
        adapter.messages = messages
        adapter.notifyDataSetChanged()
        if (messages.isNotEmpty()) {
            binding.messagesRecyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }
    
    private fun showInviteMemberDialog() {
        val currentUser = UserManager.getCurrentUser() ?: return
        
        // Получаем всех пользователей, с которыми есть чаты, но которых нет в группе
        val chats = JsonDatabase.getChatsForUser(currentUser.id)
        val availableUserIds = mutableSetOf<String>()
        
        for (chat in chats) {
            if (!chat.isGroupChat) {
                val messages = JsonDatabase.getMessages(chat.chatId)
                if (messages.isNotEmpty()) {
                    val firstMessage = messages.first()
                    val otherUserId = if (firstMessage.senderId == currentUser.id) {
                        firstMessage.receiverId
                    } else {
                        firstMessage.senderId
                    }
                    if (otherUserId != currentUser.id && !groupChat.members.contains(otherUserId)) {
                        availableUserIds.add(otherUserId)
                    }
                }
            }
        }
        
        if (availableUserIds.isEmpty()) {
            Toast.makeText(this, "Нет доступных пользователей для приглашения", Toast.LENGTH_SHORT).show()
            return
        }
        
        val availableUsers = availableUserIds.mapNotNull { userId ->
            val user = JsonDatabase.getUserById(userId)
            if (user != null) {
                user.name to user.id
            } else {
                null
            }
        }
        
        val userNames = availableUsers.map { it.first }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Пригласить участника")
            .setItems(userNames) { _, which ->
                val selectedUserId = availableUsers[which].second
                inviteMember(selectedUserId)
            }
            .show()
    }
    
    private fun inviteMember(userId: String) {
        val updatedMembers = groupChat.members.toMutableList()
        if (!updatedMembers.contains(userId)) {
            updatedMembers.add(userId)
            val updatedGroupChat = groupChat.copy(members = updatedMembers)
            JsonDatabase.saveGroupChat(updatedGroupChat)
            groupChat = updatedGroupChat
            
            val currentUser = UserManager.getCurrentUser() ?: return
            val invitedUser = JsonDatabase.getUserById(userId)
            
            // Отправляем системное сообщение о приглашении
            val message = Message(
                id = UUID.randomUUID().toString(),
                chatId = "group_$groupChatId",
                senderId = currentUser.id,
                receiverId = groupChatId,
                text = "👤 ${invitedUser?.name ?: "Пользователь"} добавлен в группу",
                timestamp = System.currentTimeMillis(),
                isRead = false,
                isGroupMessage = true
            )
            JsonDatabase.saveMessage(message)
            loadMessages()
            
            Toast.makeText(this, "Участник приглашен", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (fromAdmin) {
            // Возвращаемся в админ панель
            val intent = Intent(this, AdminActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        } else {
            finish()
        }
        return true
    }
    
    override fun onBackPressed() {
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
}

