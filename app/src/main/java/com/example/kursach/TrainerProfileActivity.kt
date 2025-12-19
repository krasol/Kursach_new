package com.example.kursach

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase
import com.example.kursach.databinding.ActivityTrainerProfileBinding
import com.example.kursach.databinding.ItemReviewBinding
import com.example.kursach.model.Review
import com.example.kursach.model.Trainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class TrainerProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrainerProfileBinding
    private lateinit var trainer: Trainer
    private lateinit var reviewsAdapter: ReviewsAdapter
    private var reviewSortType: ReviewSortType = ReviewSortType.NEWEST
    private var fromAdmin: Boolean = false
    
    enum class ReviewSortType {
        NEWEST,      // Сначала новые
        OLDEST,      // Сначала старые
        BEST,        // Сначала лучшие
        WORST        // Сначала худшие
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainerProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        trainer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("trainer", Trainer::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Trainer>("trainer")
        } ?: run {
            finish()
            return
        }

        fromAdmin = intent.getBooleanExtra("fromAdmin", false)

        // Обновляем данные анкеты, если есть более свежая версия в базе
        JsonDatabase.getTrainerById(trainer.id)?.let { latest ->
            trainer = latest
        }

        setSupportActionBar(binding.toolbar)
        if (fromAdmin) {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            supportActionBar?.setDisplayShowHomeEnabled(false)
            binding.toolbar.navigationIcon = null
        } else {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
        }
        supportActionBar?.title = trainer.name

        setupUI()
    }
    

    private fun setupUI() {
        // Показываем название хобби, если оно есть (сначала, большим и цветным)
        if (trainer.hobbyName.isNotEmpty()) {
            binding.trainerProfileHobbyName.text = trainer.hobbyName
            binding.trainerProfileHobbyName.visibility = android.view.View.VISIBLE
        } else {
            binding.trainerProfileHobbyName.visibility = android.view.View.GONE
        }
        
        // Имя тренера показываем ниже серым цветом
        val ownerId = trainer.userId.ifEmpty { trainer.id }
        val owner = JsonDatabase.getUserById(ownerId)
        val displayName = if (owner?.isBanned == true) {
            "${trainer.name} (забанен)"
        } else {
            trainer.name
        }
        binding.trainerProfileName.text = displayName
        
        // Делаем имя кликабельным для перехода на профиль тренера (пользователя)
        // Для тех админа отключаем эту возможность
        if (!fromAdmin) {
            binding.trainerProfileName.setOnClickListener { view ->
                // Останавливаем распространение события
                view.isEnabled = false

                val user = JsonDatabase.getUserById(ownerId)
                if (user != null) {
                    // Открываем профиль пользователя, а не анкету тренера
                    val intent = android.content.Intent(this, UserProfileActivity::class.java)
                    intent.putExtra("viewUserId", user.id)
                    startActivity(intent)
                } else {
                    android.widget.Toast.makeText(this, "Профиль пользователя не найден", android.widget.Toast.LENGTH_SHORT).show()
                }

                view.postDelayed({ view.isEnabled = true }, 300)
            }
        } else {
            binding.trainerProfileName.isClickable = false
            binding.trainerProfileName.isFocusable = false
        }
        
        binding.trainerProfileCategory.text = trainer.category
        binding.trainerProfileDescription.text = trainer.description
        binding.trainerProfilePrice.text = "${trainer.price} ₽/час"
        binding.trainerProfileRating.text = "⭐ ${trainer.rating}"
        
        if (trainer.availableTime.isNotEmpty()) {
            binding.trainerProfileAvailableTime.text = "🕐 ${trainer.availableTime}"
            binding.trainerProfileAvailableTime.visibility = android.view.View.VISIBLE
        } else {
            binding.trainerProfileAvailableTime.visibility = android.view.View.GONE
        }
        
        if (trainer.address.isNotEmpty()) {
            binding.trainerProfileAddress.text = "📍 ${trainer.address}"
            binding.trainerProfileAddress.visibility = android.view.View.VISIBLE
        } else {
            binding.trainerProfileAddress.visibility = android.view.View.GONE
        }
        
        // Отображаем пол
        val genderText = when (trainer.gender) {
            com.example.kursach.model.Gender.MALE -> "👤 Мужчина"
            com.example.kursach.model.Gender.FEMALE -> "👤 Женщина"
            null -> null
        }
        if (genderText != null) {
            binding.trainerProfileGender.text = genderText
            binding.trainerProfileGender.visibility = android.view.View.VISIBLE
        } else {
            binding.trainerProfileGender.visibility = android.view.View.GONE
        }
        
        // Проверяем, является ли текущий пользователь владельцем анкеты
        val currentUser = UserManager.getCurrentUser()
        val isOwner = currentUser != null && (currentUser.id == ownerId || currentUser.id == trainer.id)
        
        // Для тех админа скрываем кнопки действий
        if (fromAdmin) {
            binding.btnMessage.visibility = android.view.View.GONE
            binding.btnSchedule.visibility = android.view.View.GONE
            binding.btnReport.visibility = android.view.View.GONE
            binding.btnAddReview.visibility = android.view.View.GONE
            binding.btnEdit.visibility = android.view.View.GONE
        } else {
            // Показываем кнопку редактирования только владельцу
            if (isOwner) {
                binding.btnEdit.visibility = android.view.View.VISIBLE
                binding.btnEdit.setOnClickListener {
                    val intent = android.content.Intent(this, CreateTrainerProfileActivity::class.java)
                    intent.putExtra("trainerId", trainer.id)
                    startActivity(intent)
                }
                // Скрываем кнопки для владельца
                binding.btnMessage.visibility = android.view.View.GONE
                binding.btnSchedule.visibility = android.view.View.GONE
                binding.btnReport.visibility = android.view.View.GONE
            } else {
                binding.btnEdit.visibility = android.view.View.GONE
                binding.btnMessage.setOnClickListener {
                    val intent = android.content.Intent(this, MessengerActivity::class.java)
                    intent.putExtra("trainer", trainer)
                    startActivity(intent)
                }
                
                binding.btnSchedule.setOnClickListener {
                    val intent = android.content.Intent(this, ScheduleActivity::class.java)
                    intent.putExtra("trainer", trainer)
                    startActivity(intent)
                }
                
                binding.btnReport.setOnClickListener {
                    showReportDialog()
                }
            }
        }
        
        setupPhotos()
        setupReviews()
    }
    
    private fun showReportDialog() {
        val currentUser = UserManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Нельзя пожаловаться на свою анкету
        if (trainer.userId == currentUser.id || trainer.id == currentUser.id) {
            Toast.makeText(this, "Вы не можете пожаловаться на свою анкету", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_report, null)
        val reasonInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.reasonInput)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnSubmit = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSubmit)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Пожаловаться на анкету")
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
                targetId = trainer.id,
                targetType = com.example.kursach.model.ReportTargetType.TRAINER_PROFILE,
                targetName = trainer.name,
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
    
    private var autoScrollHandler: android.os.Handler? = null
    private var autoScrollRunnable: Runnable? = null
    private var isScrollingForward = true
    
    private fun setupPhotos() {
        val photos = if (trainer.photos.isNotEmpty()) {
            trainer.photos
        } else {
            // If no photos, show placeholder
            listOf("")
        }
        
        val validPhotos = photos.filter { it.isNotEmpty() }
        if (validPhotos.isEmpty()) {
            binding.photosTabLayout.visibility = View.GONE
            return
        }
        
        val adapter = PhotosAdapter(photos)
        binding.photosViewPager.adapter = adapter
        
        // Настраиваем TabLayout индикаторы - показываем только если больше 1 фотографии
        if (validPhotos.size > 1) {
            binding.photosTabLayout.visibility = View.VISIBLE
        } else {
            binding.photosTabLayout.visibility = View.GONE
            return
        }
        binding.photosTabLayout.removeAllTabs()
        for (i in validPhotos.indices) {
            val tab = binding.photosTabLayout.newTab()
            binding.photosTabLayout.addTab(tab)
            // Устанавливаем фиксированные размеры для каждого таба, чтобы они были круглыми
            val tabView = tab.view
            tabView.setPadding(0, 0, 0, 0)
            val layoutParams = tabView.layoutParams
            // Увеличиваем размер в 2 раза (было 12dp, стало 24dp)
            layoutParams.width = (24 * resources.displayMetrics.density).toInt()
            layoutParams.height = (24 * resources.displayMetrics.density).toInt()
            // Добавляем отступы между индикаторами
            if (i > 0) {
                (tabView.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.leftMargin = 
                    (8 * resources.displayMetrics.density).toInt()
            }
            tabView.layoutParams = layoutParams
        }
        
        // Синхронизируем ViewPager и TabLayout
        binding.photosViewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val validPosition = validPhotos.indices.firstOrNull { 
                    photos.indexOf(validPhotos[it]) == position 
                } ?: 0
                binding.photosTabLayout.getTabAt(validPosition)?.select()
            }
        })
        
        binding.photosTabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.position?.let { 
                    val photoIndex = photos.indexOf(validPhotos[it])
                    if (photoIndex >= 0) {
                        binding.photosViewPager.setCurrentItem(photoIndex, true)
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
        
        // Делаем ViewPager кликабельным для просмотра фотографий
        // Используем GestureDetector для различения кликов и свайпов
        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                if (validPhotos.isNotEmpty()) {
                    val intent = android.content.Intent(this@TrainerProfileActivity, PhotoViewerActivity::class.java)
                    intent.putStringArrayListExtra("photos", ArrayList(validPhotos))
                    val currentPosition = binding.photosViewPager.currentItem
                    val validPosition = validPhotos.indices.firstOrNull { 
                        photos.indexOf(validPhotos[it]) == currentPosition 
                    } ?: 0
                    intent.putExtra("position", validPosition)
                    startActivity(intent)
                    return true
                }
                return false
            }
        })
        
        binding.photosViewPager.setOnTouchListener { _, event ->
            // Останавливаем автопролистывание при взаимодействии
            stopAutoScroll()
            // Возобновляем автопролистывание через 3 секунды после последнего взаимодействия
            autoScrollHandler?.postDelayed({
                if (validPhotos.size > 1) {
                    startAutoScroll(validPhotos.size)
                }
            }, 3000)
            gestureDetector.onTouchEvent(event)
            false // Позволяем ViewPager обрабатывать свайпы
        }
        
        // Запускаем автопролистывание
        startAutoScroll(validPhotos.size)
    }
    
    private fun startAutoScroll(validPhotosCount: Int) {
        if (validPhotosCount <= 1) {
            stopAutoScroll()
            return
        }
        
        stopAutoScroll()
        
        // Получаем список валидных фотографий для определения позиций
        val photos = if (trainer.photos.isNotEmpty()) {
            trainer.photos
        } else {
            listOf("")
        }
        val validPhotos = photos.filter { it.isNotEmpty() }
        
        if (validPhotos.size <= 1) {
            stopAutoScroll()
            return
        }
        
        autoScrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
        autoScrollRunnable = object : Runnable {
            override fun run() {
                val currentItem = binding.photosViewPager.currentItem
                
                if (validPhotos.isEmpty()) {
                    stopAutoScroll()
                    return
                }
                
                // Находим текущую позицию в списке валидных фотографий
                val currentValidIndex = validPhotos.indices.firstOrNull { 
                    photos.indexOf(validPhotos[it]) == currentItem 
                } ?: 0
                
                val nextValidIndex = if (isScrollingForward) {
                    if (currentValidIndex < validPhotos.size - 1) {
                        currentValidIndex + 1
                    } else {
                        // Достигли конца, идем обратно
                        isScrollingForward = false
                        currentValidIndex - 1
                    }
                } else {
                    if (currentValidIndex > 0) {
                        currentValidIndex - 1
                    } else {
                        // Достигли начала, идем вперед
                        isScrollingForward = true
                        currentValidIndex + 1
                    }
                }
                
                // Находим индекс в общем списке фотографий
                val nextItem = photos.indexOf(validPhotos[nextValidIndex])
                if (nextItem >= 0 && nextItem < photos.size) {
                    binding.photosViewPager.setCurrentItem(nextItem, true)
                }
                
                // Планируем следующее переключение через 5 секунд
                autoScrollHandler?.postDelayed(this, 5000)
            }
        }
        
        // Запускаем первое переключение через 5 секунд
        autoScrollHandler?.postDelayed(autoScrollRunnable!!, 5000)
    }
    
    private fun stopAutoScroll() {
        autoScrollRunnable?.let { autoScrollHandler?.removeCallbacks(it) }
        autoScrollRunnable = null
    }
    
    override fun onPause() {
        super.onPause()
        stopAutoScroll()
    }
    
    override fun onResume() {
        super.onResume()
        // Обновляем данные анкеты при возврате на экран (например, после редактирования)
        JsonDatabase.getTrainerById(trainer.id)?.let { latest ->
            trainer = latest
            setupUI()
        }
        
        // Запускаем автопролистывание фотографий
        val photos = if (trainer.photos.isNotEmpty()) {
            trainer.photos
        } else {
            listOf("")
        }
        val validPhotos = photos.filter { it.isNotEmpty() }
        if (validPhotos.size > 1) {
            startAutoScroll(validPhotos.size)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAutoScroll()
        autoScrollHandler = null
    }
    
    private fun setupReviews() {
        reviewsAdapter = ReviewsAdapter(emptyList())
        binding.reviewsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        binding.reviewsRecyclerView.adapter = reviewsAdapter
        
        loadReviews()
        
        binding.btnAddReview.setOnClickListener {
            showAddReviewDialog()
        }
        
        binding.btnSortReviews.setOnClickListener { view ->
            showSortReviewsMenu(view)
        }
    }
    
    private fun showSortReviewsMenu(view: View) {
        val popupMenu = android.widget.PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.review_sort_menu, popupMenu.menu)
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.sort_newest -> {
                    reviewSortType = ReviewSortType.NEWEST
                    loadReviews()
                    true
                }
                R.id.sort_oldest -> {
                    reviewSortType = ReviewSortType.OLDEST
                    loadReviews()
                    true
                }
                R.id.sort_best -> {
                    reviewSortType = ReviewSortType.BEST
                    loadReviews()
                    true
                }
                R.id.sort_worst -> {
                    reviewSortType = ReviewSortType.WORST
                    loadReviews()
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }
    
    private fun loadReviews() {
        var reviews = JsonDatabase.getReviewsForTrainer(trainer.id)
        
        // Применяем сортировку
        reviews = when (reviewSortType) {
            ReviewSortType.NEWEST -> reviews.sortedByDescending { it.createdAt }
            ReviewSortType.OLDEST -> reviews.sortedBy { it.createdAt }
            ReviewSortType.BEST -> reviews.sortedByDescending { it.rating }
            ReviewSortType.WORST -> reviews.sortedBy { it.rating }
        }
        
        reviewsAdapter.updateReviews(reviews)
        
        // Вычисляем средний рейтинг
        val averageRating = if (reviews.isNotEmpty()) {
            reviews.map { it.rating }.average().toFloat()
        } else {
            0f
        }
        
        // Обновляем рейтинг тренера (показываем 0 если нет отзывов)
        binding.trainerProfileRating.text = if (reviews.isEmpty()) {
            "⭐ 0.0"
        } else {
            "⭐ ${String.format("%.1f", averageRating)}"
        }
        
        if (reviews.isEmpty()) {
            binding.emptyReviewsText.visibility = View.VISIBLE
            binding.reviewsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyReviewsText.visibility = View.GONE
            binding.reviewsRecyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun showAddReviewDialog() {
        val currentUser = UserManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Проверяем, не является ли пользователь владельцем этой анкеты
        if (trainer.userId == currentUser.id) {
            Toast.makeText(this, "Вы не можете оставить отзыв на свою анкету", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Проверяем, не оставлял ли пользователь уже отзыв на эту анкету
        val existingReviews = JsonDatabase.getReviewsForTrainer(trainer.id)
        val userReview = existingReviews.find { it.userId == currentUser.id }

        val hasPaidMeeting = JsonDatabase.getMeetingsForUser(currentUser.id)
            .any { it.trainerId == trainer.id && it.isPaid && it.status != "rejected" }
        if (!hasPaidMeeting) {
            Toast.makeText(this, "Отзыв можно оставить только после оплаты занятия", Toast.LENGTH_SHORT).show()
            return
        }
 
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_review, null)
        val reviewTextInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.reviewTextInput)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnSubmit = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSubmit)
        val ratingText = dialogView.findViewById<android.widget.TextView>(R.id.ratingText)
        
        val stars = listOf(
            dialogView.findViewById<android.widget.ImageView>(R.id.star1),
            dialogView.findViewById<android.widget.ImageView>(R.id.star2),
            dialogView.findViewById<android.widget.ImageView>(R.id.star3),
            dialogView.findViewById<android.widget.ImageView>(R.id.star4),
            dialogView.findViewById<android.widget.ImageView>(R.id.star5)
        )
        
        var selectedRating = 0
        
        // Star click listeners
        stars.forEachIndexed { index, star ->
            star.setOnClickListener {
                selectedRating = index + 1
                updateStars(stars, selectedRating)
                updateRatingText(ratingText, selectedRating)
            }
        }
        
        // Если отзыв уже существует, загружаем его данные для редактирования
        if (userReview != null) {
            reviewTextInput.setText(userReview.text)
            selectedRating = userReview.rating.toInt()
            updateStars(stars, selectedRating)
            updateRatingText(ratingText, selectedRating)
            btnSubmit.text = "Сохранить изменения"
        } else {
            // Initialize with 5 stars selected
            selectedRating = 5
            updateStars(stars, selectedRating)
            updateRatingText(ratingText, selectedRating)
        }
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle(if (userReview != null) "Редактировать отзыв" else "Написать отзыв")
            .create()
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnSubmit.setOnClickListener {
            val text = reviewTextInput?.text?.toString()?.trim() ?: ""
            
            if (text.isEmpty()) {
                Toast.makeText(this, "Введите текст отзыва", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (selectedRating == 0) {
                Toast.makeText(this, "Выберите оценку", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (userReview != null) {
                // Обновляем существующий отзыв
                val updatedReview = userReview.copy(
                    rating = selectedRating.toFloat(),
                    text = text
                )
                val allReviews = JsonDatabase.getAllReviews().toMutableList()
                val index = allReviews.indexOfFirst { it.id == userReview.id }
                if (index != -1) {
                    allReviews[index] = updatedReview
                    JsonDatabase.saveReviews(allReviews)
                    loadReviews()
                    Toast.makeText(this, "Отзыв обновлен", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Создаем новый отзыв
                val review = Review(
                    id = UUID.randomUUID().toString(),
                    trainerId = trainer.id,
                    userId = currentUser.id,
                    userName = currentUser.name,
                    rating = selectedRating.toFloat(),
                    text = text
                )
                
                JsonDatabase.saveReview(review)
                loadReviews()
                Toast.makeText(this, "Отзыв добавлен", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun updateStars(stars: List<android.widget.ImageView>, rating: Int) {
        stars.forEachIndexed { index, star ->
            if (index < rating) {
                star.setImageResource(R.drawable.ic_star_filled)
            } else {
                star.setImageResource(R.drawable.ic_star_outline)
            }
        }
    }
    
    private fun updateRatingText(textView: android.widget.TextView, rating: Int) {
        val ratingTexts = arrayOf(
            "Выберите оценку",
            "Ужасно",
            "Плохо",
            "Нормально",
            "Хорошо",
            "Отлично"
        )
        textView.text = if (rating > 0) ratingTexts[rating] else ratingTexts[0]
        textView.setTextColor(
            when (rating) {
                1, 2 -> ContextCompat.getColor(this, android.R.color.holo_red_dark) // Красный цвет для "Ужасно" и "Плохо"
                3 -> ContextCompat.getColor(this, R.color.category_music)
                4, 5 -> ContextCompat.getColor(this, R.color.accent_color)
                else -> ContextCompat.getColor(this, R.color.text_secondary)
            }
        )
    }
    
    private class ReviewsAdapter(
        private var reviews: List<Review>
    ) : RecyclerView.Adapter<ReviewsAdapter.ReviewViewHolder>() {
        
        fun updateReviews(newReviews: List<Review>) {
            reviews = newReviews
            notifyDataSetChanged()
        }
        
        class ReviewViewHolder(val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
            val binding = ItemReviewBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ReviewViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
            val review = reviews[position]
            holder.binding.reviewerName.text = review.userName
            holder.binding.reviewRating.text = String.format(Locale.getDefault(), "%.1f", review.rating)
            holder.binding.reviewText.text = review.text
            
            // Set user initials
            val initials = if (review.userName.isNotEmpty()) {
                val parts = review.userName.trim().split(" ")
                if (parts.size >= 2) {
                    "${parts[0].firstOrNull()?.uppercaseChar() ?: ""}${parts[1].firstOrNull()?.uppercaseChar() ?: ""}"
                } else {
                    review.userName.take(2).uppercase()
                }
            } else {
                "??"
            }
            holder.binding.reviewerInitials.text = initials
            
            // Format date
            val now = System.currentTimeMillis()
            val reviewTime = review.createdAt
            val diff = now - reviewTime
            val days = diff / (1000 * 60 * 60 * 24)
            val hours = diff / (1000 * 60 * 60)
            val minutes = diff / (1000 * 60)
            
            val dateText = when {
                minutes < 60 -> "только что"
                hours < 24 -> "${hours.toInt()} ${getHoursText(hours.toInt())} назад"
                days < 7 -> "${days.toInt()} ${getDaysText(days.toInt())} назад"
                days < 30 -> {
                    val weeks = (days / 7).toInt()
                    "$weeks ${getWeeksText(weeks)} назад"
                }
                days < 365 -> {
                    val months = (days / 30).toInt()
                    "$months ${getMonthsText(months)} назад"
                }
                else -> {
                    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    dateFormat.format(Date(reviewTime))
                }
            }
            holder.binding.reviewDate.text = dateText
            
            // Set avatar background color based on name hash
            val colors = arrayOf(
                R.color.primary_color,
                R.color.category_music,
                R.color.category_art,
                R.color.category_dance,
                R.color.category_cooking,
                R.color.category_language
            )
            val colorIndex = review.userName.hashCode().absoluteValue % colors.size
            val avatarCard = holder.itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.avatarCardView)
            avatarCard?.setCardBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, colors[colorIndex])
            )
            
            // Make reviewer name clickable to view their profile
            holder.binding.reviewerName.setOnClickListener {
                val user = JsonDatabase.getUserById(review.userId)
                if (user != null) {
                    val intent = android.content.Intent(holder.itemView.context, UserProfileActivity::class.java)
                    intent.putExtra("viewUserId", user.id)
                    holder.itemView.context.startActivity(intent)
                } else {
                    android.widget.Toast.makeText(holder.itemView.context, "Пользователь не найден", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        private fun getHoursText(hours: Int): String {
            return when {
                hours % 10 == 1 && hours % 100 != 11 -> "час"
                hours % 10 in 2..4 && hours % 100 !in 12..14 -> "часа"
                else -> "часов"
            }
        }
        
        private fun getDaysText(days: Int): String {
            return when {
                days % 10 == 1 && days % 100 != 11 -> "день"
                days % 10 in 2..4 && days % 100 !in 12..14 -> "дня"
                else -> "дней"
            }
        }
        
        private fun getWeeksText(weeks: Int): String {
            return when {
                weeks % 10 == 1 && weeks % 100 != 11 -> "неделя"
                weeks % 10 in 2..4 && weeks % 100 !in 12..14 -> "недели"
                else -> "недель"
            }
        }
        
        private fun getMonthsText(months: Int): String {
            return when {
                months % 10 == 1 && months % 100 != 11 -> "месяц"
                months % 10 in 2..4 && months % 100 !in 12..14 -> "месяца"
                else -> "месяцев"
            }
        }
        
        private val Int.absoluteValue: Int
            get() = if (this < 0) -this else this
        
        override fun getItemCount() = reviews.size
    }

    override fun onSupportNavigateUp(): Boolean {
        if (fromAdmin) {
            // Возвращаемся в админ панель
            val intent = android.content.Intent(this, AdminActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
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
            val intent = android.content.Intent(this, AdminActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        } else {
            super.onBackPressed()
        }
    }
    
    private class PhotosAdapter(private val photos: List<String>) : RecyclerView.Adapter<PhotosAdapter.PhotoViewHolder>() {
        
        class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: android.widget.ImageView = itemView.findViewById(R.id.photoImageView)
            val placeholder: View = itemView.findViewById(R.id.photoPlaceholder)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trainer_photo, parent, false)
            return PhotoViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            val photoPath = photos[position]
            
            if (photoPath.isNotEmpty()) {
                val photoFile = java.io.File(holder.itemView.context.filesDir, photoPath)
                if (photoFile.exists()) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                    holder.imageView.setImageBitmap(bitmap)
                    holder.imageView.visibility = View.VISIBLE
                    holder.placeholder.visibility = View.GONE
                    
                    // Делаем фото кликабельным для полноэкранного просмотра
                    holder.imageView.setOnClickListener {
                        val validPhotos = photos.filter { it.isNotEmpty() }
                        if (validPhotos.isNotEmpty()) {
                            val intent = android.content.Intent(holder.itemView.context, PhotoViewerActivity::class.java)
                            intent.putStringArrayListExtra("photos", ArrayList(validPhotos))
                            // Находим позицию в списке валидных фотографий
                            val validPosition = validPhotos.indexOf(photoPath).coerceAtLeast(0)
                            intent.putExtra("position", validPosition)
                            holder.itemView.context.startActivity(intent)
                        }
                    }
                } else {
                    holder.imageView.visibility = View.GONE
                    holder.placeholder.visibility = View.VISIBLE
                }
            } else {
                holder.imageView.visibility = View.GONE
                holder.placeholder.visibility = View.VISIBLE
            }
        }
        
        override fun getItemCount() = photos.size
    }
}

