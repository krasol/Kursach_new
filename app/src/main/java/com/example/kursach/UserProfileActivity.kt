package com.example.kursach

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kursach.adapter.UserGalleryAdapter
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase
import com.example.kursach.databinding.ActivityUserProfileBinding
import com.example.kursach.model.User
import com.example.kursach.ui.BottomNavigationHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList
import java.util.UUID

class UserProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserProfileBinding
    private var currentPhotoUri: Uri? = null
    private lateinit var galleryAdapter: UserGalleryAdapter
    private var displayedUser: User? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            loadImageFromUri(it)
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            currentPhotoUri?.let {
                loadImageFromUri(it)
            }
        }
    }

    private val pickGalleryPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            addGalleryPhoto(it)
        }
    }

    private var viewingUserId: String? = null
    private var isViewingOtherUser = false
    private var fromAdmin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.navigationIcon = null
        binding.toolbar.overflowIcon?.setTint(ContextCompat.getColor(this, android.R.color.white))

        // Check if viewing another user's profile
        viewingUserId = intent.getStringExtra("viewUserId")
        isViewingOtherUser = viewingUserId != null
        fromAdmin = intent.getBooleanExtra("fromAdmin", false)

        if (isViewingOtherUser) {
            supportActionBar?.title = "Профиль пользователя"
            // Hide edit buttons when viewing other user's profile
            binding.btnChangePhoto.visibility = View.GONE
            binding.btnEditProfile.visibility = View.GONE
            binding.btnLogout.visibility = View.GONE
            binding.btnBalance.visibility = View.GONE
            binding.btnAddGalleryPhoto.visibility = View.GONE
            if (fromAdmin) {
                binding.btnReport.visibility = View.GONE
                binding.btnMeetings.visibility = View.GONE
            } else {
                binding.btnReport.visibility = View.VISIBLE
            }
        } else {
            supportActionBar?.title = "Мой профиль"
            binding.btnBalance.visibility = View.VISIBLE
            binding.btnLogout.visibility = View.VISIBLE
            binding.btnAddGalleryPhoto.visibility = View.VISIBLE
            binding.btnReport.visibility = View.GONE
        }

        // Для тех админа скрываем bottom navigation
        if (fromAdmin) {
            binding.bottomNavigation.visibility = View.GONE
        } else {
            BottomNavigationHelper.setup(binding.bottomNavigation, this, R.id.navigation_profile)
        }

        setupGalleryRecycler()
        loadUserData()
        loadProfilePhoto()
        setupClickListeners()
    }

    private fun loadUserData() {
        val user = if (isViewingOtherUser && viewingUserId != null) {
            JsonDatabase.getUserById(viewingUserId!!)
        } else {
            UserManager.getCurrentUser()
        }
        
        if (user == null) {
            Toast.makeText(this, "Ошибка загрузки профиля", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        displayedUser = user

        // Разбиваем имя на имя и фамилию
        val nameParts = user.name.trim().split("\\s+".toRegex())
        val firstName = if (nameParts.isNotEmpty()) nameParts[0] else user.name
        val lastName = if (nameParts.size > 1) nameParts.subList(1, nameParts.size).joinToString(" ") else ""
        
        binding.userFirstNameTextView.text = firstName
        binding.userLastNameTextView.text = if (lastName.isNotEmpty()) lastName else "-"
        binding.userEmailTextView.text = user.email
        binding.userTypeTextView.text = if (user.userType == com.example.kursach.model.UserType.TRAINER) {
            "Тренер"
        } else {
            "Пользователь"
        }
        binding.userBalanceTextView.text = "${user.balance}p"
        binding.userBalanceTextView.visibility = if (isViewingOtherUser) View.GONE else View.VISIBLE
        
        // Отображаем пол
        val genderText = when (user.gender) {
            com.example.kursach.model.Gender.MALE -> "Мужской"
            com.example.kursach.model.Gender.FEMALE -> "Женский"
            null -> "Не указан"
        }
        binding.userGenderTextView.text = genderText
        
        try {
            val description = user.description ?: ""
            binding.userDescriptionTextView.text = if (description.isNotEmpty()) {
                description
            } else {
                "Нет описания"
            }
        } catch (e: Exception) {
            binding.userDescriptionTextView.text = "Нет описания"
        }

        updateGallerySection(user)
    }

    private fun loadProfilePhoto() {
        val user = displayedUser ?: return
        
        // Сначала проверяем аватарку из User, если нет - старый способ
        val avatarPath = if (user.avatar.isNotEmpty()) {
            user.avatar
        } else {
            "profile_photo_${user.id}.jpg" // Старый формат для обратной совместимости
        }
        
        val photoFile = File(filesDir, avatarPath)
        
        if (photoFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            binding.profilePhotoImageView.setImageBitmap(bitmap)
            binding.profilePhotoPlaceholder.visibility = android.view.View.GONE
        } else {
            binding.profilePhotoPlaceholder.visibility = android.view.View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        if (!isViewingOtherUser) {
            binding.btnChangePhoto.setOnClickListener {
                showPhotoSourceDialog()
            }
            
            binding.btnEditProfile.setOnClickListener {
                showEditProfileDialog()
            }
            
            binding.btnLogout.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Выход из аккаунта")
                    .setMessage("Вы уверены, что хотите выйти?")
                    .setPositiveButton("Выйти") { _, _ ->
                        UserManager.logout()
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }

            binding.btnBalance.setOnClickListener {
                showBalanceDialog()
            }

            binding.btnAddGalleryPhoto.setOnClickListener {
                pickGalleryPhotoLauncher.launch("image/*")
            }
        } else {
            binding.btnReport.setOnClickListener {
                showReportDialog()
            }
        }
        
        binding.btnMeetings.setOnClickListener {
            val intent = Intent(this, MeetingsActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun showReportDialog() {
        val currentUser = UserManager.getCurrentUser()
        val targetUser = displayedUser
        if (currentUser == null || targetUser == null) {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Нельзя пожаловаться на себя
        if (targetUser.id == currentUser.id) {
            Toast.makeText(this, "Вы не можете пожаловаться на себя", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_report, null)
        val reasonInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.reasonInput)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnSubmit = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSubmit)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Пожаловаться на пользователя")
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
                targetId = targetUser.id,
                targetType = com.example.kursach.model.ReportTargetType.USER_PROFILE,
                targetName = targetUser.name,
                reason = reason,
                status = com.example.kursach.model.ReportStatus.PENDING,
                createdAt = System.currentTimeMillis()
            )
            
            JsonDatabase.createReport(report)
            Toast.makeText(this, "Жалоба отправлена администратору", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun showBalanceDialog() {
        val user = UserManager.getCurrentUser() ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_balance, null)
        val amountInputLayout = dialogView.findViewById<TextInputLayout>(R.id.amountInputLayout)
        val amountEditText = dialogView.findViewById<TextInputEditText>(R.id.amountEditText)
        val currentBalanceText = dialogView.findViewById<TextView>(R.id.currentBalanceText)
        var currentUser = user
        currentBalanceText.text = "Баланс: ${currentUser.balance} ₽"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Управление балансом")
            .setNegativeButton("Закрыть", null)
            .create()

        dialog.setOnShowListener {
            val btnDeposit = dialogView.findViewById<MaterialButton>(R.id.btnDeposit)
            val btnWithdraw = dialogView.findViewById<MaterialButton>(R.id.btnWithdraw)

            fun parseAmount(): Int? {
                val raw = amountEditText.text?.toString()?.trim()
                val value = raw?.toIntOrNull()
                return if (value == null || value <= 0) {
                    amountInputLayout.error = "Введите положительное число"
                    null
                } else {
                    amountInputLayout.error = null
                    value
                }
            }

            fun applyUserUpdate(updatedUser: com.example.kursach.model.User) {
                currentUser = updatedUser
                UserManager.setCurrentUser(updatedUser)
                loadUserData()
                currentBalanceText.text = "Баланс: ${updatedUser.balance} ₽"
                amountEditText.setText("")
                amountInputLayout.error = null
            }

            btnDeposit.setOnClickListener {
                val amount = parseAmount() ?: return@setOnClickListener
                val updatedUser = JsonDatabase.updateUserBalance(currentUser.id, currentUser.balance + amount)
                if (updatedUser != null) {
                    applyUserUpdate(updatedUser)
                    Toast.makeText(this, "Баланс пополнен на $amount ₽", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Не удалось обновить баланс", Toast.LENGTH_SHORT).show()
                }
            }

            btnWithdraw.setOnClickListener {
                val amount = parseAmount() ?: return@setOnClickListener
                if (amount > currentUser.balance) {
                    amountInputLayout.error = "Недостаточно средств"
                    return@setOnClickListener
                }
                val updatedUser = JsonDatabase.updateUserBalance(currentUser.id, currentUser.balance - amount)
                if (updatedUser != null) {
                    applyUserUpdate(updatedUser)
                    Toast.makeText(this, "Выведено $amount ₽", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Не удалось обновить баланс", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showEditProfileDialog() {
        val user = UserManager.getCurrentUser() ?: return
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val nameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.nameInput)
        val descriptionInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.descriptionInput)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)
        
        nameInput.setText(user.name)
        descriptionInput.setText(user.description ?: "")
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnSave.setOnClickListener {
            val newName = nameInput.text.toString().trim()
            val newDescription = descriptionInput.text.toString().trim()
            
            if (newName.isEmpty()) {
                Toast.makeText(this, "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val success = JsonDatabase.updateUser(user.id, newName, newDescription)
            if (success) {
                loadUserData()
                Toast.makeText(this, "Профиль успешно обновлен", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Ошибка обновления профиля", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.show()
    }

    private fun showPhotoSourceDialog() {
        val options = arrayOf("Камера", "Галерея")
        AlertDialog.Builder(this)
            .setTitle("Выберите источник")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> pickImageFromGallery()
                }
            }
            .show()
    }

    private fun takePhoto() {
        val user = UserManager.getCurrentUser() ?: return
        val photoFile = File(filesDir, "temp_photo_${user.id}.jpg")
        currentPhotoUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        takePhotoLauncher.launch(currentPhotoUri)
    }

    private fun pickImageFromGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun loadImageFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Сохраняем фото
            val user = UserManager.getCurrentUser() ?: return
            val photoFileName = "avatar_${user.id}_${UUID.randomUUID()}.jpg"
            val photoFile = File(filesDir, photoFileName)
            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            // Удаляем старое фото, если есть
            if (user.avatar.isNotEmpty()) {
                val oldPhotoFile = File(filesDir, user.avatar)
                if (oldPhotoFile.exists()) {
                    oldPhotoFile.delete()
                }
            }

            // Обновляем аватарку в User
            val updatedUser = JsonDatabase.updateUserAvatar(user.id, photoFileName)
            if (updatedUser != null) {
                UserManager.setCurrentUser(updatedUser)
                displayedUser = updatedUser
                loadUserData()
            }

            // Отображаем фото
            binding.profilePhotoImageView.setImageBitmap(bitmap)
            binding.profilePhotoPlaceholder.visibility = android.view.View.GONE
            
            Toast.makeText(this, "Фото успешно загружено", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addGalleryPhoto(uri: Uri) {
        val user = displayedUser ?: return
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val photoFileName = "user_gallery_${user.id}_${UUID.randomUUID()}.jpg"
            val photoFile = File(filesDir, photoFileName)
            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val updatedUser = JsonDatabase.addUserGalleryPhoto(user.id, photoFileName)
            if (updatedUser != null) {
                displayedUser = updatedUser
                if (!isViewingOtherUser && UserManager.getCurrentUser()?.id == updatedUser.id) {
                    UserManager.setCurrentUser(updatedUser)
                }
                updateGallerySection(updatedUser)
                Toast.makeText(this, "Фото добавлено", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Не удалось сохранить фото", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeGalleryPhoto(photoPath: String) {
        val user = displayedUser ?: return
        val updatedUser = JsonDatabase.removeUserGalleryPhoto(user.id, photoPath)
        if (updatedUser != null) {
            val photoFile = File(filesDir, photoPath)
            if (photoFile.exists()) {
                photoFile.delete()
            }
            displayedUser = updatedUser
            if (UserManager.getCurrentUser()?.id == updatedUser.id) {
                UserManager.setCurrentUser(updatedUser)
            }
            updateGallerySection(updatedUser)
            Toast.makeText(this, "Фото удалено", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Не удалось удалить фото", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupGalleryRecycler() {
        galleryAdapter = UserGalleryAdapter(
            photos = emptyList(),
            isOwner = !isViewingOtherUser,
            onPhotoClick = { position, _ ->
                openGalleryViewer(position)
            },
            onRemovePhoto = if (!isViewingOtherUser) { photoPath ->
                showRemovePhotoConfirmation(photoPath)
            } else {
                null
            }
        )

        binding.galleryRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.galleryRecyclerView.adapter = galleryAdapter
    }

    private fun showRemovePhotoConfirmation(photoPath: String) {
        AlertDialog.Builder(this)
            .setTitle("Удалить фото")
            .setMessage("Вы уверены, что хотите удалить это фото?")
            .setPositiveButton("Удалить") { _, _ ->
                removeGalleryPhoto(photoPath)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openGalleryViewer(position: Int) {
        val user = displayedUser ?: return
        if (user.galleryPhotos.isEmpty()) return
        val intent = Intent(this, PhotoViewerActivity::class.java)
        intent.putStringArrayListExtra("photos", ArrayList(user.galleryPhotos))
        intent.putExtra("position", position)
        startActivity(intent)
    }

    private fun updateGallerySection(user: User) {
        val photos = user.galleryPhotos
        galleryAdapter.updatePhotos(photos)
        val hasPhotos = photos.isNotEmpty()
        binding.galleryEmptyText.visibility = if (!hasPhotos && !isViewingOtherUser) View.VISIBLE else View.GONE
        binding.galleryRecyclerView.visibility = if (hasPhotos) View.VISIBLE else View.GONE
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

