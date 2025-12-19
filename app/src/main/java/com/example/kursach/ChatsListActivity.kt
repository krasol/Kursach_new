package com.example.kursach

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kursach.adapter.ChatAdapter
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase
import com.example.kursach.databinding.ActivityChatsListBinding
import com.example.kursach.model.ChatInfo
import com.example.kursach.model.Trainer
import com.example.kursach.ui.BottomNavigationHelper

class ChatsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatsListBinding
    private lateinit var adapter: ChatAdapter
    private var notificationPopup: NotificationPopup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentUser = UserManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        binding.toolbar.navigationIcon = null
        // Устанавливаем цвет троеточия сразу
        binding.toolbar.post {
            binding.toolbar.overflowIcon?.setTint(ContextCompat.getColor(this, android.R.color.white))
        }

        BottomNavigationHelper.setup(binding.bottomNavigation, this, R.id.navigation_chats)

        setupNotificationsIcon()
        setupCreateProfileButton()
        setupRecyclerView()
        setupCreateGroupButton()
        loadChats()
    }
    
    private fun setupCreateProfileButton() {
        val currentUser = UserManager.getCurrentUser() ?: return
        if (currentUser.userType == com.example.kursach.model.UserType.TRAINER) {
            val createProfileIconView = android.widget.ImageView(this)
            createProfileIconView.setImageResource(android.R.drawable.ic_input_add)
            createProfileIconView.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
            createProfileIconView.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            createProfileIconView.setPadding(16, 16, 16, 16)
            
            val createProfileParams = androidx.appcompat.widget.Toolbar.LayoutParams(
                androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT,
                androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT
            )
            createProfileParams.gravity = android.view.Gravity.END
            createProfileParams.setMargins(0, 0, 16, 0)
            createProfileIconView.layoutParams = createProfileParams
            
            createProfileIconView.setOnClickListener {
                val intent = Intent(this, CreateTrainerProfileActivity::class.java)
                startActivity(intent)
            }
            
            // Вставляем кнопку создания анкеты перед иконкой уведомлений
            val notificationView = binding.toolbar.findViewById<android.view.View>(R.id.badge_text)?.parent as? android.view.View
            if (notificationView != null) {
                binding.toolbar.addView(createProfileIconView, binding.toolbar.indexOfChild(notificationView))
            } else {
                binding.toolbar.addView(createProfileIconView)
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu?.findItem(R.id.menu_create_profile)?.isVisible = false
        return true
    }
    
    override fun onPrepareOptionsMenu(menu: android.view.Menu?): Boolean {
        binding.toolbar.overflowIcon?.setTint(ContextCompat.getColor(this, android.R.color.white))
        return super.onPrepareOptionsMenu(menu)
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_terms -> {
                com.example.kursach.utils.DocumentUtils.showDocumentDialog(this, R.string.terms_title, R.raw.terms)
                true
            }
            R.id.menu_privacy -> {
                com.example.kursach.utils.DocumentUtils.showDocumentDialog(this, R.string.privacy_title, R.raw.privacy)
                true
            }
            R.id.menu_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupCreateGroupButton() {
        binding.btnCreateGroup.setOnClickListener {
            val intent = Intent(this, CreateGroupActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(emptyList()) { chatInfo ->
            openChat(chatInfo)
        }
        binding.chatsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatsRecyclerView.adapter = adapter
    }

    private fun loadChats() {
        val currentUser = UserManager.getCurrentUser() ?: return
        val chats = JsonDatabase.getChatsForUser(currentUser.id)
        
        if (chats.isEmpty()) {
            binding.emptyChatsTextView.visibility = TextView.VISIBLE
            binding.chatsRecyclerView.visibility = android.view.View.GONE
        } else {
            binding.emptyChatsTextView.visibility = TextView.GONE
            binding.chatsRecyclerView.visibility = android.view.View.VISIBLE
            adapter.updateChats(chats)
        }
    }

    private fun openChat(chatInfo: ChatInfo) {
        val currentUser = UserManager.getCurrentUser() ?: return
        
        // Если это групповой чат
        if (chatInfo.isGroupChat && chatInfo.groupChatId != null) {
            val intent = Intent(this, GroupMessengerActivity::class.java)
            intent.putExtra("groupChatId", chatInfo.groupChatId)
            startActivity(intent)
            return
        }
        
        // Обычный чат
        val messages = JsonDatabase.getMessages(chatInfo.chatId)
        val otherUserId = if (messages.isNotEmpty()) {
            val firstMessage = messages.first()
            if (firstMessage.senderId == currentUser.id) {
                firstMessage.receiverId
            } else {
                firstMessage.senderId
            }
        } else {
            chatInfo.trainerId
        }
        
        // Проверяем, не пытается ли пользователь открыть чат с самим собой
        if (currentUser.id == otherUserId) {
            Toast.makeText(this, "Вы не можете писать самому себе", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Ищем тренера по userId (для объединения чатов с одним пользователем)
        var trainer = JsonDatabase.getTrainers().find { 
            it.userId.ifEmpty { it.id } == otherUserId 
        }
        
        // Если тренер не найден, возможно это обычный пользователь
        if (trainer == null) {
            val otherUser = JsonDatabase.getUserById(otherUserId)
            if (otherUser != null) {
                // Создаем временный объект Trainer для обычного пользователя
                trainer = com.example.kursach.model.Trainer(
                    id = otherUser.id,
                    userId = otherUser.id,
                    name = otherUser.name,
                    category = "Пользователь",
                    hobbyName = "",
                    description = otherUser.description ?: "",
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
            }
        }
        
        if (trainer == null) {
            Toast.makeText(this, "Чат недоступен", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, MessengerActivity::class.java)
        intent.putExtra("trainer", trainer)
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupNotificationsIcon() {
        val currentUser = UserManager.getCurrentUser() ?: return
        
        // Создаем иконку уведомлений с бейджем
        val notificationIconView = android.view.LayoutInflater.from(this).inflate(R.layout.menu_notification_badge, null)
        val badgeText = notificationIconView.findViewById<android.widget.TextView>(R.id.badge_text)
        
        val unreadCount = NotificationManager.getUnreadCount(currentUser.id)
        if (unreadCount > 0) {
            badgeText.text = if (unreadCount > 99) "99+" else unreadCount.toString()
            badgeText.visibility = android.view.View.VISIBLE
        } else {
            badgeText.visibility = android.view.View.GONE
        }
        
        // Добавляем иконку в toolbar
        binding.toolbar.addView(notificationIconView)
        val params = notificationIconView.layoutParams as androidx.appcompat.widget.Toolbar.LayoutParams
        params.gravity = android.view.Gravity.END
        params.setMargins(0, 0, 16, 0)
        notificationIconView.layoutParams = params
        
        notificationIconView.setOnClickListener {
            showNotificationsPopup(notificationIconView)
        }
    }
    
    private fun showNotificationsPopup(anchorView: android.view.View) {
        if (notificationPopup?.isShowing() == true) {
            notificationPopup?.dismiss()
            return
        }
        
        notificationPopup = NotificationPopup(this)
        notificationPopup?.show(anchorView)
    }
    
    override fun onResume() {
        super.onResume()
        loadChats() // Обновляем список при возврате на экран
        updateNotificationsIcon()
    }
    
    private fun updateNotificationsIcon() {
        val currentUser = UserManager.getCurrentUser() ?: return
        val unreadCount = NotificationManager.getUnreadCount(currentUser.id)
        
        // Находим view уведомлений в toolbar
        val notificationView = binding.toolbar.findViewById<android.view.View>(R.id.badge_text)?.parent as? android.view.View
        if (notificationView != null) {
            val badgeText = notificationView.findViewById<android.widget.TextView>(R.id.badge_text)
            if (unreadCount > 0) {
                badgeText.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                badgeText.visibility = android.view.View.VISIBLE
            } else {
                badgeText.visibility = android.view.View.GONE
            }
        }
    }
}

