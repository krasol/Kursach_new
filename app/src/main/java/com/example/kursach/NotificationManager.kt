package com.example.kursach

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase
import com.example.kursach.model.Message

object NotificationManager {
    private const val CHANNEL_ID = "messages_channel"
    private const val CHANNEL_NAME = "Сообщения"
    private const val NOTIFICATION_ID = 1

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления о новых сообщениях"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(context: Context, message: Message) {
        // Показываем уведомление только получателю, не отправителю
        // Для групповых сообщений показываем всем участникам кроме отправителя
        // Для обычных сообщений показываем только получателю
        
        // Для групповых сообщений показываем всем участникам кроме отправителя
        if (message.isGroupMessage) {
            val groupChat = JsonDatabase.getGroupChatById(message.receiverId) ?: return
            groupChat.members.forEach { memberId ->
                if (memberId != message.senderId) {
                    showNotificationForUser(context, message, memberId, groupChat.name)
                }
            }
        } else {
            // Для обычных сообщений показываем только получателю
            if (message.senderId != message.receiverId) {
                showNotificationForUser(context, message, message.receiverId, null)
            }
        }
    }
    
    private fun showNotificationForUser(context: Context, message: Message, userId: String, groupName: String?) {
        // Показываем уведомление получателю (не проверяем currentUser, так как уведомления должны показываться даже если приложение в фоне)
        
        // Получаем имя отправителя
        val sender = JsonDatabase.getUserById(message.senderId)
        val senderName = sender?.name ?: "Пользователь"
        
        // Определяем, это групповой чат или обычный
        val isGroup = message.isGroupMessage
        
        // Создаем Intent для открытия чата
        val intent = if (isGroup) {
            Intent(context, GroupMessengerActivity::class.java).apply {
                putExtra("groupChatId", message.receiverId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            // Для обычного чата нужно найти тренера
            val trainer = JsonDatabase.getTrainers().find { 
                it.userId.ifEmpty { it.id } == message.senderId 
            }
            if (trainer != null) {
                Intent(context, MessengerActivity::class.java).apply {
                    putExtra("trainer", trainer)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else {
                Intent(context, ChatsListActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val chatName = groupName ?: (if (isGroup) {
            val groupChat = JsonDatabase.getGroupChatById(message.receiverId)
            groupChat?.name ?: "Группа"
        } else {
            senderName
        })
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(if (isGroup) "Сообщение в группе: $chatName" else "Новое сообщение от $senderName")
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Используем уникальный ID для каждого сообщения, чтобы показывать все уведомления
        val notificationId = message.id.hashCode()
        notificationManager.notify(notificationId, notification)
    }

    fun getUnreadCount(userId: String): Int {
        val allMessages = JsonDatabase.getAllMessages()
        return allMessages.count { message ->
            // Обычные сообщения
            (!message.isGroupMessage && message.receiverId == userId && !message.isRead) ||
            // Групповые сообщения
            (message.isGroupMessage && message.senderId != userId && !message.isRead && 
             JsonDatabase.getGroupChatById(message.receiverId)?.members?.contains(userId) == true)
        }
    }
}

