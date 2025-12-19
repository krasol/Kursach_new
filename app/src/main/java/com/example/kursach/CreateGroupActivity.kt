package com.example.kursach

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase
import com.example.kursach.databinding.ActivityCreateGroupBinding
import com.example.kursach.databinding.ItemGroupMemberBinding
import com.example.kursach.model.GroupChat
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateGroupBinding
    private val selectedMembers = mutableSetOf<String>()
    private lateinit var membersAdapter: GroupMembersAdapter
    private var selectedGroupPhotoUri: Uri? = null

    private val pickGroupPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            setGroupPhoto(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
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
        supportActionBar?.title = "Создать группу"

        setupRecyclerView()
        loadAvailableMembers()
        
        binding.btnCreateGroup.setOnClickListener {
            createGroup()
        }

        binding.btnPickGroupPhoto.setOnClickListener {
            pickGroupPhotoLauncher.launch("image/*")
        }
    }

    private fun setupRecyclerView() {
        membersAdapter = GroupMembersAdapter(emptyList(), selectedMembers) { userId ->
            if (selectedMembers.contains(userId)) {
                selectedMembers.remove(userId)
            } else {
                selectedMembers.add(userId)
            }
            membersAdapter.updateMembers(membersAdapter.members)
        }
        binding.membersRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.membersRecyclerView.adapter = membersAdapter
    }

    private fun loadAvailableMembers() {
        val currentUser = UserManager.getCurrentUser() ?: return
        
        // Получаем всех пользователей, с которыми есть чаты
        val chats = JsonDatabase.getChatsForUser(currentUser.id)
        val userIds = mutableSetOf<String>()
        
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
                    if (otherUserId != currentUser.id) {
                        userIds.add(otherUserId)
                    }
                }
            }
        }
        
        val members = userIds.mapNotNull { userId ->
            val user = JsonDatabase.getUserById(userId)
            if (user != null) {
                MemberItem(user.id, user.name)
            } else {
                null
            }
        }
        
        membersAdapter.updateMembers(members)
    }

    private fun createGroup() {
        val currentUser = UserManager.getCurrentUser() ?: return
        val groupName = binding.groupNameInput.text.toString().trim()
        
        if (groupName.isEmpty()) {
            Toast.makeText(this, "Введите название группы", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedMembers.isEmpty()) {
            Toast.makeText(this, "Выберите хотя бы одного участника", Toast.LENGTH_SHORT).show()
            return
        }
        
        val allMembers = mutableListOf(currentUser.id)
        allMembers.addAll(selectedMembers)

        val groupId = UUID.randomUUID().toString()
        val photoPath = saveGroupPhoto(groupId)

        val groupChat = GroupChat(
            id = groupId,
            name = groupName,
            createdBy = currentUser.id,
            members = allMembers,
            photoPath = photoPath
        )
        
        JsonDatabase.saveGroupChat(groupChat)
        
        Toast.makeText(this, "Группа создана", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun setGroupPhoto(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                selectedGroupPhotoUri = uri
                binding.groupPhotoImageView.setImageBitmap(bitmap)
                binding.groupPhotoImageView.visibility = View.VISIBLE
                binding.groupPhotoPlaceholder.visibility = View.GONE
            } else {
                Toast.makeText(this, "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveGroupPhoto(groupId: String): String {
        val uri = selectedGroupPhotoUri ?: return ""
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                val fileName = "group_photo_${groupId}.jpg"
                val file = File(filesDir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                fileName
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    data class MemberItem(val userId: String, val userName: String)

    private class GroupMembersAdapter(
        var members: List<MemberItem>,
        private val selectedMembers: MutableSet<String>,
        private val onMemberClick: (String) -> Unit
    ) : RecyclerView.Adapter<GroupMembersAdapter.MemberViewHolder>() {

        class MemberViewHolder(val binding: ItemGroupMemberBinding) 
            : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
            val binding = ItemGroupMemberBinding.inflate(
                android.view.LayoutInflater.from(parent.context),
                parent,
                false
            )
            return MemberViewHolder(binding)
        }

        override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
            val member = members[position]
            holder.binding.memberName.text = member.userName
            holder.binding.checkbox.isChecked = selectedMembers.contains(member.userId)
            
            holder.itemView.setOnClickListener {
                onMemberClick(member.userId)
            }
        }

        override fun getItemCount() = members.size

        fun updateMembers(newMembers: List<MemberItem>) {
            members = newMembers
            notifyDataSetChanged()
        }
    }
}

