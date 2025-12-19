package com.example.kursach.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.kursach.R
import com.example.kursach.model.ChatInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private var chats: List<ChatInfo>,
    private val onChatClick: (ChatInfo) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val trainerName: TextView = itemView.findViewById(R.id.chatTrainerName)
        val lastMessage: TextView = itemView.findViewById(R.id.chatLastMessage)
        val lastMessageTime: TextView = itemView.findViewById(R.id.chatLastMessageTime)
        val unreadBadge: TextView = itemView.findViewById(R.id.chatUnreadBadge)
        val avatarImage: android.widget.ImageView = itemView.findViewById(R.id.chatAvatarImage)
        val avatarPlaceholder: TextView = itemView.findViewById(R.id.chatAvatarPlaceholder)
        val avatarCard: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.chatAvatarCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        
        // Проверяем, забанен ли пользователь
        val user = com.example.kursach.database.JsonDatabase.getUserById(chat.trainerId)
        val displayName = if (user?.isBanned == true) {
            "${chat.trainerName} (забанен)"
        } else {
            chat.trainerName
        }
        holder.trainerName.text = displayName
        
        holder.lastMessage.text = chat.lastMessage
        
        bindAvatar(holder, chat)
        
        // Форматируем время
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
        val now = System.currentTimeMillis()
        val messageTime = chat.lastMessageTime
        
        val timeText = if (now - messageTime < 86400000) { // Меньше суток
            timeFormat.format(Date(messageTime))
        } else {
            dateFormat.format(Date(messageTime))
        }
        holder.lastMessageTime.text = timeText
        
        // Показываем бейдж непрочитанных сообщений
        if (chat.unreadCount > 0) {
            holder.unreadBadge.visibility = View.VISIBLE
            holder.unreadBadge.text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
        } else {
            holder.unreadBadge.visibility = View.GONE
        }
        
        holder.itemView.setOnClickListener {
            onChatClick(chat)
        }
        
        if (chat.isGroupChat) {
            holder.trainerName.setOnClickListener(null)
            holder.avatarCard.setOnClickListener(null)
            holder.itemView.setOnLongClickListener(null)
        } else {
            val openProfile: (View) -> Unit = {
                openUserProfile(holder.itemView, chat.trainerId)
            }
            holder.trainerName.setOnClickListener(openProfile)
            holder.avatarCard.setOnClickListener(openProfile)
            holder.itemView.setOnLongClickListener {
                openUserProfile(holder.itemView, chat.trainerId)
                true
            }
        }
    }

    override fun getItemCount(): Int = chats.size

    fun updateChats(newChats: List<ChatInfo>) {
        chats = newChats
        notifyDataSetChanged()
    }

    private fun bindAvatar(holder: ChatViewHolder, chat: ChatInfo) {
        val context = holder.itemView.context
        if (chat.isGroupChat) {
            val groupChat = chat.groupChatId?.let { com.example.kursach.database.JsonDatabase.getGroupChatById(it) }
            val photoPath = groupChat?.photoPath.orEmpty()
            if (photoPath.isNotEmpty()) {
                val photoFile = File(context.filesDir, photoPath)
                if (photoFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    holder.avatarImage.setImageBitmap(bitmap)
                    holder.avatarImage.visibility = View.VISIBLE
                    holder.avatarPlaceholder.visibility = View.GONE
                    return
                }
            }
            holder.avatarImage.setImageDrawable(null)
            holder.avatarImage.visibility = View.GONE
            holder.avatarPlaceholder.visibility = View.VISIBLE
            holder.avatarPlaceholder.text = "👥"
        } else {
            val initials = getInitials(chat.trainerName)
            holder.avatarPlaceholder.text = initials.ifEmpty { "👤" }

            // Сначала пытаемся получить пользователя
            var user = com.example.kursach.database.JsonDatabase.getUserById(chat.trainerId)
            var avatarPath: String? = null
            
            // Если пользователь найден, проверяем его аватар
            if (user != null && user.avatar.isNotEmpty()) {
                avatarPath = user.avatar
            } else {
                // Если пользователь не найден, возможно это тренер
                val trainer = com.example.kursach.database.JsonDatabase.getTrainerById(chat.trainerId)
                if (trainer != null) {
                    // Проверяем аватар тренера
                    if (trainer.avatar.isNotEmpty()) {
                        avatarPath = trainer.avatar
                    } else {
                        // Пробуем найти пользователя по userId тренера
                        val trainerUser = com.example.kursach.database.JsonDatabase.getUserById(trainer.userId.ifEmpty { trainer.id })
                        if (trainerUser != null && trainerUser.avatar.isNotEmpty()) {
                            avatarPath = trainerUser.avatar
                        }
                    }
                }
            }
            
            // Если аватар не найден, пробуем старый формат для обратной совместимости
            if (avatarPath == null) {
                val oldPhotoFile = File(context.filesDir, "profile_photo_${chat.trainerId}.jpg")
                if (oldPhotoFile.exists()) {
                    avatarPath = "profile_photo_${chat.trainerId}.jpg"
                }
            }
            
            // Загружаем аватар, если найден
            if (avatarPath != null) {
                val photoFile = File(context.filesDir, avatarPath)
                if (photoFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    holder.avatarImage.setImageBitmap(bitmap)
                    holder.avatarImage.visibility = View.VISIBLE
                    holder.avatarPlaceholder.visibility = View.GONE
                } else {
                    holder.avatarImage.setImageDrawable(null)
                    holder.avatarImage.visibility = View.GONE
                    holder.avatarPlaceholder.visibility = View.VISIBLE
                }
            } else {
                holder.avatarImage.setImageDrawable(null)
                holder.avatarImage.visibility = View.GONE
                holder.avatarPlaceholder.visibility = View.VISIBLE
            }
        }
    }

    private fun openUserProfile(view: View, userId: String) {
        var user = com.example.kursach.database.JsonDatabase.getUserById(userId)
        if (user == null) {
            val trainer = com.example.kursach.database.JsonDatabase.getTrainerById(userId)
            if (trainer != null) {
                user = com.example.kursach.database.JsonDatabase.getUserById(trainer.userId.ifEmpty { trainer.id })
            }
        }

        if (user != null) {
            val intent = android.content.Intent(view.context, com.example.kursach.UserProfileActivity::class.java)
            intent.putExtra("viewUserId", user.id)
            view.context.startActivity(intent)
        } else {
            android.widget.Toast.makeText(view.context, "Пользователь не найден", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun getInitials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            parts.size == 1 && parts[0].isNotEmpty() -> parts[0].take(2).uppercase(Locale.getDefault())
            else -> ""
        }
    }
}






