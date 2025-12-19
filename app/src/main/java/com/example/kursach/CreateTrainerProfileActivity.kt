package com.example.kursach

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase
import com.example.kursach.databinding.ActivityCreateTrainerProfileBinding
import com.example.kursach.databinding.ItemPhotoBinding
import com.example.kursach.model.Gender
import com.example.kursach.model.Trainer
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

class CreateTrainerProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateTrainerProfileBinding
    private var editingTrainer: Trainer? = null
    private val selectedPhotos = mutableListOf<String>() // List of photo file paths
    private var selectedAvatar: String? = null // Avatar photo file path
    private lateinit var photosAdapter: PhotosListAdapter
    
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            addPhotoFromUri(it)
        }
    }
    
    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            addAvatarFromUri(it)
        }
    }
    
    private val categories = arrayOf("Спорт", "Музыка", "Искусство", "Танцы", "Кулинария", "Языки")
    private val dayNamesShort = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val dayNamesFull = arrayOf(
        "Понедельник",
        "Вторник",
        "Среда",
        "Четверг",
        "Пятница",
        "Суббота",
        "Воскресенье"
    )
    private val selectedDays = mutableSetOf<Int>()
    private val timeSlots = (0..24).map { String.format("%02d:00", it) }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateTrainerProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Создать анкету"

        val currentUser = UserManager.getCurrentUser()
        if (currentUser == null || currentUser.userType != com.example.kursach.model.UserType.TRAINER) {
            Toast.makeText(this, "Только тренеры могут создавать анкеты", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Проверяем, редактируем ли существующую анкету или создаем новую
        val trainerId = intent.getStringExtra("trainerId")
        if (trainerId != null) {
            // Редактирование существующей анкеты
            editingTrainer = JsonDatabase.getTrainerById(trainerId)
            if (editingTrainer != null) {
                supportActionBar?.title = "Редактировать анкету"
                resetAllFields()
                binding.editName.setText(currentUser.name)
                loadTrainerData(editingTrainer!!)
            } else {
                // Анкета не найдена, создаем новую
                supportActionBar?.title = "Создать анкету"
                resetAllFields()
                binding.editName.setText(currentUser.name)
            }
        } else {
            // Создание новой анкеты - всегда пустые поля
            supportActionBar?.title = "Создать анкету"
            resetAllFields()
            binding.editName.setText(currentUser.name)
        }
        
        // Имя всегда берется из аккаунта и недоступно для редактирования
        binding.editName.isEnabled = false

        setupDropdowns()
        setupPhotosRecyclerView()
        setupAvatarButton()
        binding.btnSave.setOnClickListener {
            saveTrainerProfile()
        }
    }
    
    private fun setupPhotosRecyclerView() {
        photosAdapter = PhotosListAdapter(selectedPhotos) { position ->
            // Remove photo
            selectedPhotos.removeAt(position)
            photosAdapter.updatePhotos(selectedPhotos)
        }
        binding.photosRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.photosRecyclerView.adapter = photosAdapter
        
        binding.btnAddPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }
    
    private class PhotosListAdapter(
        private var photos: MutableList<String>,
        private val onRemoveClick: (Int) -> Unit
    ) : RecyclerView.Adapter<PhotosListAdapter.PhotoViewHolder>() {
        
        fun updatePhotos(newPhotos: MutableList<String>) {
            photos = newPhotos
            notifyDataSetChanged()
        }
        
        class PhotoViewHolder(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val binding = ItemPhotoBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return PhotoViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            val photoPath = photos[position]
            val photoFile = File(holder.itemView.context.filesDir, photoPath)
            
            if (photoFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                holder.binding.photoImageView.setImageBitmap(bitmap)
            }
            
            holder.binding.btnRemovePhoto.setOnClickListener {
                onRemoveClick(position)
            }
        }
        
        override fun getItemCount() = photos.size
    }
    
    private fun addPhotoFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val currentUser = UserManager.getCurrentUser() ?: return
            val photoId = UUID.randomUUID().toString()
            val photoFileName = "trainer_photo_${currentUser.id}_$photoId.jpg"
            val photoFile = File(filesDir, photoFileName)
            
            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            selectedPhotos.add(photoFileName)
            if (::photosAdapter.isInitialized) {
                photosAdapter.updatePhotos(selectedPhotos)
            }
            
            Toast.makeText(this, "Фото добавлено", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupAvatarButton() {
        binding.btnChangeAvatar.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }
    }
    
    private fun addAvatarFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val currentUser = UserManager.getCurrentUser() ?: return
            val avatarId = UUID.randomUUID().toString()
            val avatarFileName = "trainer_avatar_${currentUser.id}_$avatarId.jpg"
            val avatarFile = File(filesDir, avatarFileName)
            
            FileOutputStream(avatarFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            // Удаляем старое фото аватарки, если есть
            selectedAvatar?.let { oldAvatar ->
                val oldFile = File(filesDir, oldAvatar)
                if (oldFile.exists()) {
                    oldFile.delete()
                }
            }

            selectedAvatar = avatarFileName

            // Отображаем аватарку
            binding.trainerAvatarImageView.setImageBitmap(bitmap)
            binding.trainerAvatarImageView.visibility = android.view.View.VISIBLE
            binding.trainerAvatarPlaceholder.visibility = android.view.View.GONE
            
            Toast.makeText(this, "Аватарка выбрана", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки аватарки", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupDropdowns() {
        // Category dropdown
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, categories)
        binding.editCategory.setAdapter(categoryAdapter)
        binding.editCategory.setOnItemClickListener { _, _, position, _ ->
            binding.editCategory.setText(categories[position], false)
        }
        binding.editCategory.setOnClickListener {
            binding.editCategory.showDropDown()
        }
        
        // Days selector
        binding.editSelectedDays.setOnClickListener {
            openDaysSelectionDialog()
        }
        binding.editSelectedDays.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                openDaysSelectionDialog()
                binding.editSelectedDays.clearFocus()
            }
        }
        binding.btnSelectDays.setOnClickListener {
            openDaysSelectionDialog()
        }

        // Time slots - start time
        val timeAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, timeSlots)
        binding.editStartTime.setAdapter(timeAdapter)
        binding.editStartTime.setOnItemClickListener { _, _, position, _ ->
            binding.editStartTime.setText(timeSlots[position], false)
        }
        binding.editStartTime.setOnClickListener {
            binding.editStartTime.showDropDown()
        }
        
        // Time slots - end time
        binding.editEndTime.setAdapter(timeAdapter)
        binding.editEndTime.setOnItemClickListener { _, _, position, _ ->
            binding.editEndTime.setText(timeSlots[position], false)
        }
        binding.editEndTime.setOnClickListener {
            binding.editEndTime.showDropDown()
        }
    }

    private fun openDaysSelectionDialog() {
        val tempSelection = selectedDays.toMutableSet()
        val checkedItems = BooleanArray(dayNamesFull.size) { index -> tempSelection.contains(index) }

        AlertDialog.Builder(this)
            .setTitle("Выберите рабочие дни")
            .setMultiChoiceItems(dayNamesFull, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    tempSelection.add(which)
                } else {
                    tempSelection.remove(which)
                }
            }
            .setPositiveButton("Готово") { dialog, _ ->
                selectedDays.clear()
                selectedDays.addAll(tempSelection)
                updateSelectedDaysText()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateSelectedDaysText() {
        if (!::binding.isInitialized) return
        if (selectedDays.isEmpty()) {
            binding.editSelectedDays.setText("")
            binding.selectedDaysInputLayout.helperText = "Выберите рабочие дни"
        } else {
            val displayText = selectedDays.sorted().map { dayNamesShort[it] }.joinToString(", ")
            binding.editSelectedDays.setText(displayText)
            binding.selectedDaysInputLayout.helperText = null
        }
    }

    private fun extractDaysFromTrainer(trainer: Trainer): List<Int> {
        if (trainer.availableDays.isNotEmpty()) {
            return trainer.availableDays.distinct().sorted()
        }
        val dayPart = trainer.availableTime.split(": ").firstOrNull()?.trim().orEmpty()
        return parseDaysFromString(dayPart)
    }

    private fun extractTimeRange(availableTime: String): Pair<String, String>? {
        val parts = availableTime.split(": ")
        if (parts.size < 2) return null
        val timeRange = parts[1].split("-")
        return if (timeRange.size == 2) {
            timeRange[0].trim() to timeRange[1].trim()
        } else {
            null
        }
    }

    private fun parseDaysFromString(dayPart: String): List<Int> {
        if (dayPart.isBlank()) return emptyList()
        val normalized = dayPart.replace(" ", "")
        return when {
            normalized.contains(",") -> normalized.split(",")
                .mapNotNull { dayLabelToIndex(it) }
                .distinct()
                .sorted()
            normalized.contains("-") -> {
                val parts = normalized.split("-")
                val start = parts.getOrNull(0)?.let { dayLabelToIndex(it) }
                val end = parts.getOrNull(1)?.let { dayLabelToIndex(it) }
                if (start != null && end != null) {
                    if (start <= end) {
                        (start..end).toList()
                    } else {
                        ((start until dayNamesShort.size) + (0..end)).toList()
                    }
                } else {
                    emptyList()
                }
            }
            else -> dayLabelToIndex(normalized)?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun dayLabelToIndex(label: String): Int? {
        val trimmed = label.trim().lowercase()
        return when {
            trimmed.startsWith("пн") -> 0
            trimmed.startsWith("вт") -> 1
            trimmed.startsWith("ср") -> 2
            trimmed.startsWith("чт") -> 3
            trimmed.startsWith("пт") -> 4
            trimmed.startsWith("сб") -> 5
            trimmed.startsWith("вс") -> 6
            else -> null
        }
    }
 
    private fun resetAllFields() {
        binding.editHobbyName.setText("")
        binding.editCategory.setText("", false)
        binding.editDescription.setText("")
        binding.editPrice.setText("")
        binding.editStartTime.setText("", false)
        binding.editEndTime.setText("", false)
        binding.editAddress.setText("")
        
        // Сбрасываем аватарку
        selectedAvatar = null
        binding.trainerAvatarImageView.visibility = android.view.View.GONE
        binding.trainerAvatarPlaceholder.visibility = android.view.View.VISIBLE
        
        // Пол берется из аккаунта автоматически, не показываем выбор
        val currentUser = UserManager.getCurrentUser()
        val genderText = when (currentUser?.gender) {
            Gender.MALE -> "Пол: Мужчина"
            Gender.FEMALE -> "Пол: Женщина"
            null -> null
        }
        if (genderText != null) {
            binding.trainerGenderText.text = genderText
            binding.trainerGenderText.visibility = android.view.View.VISIBLE
        } else {
            binding.trainerGenderText.visibility = android.view.View.GONE
        }
        
        selectedDays.clear()
        updateSelectedDaysText()
        selectedPhotos.clear()
        if (::photosAdapter.isInitialized) {
            photosAdapter.updatePhotos(selectedPhotos)
        }
    }
    
    private fun loadTrainerData(trainer: Trainer) {
        binding.editHobbyName.setText(trainer.hobbyName)
        // Имя всегда из аккаунта, не меняем его
        binding.editCategory.setText(trainer.category, false)
        binding.editDescription.setText(trainer.description)
        binding.editPrice.setText(trainer.price.toString())
        
        // Загружаем аватарку
        if (trainer.avatar.isNotEmpty()) {
            selectedAvatar = trainer.avatar
            val avatarFile = File(filesDir, trainer.avatar)
            if (avatarFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(avatarFile.absolutePath)
                binding.trainerAvatarImageView.setImageBitmap(bitmap)
                binding.trainerAvatarImageView.visibility = android.view.View.VISIBLE
                binding.trainerAvatarPlaceholder.visibility = android.view.View.GONE
            } else {
                binding.trainerAvatarImageView.visibility = android.view.View.GONE
                binding.trainerAvatarPlaceholder.visibility = android.view.View.VISIBLE
            }
        } else {
            selectedAvatar = null
            binding.trainerAvatarImageView.visibility = android.view.View.GONE
            binding.trainerAvatarPlaceholder.visibility = android.view.View.VISIBLE
        }
        
        // Пол берется из аккаунта автоматически, показываем только текст
        val currentUser = UserManager.getCurrentUser()
        val genderToShow = trainer.gender ?: currentUser?.gender
        val genderText = when (genderToShow) {
            Gender.MALE -> "Пол: Мужчина"
            Gender.FEMALE -> "Пол: Женщина"
            null -> null
        }
        if (genderText != null) {
            binding.trainerGenderText.text = genderText
            binding.trainerGenderText.visibility = android.view.View.VISIBLE
        } else {
            binding.trainerGenderText.visibility = android.view.View.GONE
        }
        
        selectedDays.clear()
        selectedDays.addAll(extractDaysFromTrainer(trainer))
        updateSelectedDaysText()

        val timeRange = extractTimeRange(trainer.availableTime)
        if (timeRange != null) {
            binding.editStartTime.setText(timeRange.first, false)
            binding.editEndTime.setText(timeRange.second, false)
        } else {
            binding.editStartTime.setText("", false)
            binding.editEndTime.setText("", false)
        }
        
        binding.editAddress.setText(trainer.address)
        
        // Загружаем фотографии
        selectedPhotos.clear()
        selectedPhotos.addAll(trainer.photos)
        if (::photosAdapter.isInitialized) {
            photosAdapter.updatePhotos(selectedPhotos)
        }
    }

    private fun saveTrainerProfile() {
        val currentUser = UserManager.getCurrentUser() ?: return
        
        val hobbyName = binding.editHobbyName.text.toString().trim()
        // Имя всегда берется из аккаунта, а не из поля
        val name = currentUser.name
        val category = binding.editCategory.text.toString().trim()
        val description = binding.editDescription.text.toString().trim()
        val priceText = binding.editPrice.text.toString().trim()
        val address = binding.editAddress.text.toString().trim()
        
        val startTime = binding.editStartTime.text.toString().trim()
        val endTime = binding.editEndTime.text.toString().trim()
        
        if (selectedDays.isEmpty()) {
            binding.selectedDaysInputLayout.error = "Выберите рабочие дни"
            Toast.makeText(this, "Выберите хотя бы один рабочий день", Toast.LENGTH_SHORT).show()
            return
        } else {
            binding.selectedDaysInputLayout.error = null
        }

        if (startTime.isEmpty() || endTime.isEmpty()) {
            Toast.makeText(this, "Заполните время работы", Toast.LENGTH_SHORT).show()
            return
        }

        val daysDisplay = selectedDays.sorted().map { dayNamesShort[it] }.joinToString(", ")
        val availableTime = "$daysDisplay: $startTime-$endTime"
        
        if (category.isEmpty() || description.isEmpty() || priceText.isEmpty()) {
            Toast.makeText(this, "Заполните все обязательные поля", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!categories.contains(category)) {
            Toast.makeText(this, "Выберите категорию из списка", Toast.LENGTH_SHORT).show()
            return
        }

        val price = try {
            priceText.toInt()
        } catch (e: Exception) {
            Toast.makeText(this, "Некорректная цена", Toast.LENGTH_SHORT).show()
            return
        }

        if (price <= 0) {
            Toast.makeText(this, "Цена должна быть больше 0", Toast.LENGTH_SHORT).show()
            return
        }

        // Пол всегда берется из аккаунта пользователя
        val gender = currentUser.gender

        val trainer = Trainer(
            id = editingTrainer?.id ?: UUID.randomUUID().toString(), // Генерируем новый ID для новой анкеты
            userId = editingTrainer?.userId?.takeIf { it.isNotEmpty() } ?: currentUser.id, // Сохраняем userId при редактировании или используем текущего пользователя
            name = name,
            category = category,
            hobbyName = hobbyName,
            description = description,
            price = price,
            rating = editingTrainer?.rating ?: 0f, // Сохраняем рейтинг при редактировании
            gender = gender,
            imageUrl = "",
            avatar = selectedAvatar ?: "", // Сохраняем аватарку
            availableTime = availableTime,
            address = address,
            latitude = null,
            longitude = null,
            availableDays = selectedDays.sorted(),
            photos = selectedPhotos.toList() // Сохраняем список фотографий
        )

        JsonDatabase.saveTrainer(trainer)
        Toast.makeText(this, "Анкета сохранена", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}



