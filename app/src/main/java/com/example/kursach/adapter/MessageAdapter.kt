package com.example.kursach.adapter

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.example.kursach.R
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase
import com.example.kursach.model.Message
import com.example.kursach.model.Meeting
import com.example.kursach.utils.MeetingUtils
import com.google.android.material.button.MaterialButton
import java.io.File

class MessageAdapter(
    var messages: List<Message>,
    private val onMeetingAction: ((Meeting, Boolean) -> Unit)? = null,
    private val isGroupChat: Boolean = false,
    private val onReleasePayment: ((Meeting) -> Unit)? = null,
    private val peerName: String? = null, // Имя собеседника для системных сообщений
    private val onEditMessage: ((Message) -> Unit)? = null, // Callback для редактирования сообщения
    private val onDeleteMessage: ((Message) -> Unit)? = null, // Callback для удаления сообщения
    private val onUserProfileClick: ((String) -> Unit)? = null // Callback для открытия профиля пользователя
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val currentUserId = UserManager.getCurrentUser()?.id

    companion object {
        private const val TYPE_SENT = 0
        private const val TYPE_RECEIVED = 1
        private const val TYPE_MEETING_REQUEST = 2
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        val messageImage: ImageView = itemView.findViewById(R.id.messageImage)
    }

    class MeetingRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        val meetingStatus: TextView = itemView.findViewById(R.id.meetingStatus)
        val buttonsLayout: View = itemView.findViewById(R.id.buttonsLayout)
        val btnAccept: MaterialButton = itemView.findViewById(R.id.btnAccept)
        val btnReject: MaterialButton = itemView.findViewById(R.id.btnReject)
        val btnReleasePayment: MaterialButton = itemView.findViewById(R.id.btnReleasePayment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
                MessageViewHolder(view)
            }
            TYPE_MEETING_REQUEST -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_meeting_request, parent, false)
                MeetingRequestViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
                MessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(message.timestamp))

        when (holder) {
            is MessageViewHolder -> {
                holder.messageTime.text = time
                
                // Показываем "Сообщение удалено" если сообщение было удалено
                if (message.isDeleted) {
                    holder.messageText.text = "Сообщение удалено"
                    holder.messageText.alpha = 0.5f
                    holder.messageImage.visibility = View.GONE
                } else {
                    holder.messageText.alpha = 1.0f
                    
                    // Обрабатываем вложения (изображения и файлы)
                    if (message.attachmentType == "image" && message.attachmentPath != null) {
                        val imageFile = File(holder.itemView.context.filesDir, message.attachmentPath)
                        if (imageFile.exists()) {
                            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                            holder.messageImage.setImageBitmap(bitmap)
                            holder.messageImage.visibility = View.VISIBLE
                            holder.messageImage.setOnClickListener {
                                // Открываем изображение в PhotoViewerActivity
                                val intent = Intent(holder.itemView.context, com.example.kursach.PhotoViewerActivity::class.java)
                                intent.putStringArrayListExtra("photos", arrayListOf(message.attachmentPath))
                                intent.putExtra("position", 0)
                                holder.itemView.context.startActivity(intent)
                            }
                        } else {
                            holder.messageImage.visibility = View.GONE
                        }
                        var displayText = message.text
                        if (message.isEdited) {
                            displayText += " (изменено)"
                        }
                        holder.messageText.text = displayText
                    } else if (message.attachmentType == "file" && message.attachmentPath != null) {
                        holder.messageImage.visibility = View.GONE
                        var displayText = message.text
                        if (message.isEdited) {
                            displayText += " (изменено)"
                        }
                        holder.messageText.text = displayText
                    } else {
                        holder.messageImage.visibility = View.GONE
                        var displayText = message.text
                        if (message.isEdited) {
                            displayText += " (изменено)"
                        }
                        holder.messageText.text = displayText
                    }
                    
                    // Показываем метку "отредактировано" если сообщение было отредактировано
                    if (message.isEdited) {
                        val editedText = " (изменено)"
                        if (!holder.messageText.text.toString().endsWith(editedText)) {
                            holder.messageText.text = "${holder.messageText.text}$editedText"
                        }
                    }
                }
                
                // Для системных сообщений показываем имя собеседника
                if (message.senderId == "system") {
                    val senderNameView = holder.itemView.findViewById<TextView>(R.id.senderName)
                    if (senderNameView != null) {
                        // Используем имя собеседника, если оно передано, иначе получаем из БД
                        val displayName = peerName ?: run {
                            // Получаем имя собеседника из receiverId или из контекста чата
                            val otherUserId = if (message.receiverId == currentUserId) {
                                // Если получатель - текущий пользователь, ищем отправителя в других сообщениях
                                messages.firstOrNull { it.senderId != "system" && it.senderId != currentUserId }?.senderId
                            } else {
                                message.receiverId
                            }
                            val user = otherUserId?.let { JsonDatabase.getUserById(it) }
                            user?.name ?: "Пользователь"
                        }
                        senderNameView.text = displayName
                        senderNameView.visibility = View.VISIBLE
                        senderNameView.isClickable = true
                        senderNameView.isFocusable = true
                        senderNameView.setOnClickListener {
                            val otherUserId = if (message.receiverId == currentUserId) {
                                messages.firstOrNull { it.senderId != "system" && it.senderId != currentUserId }?.senderId
                            } else {
                                message.receiverId
                            }
                            otherUserId?.let { onUserProfileClick?.invoke(it) }
                        }
                    }
                }
                // Для групповых чатов показываем имя отправителя
                else if (isGroupChat && message.senderId != currentUserId) {
                    val senderNameView = holder.itemView.findViewById<TextView>(R.id.senderName)
                    if (senderNameView != null) {
                        val sender = JsonDatabase.getUserById(message.senderId)
                        val displayName = if (sender?.isBanned == true) {
                            "${sender.name} (забанен)"
                        } else {
                            sender?.name ?: "Пользователь"
                        }
                        senderNameView.text = displayName
                        senderNameView.visibility = View.VISIBLE
                        senderNameView.isClickable = true
                        senderNameView.isFocusable = true
                        senderNameView.setOnClickListener {
                            onUserProfileClick?.invoke(message.senderId)
                        }
                    }
                } else {
                    val senderNameView = holder.itemView.findViewById<TextView>(R.id.senderName)
                    senderNameView?.visibility = View.GONE
                }
                
                // Добавляем long click listener для редактирования, удаления и скачивания
                holder.itemView.setOnLongClickListener {
                    if (message.isDeleted) {
                        false
                    } else if (message.senderId == currentUserId && message.senderId != "system") {
                        // Для своих сообщений показываем меню редактирования/удаления/скачивания
                        showMessageOptionsMenu(holder.itemView, message)
                        true
                    } else if (message.attachmentType != null && message.attachmentPath != null) {
                        // Для сообщений с вложениями от других пользователей показываем только скачивание
                        showDownloadMenu(holder.itemView, message)
                        true
                    } else {
                        false
                    }
                }
            }
            is MeetingRequestViewHolder -> {
                holder.messageText.text = message.text
                holder.messageTime.text = time
                
                // Получаем запись
                val meeting = message.meetingId?.let { meetingId ->
                    JsonDatabase.getAllMeetings().find { it.id == meetingId }
                }
                
                if (meeting != null) {
                    val statusText = when (meeting.status) {
                        "confirmed" -> "Статус: Принята"
                        "rejected" -> "Статус: Отклонена"
                        else -> "Статус: На рассмотрении"
                    }
                    holder.meetingStatus.text = statusText
                    
                    // Set background color based on status
                    val cardView = holder.itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.meetingCardView)
                    if (cardView != null) {
                        when (meeting.status) {
                            "confirmed" -> {
                                cardView.setCardBackgroundColor(holder.itemView.context.getColor(R.color.success_color))
                            }
                            "rejected" -> {
                                cardView.setCardBackgroundColor(holder.itemView.context.getColor(R.color.error_color))
                            }
                            else -> {
                                cardView.setCardBackgroundColor(holder.itemView.context.getColor(R.color.message_received_bg))
                            }
                        }
                    }
                    
                    // Показываем кнопки только если статус pending и это тренер
                    // Check if current user is the trainer (receiver of the original request message)
                    // The original request message has senderId = userId, receiverId = trainerId
                    val isTrainer = currentUserId == message.receiverId
                    if (meeting.status == "pending" && isTrainer && onMeetingAction != null) {
                        holder.buttonsLayout.visibility = View.VISIBLE
                        holder.btnAccept.setOnClickListener {
                            onMeetingAction(meeting, true)
                        }
                        holder.btnReject.setOnClickListener {
                            onMeetingAction(meeting, false)
                        }
                    } else {
                        holder.buttonsLayout.visibility = View.GONE
                    }

                    val shouldShowRelease = currentUserId == meeting.userId &&
                            meeting.status == "confirmed" &&
                            meeting.isPaid &&
                            !meeting.isPaymentReleased &&
                            MeetingUtils.isMeetingInPast(meeting) &&
                            onReleasePayment != null

                    if (shouldShowRelease) {
                        holder.btnReleasePayment.visibility = View.VISIBLE
                        holder.btnReleasePayment.setOnClickListener {
                            onReleasePayment?.invoke(meeting)
                        }
                    } else {
                        holder.btnReleasePayment.visibility = View.GONE
                        holder.btnReleasePayment.setOnClickListener(null)
                    }
                } else {
                    holder.buttonsLayout.visibility = View.GONE
                    holder.btnReleasePayment.visibility = View.GONE
                    holder.btnReleasePayment.setOnClickListener(null)
                }
            }
        }
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        // Show meeting request view for any message with meetingId (both sent and received)
        return if (message.meetingId != null) {
            TYPE_MEETING_REQUEST
        } else if (message.senderId == "system") {
            // Системные сообщения показываем как полученные
            TYPE_RECEIVED
        } else if (message.senderId == currentUserId) {
            TYPE_SENT
        } else {
            TYPE_RECEIVED
        }
    }
    
    private fun downloadFile(context: android.content.Context, file: File, fileName: String, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            // Используем ACTION_SEND для всех версий Android
            // Это позволяет пользователю выбрать приложение для сохранения файла
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Сохранить файл"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Ошибка сохранения файла: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
    
    private fun showMessageOptionsMenu(view: View, message: Message) {
        val popupMenu = android.widget.PopupMenu(view.context, view)
        popupMenu.menuInflater.inflate(R.menu.message_options_menu, popupMenu.menu)
        
        // Показываем опцию скачивания только если есть вложение
        val downloadItem = popupMenu.menu.findItem(R.id.menu_download_attachment)
        downloadItem?.isVisible = message.attachmentType != null && message.attachmentPath != null
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_edit_message -> {
                    onEditMessage?.invoke(message)
                    true
                }
                R.id.menu_delete_message -> {
                    onDeleteMessage?.invoke(message)
                    true
                }
                R.id.menu_download_attachment -> {
                    downloadAttachment(message, view.context)
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }
    
    private fun showDownloadMenu(view: View, message: Message) {
        val popupMenu = android.widget.PopupMenu(view.context, view)
        popupMenu.menuInflater.inflate(R.menu.message_options_menu, popupMenu.menu)
        
        // Скрываем редактирование и удаление, показываем только скачивание
        popupMenu.menu.findItem(R.id.menu_edit_message)?.isVisible = false
        popupMenu.menu.findItem(R.id.menu_delete_message)?.isVisible = false
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_download_attachment -> {
                    downloadAttachment(message, view.context)
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }
    
    private fun downloadAttachment(message: Message, context: android.content.Context) {
        if (message.attachmentPath == null) return
        
        val file = File(context.filesDir, message.attachmentPath)
        if (!file.exists()) {
            android.widget.Toast.makeText(context, "Файл не найден", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        when (message.attachmentType) {
            "image" -> {
                downloadFile(context, file, "image_${message.id}.jpg", "image/jpeg")
            }
            "file" -> {
                val fileName = message.text.replace("📎 ", "")
                val mimeType = getMimeType(fileName)
                downloadFile(context, file, fileName, mimeType)
            }
        }
    }
}





