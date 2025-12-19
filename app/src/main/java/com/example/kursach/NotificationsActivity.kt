package com.example.kursach

import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase
import com.example.kursach.databinding.ActivityNotificationsBinding
import com.example.kursach.databinding.ItemNotificationBinding
import com.example.kursach.model.Message

class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var adapter: NotificationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentUser = UserManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Уведомления"

        binding.btnMarkAllRead.setOnClickListener {
            markAllNotificationsAsRead()
        }

        setupRecyclerView()
        loadNotifications()
    }

    private fun setupRecyclerView() {
        adapter = NotificationsAdapter(emptyList()) { notification ->
            openChatFromNotification(notification)
        }
        binding.notificationsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.notificationsRecyclerView.adapter = adapter
    }

    private fun loadNotifications() {
        val currentUser = UserManager.getCurrentUser() ?: return
        
        // Получаем все непрочитанные сообщения
        val allMessages = JsonDatabase.getAllMessages()
        val unreadMessages = allMessages.filter { message ->
            // Обычные сообщения
            (!message.isGroupMessage && message.receiverId == currentUser.id && !message.isRead) ||
            // Групповые сообщения
            (message.isGroupMessage && message.senderId != currentUser.id && !message.isRead && 
             JsonDatabase.getGroupChatById(message.receiverId)?.members?.contains(currentUser.id) == true)
        }.sortedByDescending { it.timestamp }
        
        val notifications = unreadMessages.map { message ->
            val sender = JsonDatabase.getUserById(message.senderId)
            val senderName = sender?.name ?: "Пользователь"
            
            val isGroup = message.isGroupMessage
            val chatName = if (isGroup) {
                val groupChat = JsonDatabase.getGroupChatById(message.receiverId)
                groupChat?.name ?: "Группа"
            } else {
                senderName
            }
            
            NotificationItem(
                message = message,
                title = if (isGroup) "Сообщение в группе: $chatName" else "Новое сообщение от $senderName",
                text = message.text,
                timestamp = message.timestamp
            )
        }
        
        adapter.updateNotifications(notifications)
        
        if (notifications.isEmpty()) {
            binding.emptyNotificationsText.visibility = android.view.View.VISIBLE
            binding.notificationsRecyclerView.visibility = android.view.View.GONE
        } else {
            binding.emptyNotificationsText.visibility = android.view.View.GONE
            binding.notificationsRecyclerView.visibility = android.view.View.VISIBLE
        }
    }
    
    private fun openChatFromNotification(notification: NotificationItem) {
        val message = notification.message
        
        if (message.isGroupMessage) {
            val intent = android.content.Intent(this, GroupMessengerActivity::class.java)
            intent.putExtra("groupChatId", message.receiverId)
            startActivity(intent)
        } else {
            val trainer = JsonDatabase.getTrainers().find { 
                it.userId.ifEmpty { it.id } == message.senderId 
            }
            if (trainer != null) {
                val intent = android.content.Intent(this, MessengerActivity::class.java)
                intent.putExtra("trainer", trainer)
                startActivity(intent)
            } else {
                val intent = android.content.Intent(this, ChatsListActivity::class.java)
                startActivity(intent)
            }
        }
        
        // Помечаем сообщение как прочитанное
        val allMessages = JsonDatabase.getAllMessages().toMutableList()
        val index = allMessages.indexOfFirst { it.id == message.id }
        if (index != -1) {
            allMessages[index] = message.copy(isRead = true)
            JsonDatabase.saveMessages(allMessages)
            loadNotifications()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    override fun onResume() {
        super.onResume()
        loadNotifications()
    }
    
    private fun markAllNotificationsAsRead() {
        val currentUser = UserManager.getCurrentUser() ?: return
        
        val allMessages = JsonDatabase.getAllMessages().toMutableList()
        var hasChanges = false
        
        for (i in allMessages.indices) {
            val message = allMessages[i]
            // Обычные сообщения
            if (!message.isGroupMessage && message.receiverId == currentUser.id && !message.isRead) {
                allMessages[i] = message.copy(isRead = true)
                hasChanges = true
            }
            // Групповые сообщения
            else if (message.isGroupMessage && message.senderId != currentUser.id && !message.isRead) {
                val groupChat = JsonDatabase.getGroupChatById(message.receiverId)
                if (groupChat?.members?.contains(currentUser.id) == true) {
                    allMessages[i] = message.copy(isRead = true)
                    hasChanges = true
                }
            }
        }
        
        if (hasChanges) {
            JsonDatabase.saveMessages(allMessages)
            Toast.makeText(this, "Все уведомления отмечены как прочитанные", Toast.LENGTH_SHORT).show()
            loadNotifications()
        } else {
            Toast.makeText(this, "Нет непрочитанных уведомлений", Toast.LENGTH_SHORT).show()
        }
    }

    data class NotificationItem(
        val message: Message,
        val title: String,
        val text: String,
        val timestamp: Long
    )

    private class NotificationsAdapter(
        private var notifications: List<NotificationItem>,
        private val onNotificationClick: (NotificationItem) -> Unit
    ) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

        class NotificationViewHolder(val binding: ItemNotificationBinding)
            : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
            val binding = ItemNotificationBinding.inflate(
                android.view.LayoutInflater.from(parent.context),
                parent,
                false
            )
            return NotificationViewHolder(binding)
        }

        override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
            val notification = notifications[position]
            
            holder.binding.notificationTitle.text = notification.title
            holder.binding.notificationText.text = notification.text
            
            // Форматируем время
            val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val dateFormat = java.text.SimpleDateFormat("dd.MM.yy HH:mm", java.util.Locale.getDefault())
            val now = System.currentTimeMillis()
            val messageTime = notification.timestamp
            
            val timeText = if (now - messageTime < 86400000) { // Меньше суток
                timeFormat.format(java.util.Date(messageTime))
            } else {
                dateFormat.format(java.util.Date(messageTime))
            }
            holder.binding.notificationTime.text = timeText
            
            holder.itemView.setOnClickListener {
                onNotificationClick(notification)
            }
        }

        override fun getItemCount(): Int = notifications.size

        fun updateNotifications(newNotifications: List<NotificationItem>) {
            notifications = newNotifications
            notifyDataSetChanged()
        }
    }
}

