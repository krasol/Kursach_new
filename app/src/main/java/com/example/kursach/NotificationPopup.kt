package com.example.kursach

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase
import com.example.kursach.databinding.PopupNotificationsBinding
import com.example.kursach.model.Message

class NotificationPopup(private val context: android.content.Context) {
    
    private var popupWindow: PopupWindow? = null
    private lateinit var binding: PopupNotificationsBinding
    private lateinit var adapter: NotificationPopupAdapter
    
    fun show(anchorView: View) {
        val currentUser = UserManager.getCurrentUser() ?: return
        
        // Создаем binding
        binding = PopupNotificationsBinding.inflate(LayoutInflater.from(context))
        
        // Настраиваем RecyclerView
        setupRecyclerView()
        
        // Загружаем уведомления
        loadNotifications()
        
        // Создаем PopupWindow
        popupWindow = PopupWindow(
            binding.root,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 8f
        }
        
        // Показываем popup под иконкой, выравнивая справа
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val popupWidth = binding.root.width
        
        // Выравниваем popup справа от иконки
        val xOffset = anchorView.width - popupWidth
        val yOffset = anchorView.height
        
        popupWindow?.showAsDropDown(anchorView, xOffset, yOffset, Gravity.END)
    }
    
    private fun setupRecyclerView() {
        adapter = NotificationPopupAdapter(emptyList()) { notification ->
            openChatFromNotification(notification)
            dismiss()
        }
        binding.notificationsRecyclerView.layoutManager = LinearLayoutManager(context)
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
        
        // Обновляем счетчик
        binding.notificationsCount.text = notifications.size.toString()
        
        if (notifications.isEmpty()) {
            binding.emptyNotificationsText.visibility = View.VISIBLE
            binding.notificationsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyNotificationsText.visibility = View.GONE
            binding.notificationsRecyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun openChatFromNotification(notification: NotificationItem) {
        val message = notification.message
        
        if (message.isGroupMessage) {
            val intent = android.content.Intent(context, GroupMessengerActivity::class.java)
            intent.putExtra("groupChatId", message.receiverId)
            context.startActivity(intent)
        } else {
            val trainer = JsonDatabase.getTrainers().find { 
                it.userId.ifEmpty { it.id } == message.senderId 
            }
            if (trainer != null) {
                val intent = android.content.Intent(context, MessengerActivity::class.java)
                intent.putExtra("trainer", trainer)
                context.startActivity(intent)
            } else {
                val intent = android.content.Intent(context, ChatsListActivity::class.java)
                context.startActivity(intent)
            }
        }
        
        // Помечаем сообщение как прочитанное
        val allMessages = JsonDatabase.getAllMessages().toMutableList()
        val index = allMessages.indexOfFirst { it.id == message.id }
        if (index != -1) {
            allMessages[index] = message.copy(isRead = true)
            JsonDatabase.saveMessages(allMessages)
        }
    }
    
    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }
    
    fun isShowing(): Boolean {
        return popupWindow?.isShowing == true
    }
    
    data class NotificationItem(
        val message: Message,
        val title: String,
        val text: String,
        val timestamp: Long
    )
    
    private class NotificationPopupAdapter(
        private var notifications: List<NotificationItem>,
        private val onNotificationClick: (NotificationItem) -> Unit
    ) : RecyclerView.Adapter<NotificationPopupAdapter.NotificationViewHolder>() {

        class NotificationViewHolder(val binding: com.example.kursach.databinding.ItemNotificationBinding)
            : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
            val binding = com.example.kursach.databinding.ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.context),
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

