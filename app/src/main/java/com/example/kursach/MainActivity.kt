package com.example.kursach

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kursach.adapter.TrainerAdapter
import com.example.kursach.database.JsonDatabase
import com.example.kursach.databinding.ActivityMainBinding
import com.example.kursach.model.Gender
import com.example.kursach.model.Trainer
import com.example.kursach.ui.BottomNavigationHelper
import com.example.kursach.utils.DocumentUtils
import com.google.android.material.color.MaterialColors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: TrainerAdapter
    private var allTrainers: List<Trainer> = emptyList()
    private var filteredTrainers: List<Trainer> = emptyList()
    private var currentSortType: SortType = SortType.DEFAULT
    private var selectedCategory: String? = null
    private var selectedGender: Gender? = null
    private var notificationPopup: NotificationPopup? = null

    enum class SortType {
        DEFAULT,
        PRICE_ASC,
        PRICE_DESC,
        RATING_ASC,
        RATING_DESC
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Проверяем авторизацию
        if (!com.example.kursach.data.UserManager.isLoggedIn()) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        // Устанавливаем цвет троеточия сразу
        binding.toolbar.post {
            binding.toolbar.overflowIcon?.setTint(ContextCompat.getColor(this, android.R.color.white))
        }

        BottomNavigationHelper.setup(binding.bottomNavigation, this, R.id.navigation_home)

        setupNotificationsIcon()
        setupRecyclerView()
        setupSearch()
        setupSortButton()
        setupCategoryButton()
        loadTrainers()
    }
    
    override fun onResume() {
        super.onResume()
        // Обновляем список тренеров при возврате на экран
        loadTrainers()
        // Обновляем бейдж уведомлений при возвращении на экран
        updateNotificationsIcon()
    }
    
    private fun updateNotificationsIcon() {
        val currentUser = com.example.kursach.data.UserManager.getCurrentUser() ?: return
        val unreadCount = NotificationManager.getUnreadCount(currentUser.id)
        
        // Находим view уведомлений в toolbar
        val notificationView = binding.toolbar.findViewById<View>(R.id.badge_text)?.parent as? View
        if (notificationView != null) {
            val badgeText = notificationView.findViewById<android.widget.TextView>(R.id.badge_text)
            if (unreadCount > 0) {
                badgeText.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                badgeText.visibility = View.VISIBLE
            } else {
                badgeText.visibility = View.GONE
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = TrainerAdapter(emptyList()) { trainer ->
            openTrainerProfile(trainer)
        }
        binding.trainersRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.trainersRecyclerView.adapter = adapter
        val bottomPadding = resources.getDimensionPixelSize(R.dimen.bottom_nav_height) +
                resources.getDimensionPixelSize(R.dimen.content_bottom_padding)
        binding.trainersRecyclerView.setPadding(0, 0, 0, bottomPadding)
        binding.trainersRecyclerView.clipToPadding = false
    }

    private fun setupSearch() {
        // Находим TextView для hint
        val hintTextView = binding.root.findViewById<android.widget.TextView>(R.id.searchHintText)
        
        binding.searchEditText.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Скрываем/показываем hint в зависимости от наличия текста
                hintTextView?.visibility = if (s.isNullOrEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {
                performSearch()
            }
        })
        
        // Проверяем начальное состояние
        hintTextView?.visibility = if (binding.searchEditText.text.isNullOrEmpty()) View.VISIBLE else View.GONE
    }


    private fun setupSortButton() {
        binding.sortButton.setOnClickListener { view ->
            showSortMenu(view)
        }
    }
    
    private fun setupCategoryButton() {
        binding.categoryButton.setOnClickListener { view ->
            showCategoryMenu(view)
        }
    }
    
    private fun showSortMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.sort_menu, popupMenu.menu)
        
        // Включаем отображение иконок в PopupMenu
        try {
            val fieldMPopup = PopupMenu::class.java.getDeclaredField("mPopup")
            fieldMPopup.isAccessible = true
            val mPopup = fieldMPopup.get(popupMenu)
            mPopup?.javaClass?.getDeclaredMethod("setForceShowIcon", Boolean::class.java)?.invoke(mPopup, true)
            
            val iconColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
            val menu = popupMenu.menu
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                item?.icon?.setTint(iconColor)
            }
        } catch (e: Exception) {
            // Если не удалось включить иконки, продолжаем без них
        }
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.sort_default -> {
                    // Если уже выбрано "По умолчанию", сбрасываем все фильтры
                    if (currentSortType == SortType.DEFAULT) {
                        currentSortType = SortType.DEFAULT
                        selectedGender = null
                        selectedCategory = null
                        binding.searchEditText.text?.clear()
                    } else {
                        currentSortType = SortType.DEFAULT
                    }
                    applyFilters()
                    true
                }
                R.id.sort_price_asc -> {
                    currentSortType = SortType.PRICE_ASC
                    applyFilters()
                    true
                }
                R.id.sort_price_desc -> {
                    currentSortType = SortType.PRICE_DESC
                    applyFilters()
                    true
                }
                R.id.sort_rating_desc -> {
                    currentSortType = SortType.RATING_DESC
                    applyFilters()
                    true
                }
                R.id.sort_rating_asc -> {
                    currentSortType = SortType.RATING_ASC
                    applyFilters()
                    true
                }
                R.id.gender_male -> {
                    // Если уже выбран мужской пол, сбрасываем фильтр
                    if (selectedGender == Gender.MALE) {
                        selectedGender = null
                    } else {
                        selectedGender = Gender.MALE
                    }
                    applyFilters()
                    true
                }
                R.id.gender_female -> {
                    // Если уже выбран женский пол, сбрасываем фильтр
                    if (selectedGender == Gender.FEMALE) {
                        selectedGender = null
                    } else {
                        selectedGender = Gender.FEMALE
                    }
                    applyFilters()
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }
    
    private fun showCategoryMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.category_menu, popupMenu.menu)
        
        // Включаем отображение иконок в PopupMenu
        try {
            val fieldMPopup = PopupMenu::class.java.getDeclaredField("mPopup")
            fieldMPopup.isAccessible = true
            val mPopup = fieldMPopup.get(popupMenu)
            mPopup?.javaClass?.getDeclaredMethod("setForceShowIcon", Boolean::class.java)?.invoke(mPopup, true)
            
            val iconColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
            val menu = popupMenu.menu
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                item?.icon?.setTint(iconColor)
            }
        } catch (e: Exception) {
            // Если не удалось включить иконки, продолжаем без них
        }
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.category_all -> {
                    selectedCategory = null
                    applyFilters()
                    true
                }
                R.id.category_sport -> {
                    selectedCategory = "Спорт"
                    applyFilters()
                    true
                }
                R.id.category_music -> {
                    selectedCategory = "Музыка"
                    applyFilters()
                    true
                }
                R.id.category_art -> {
                    selectedCategory = "Искусство"
                    applyFilters()
                    true
                }
                R.id.category_dance -> {
                    selectedCategory = "Танцы"
                    applyFilters()
                    true
                }
                R.id.category_cooking -> {
                    selectedCategory = "Кулинария"
                    applyFilters()
                    true
                }
                R.id.category_language -> {
                    selectedCategory = "Языки"
                    applyFilters()
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }

    private fun loadTrainers() {
        try {
            // Загружаем тренеров из базы данных (объединяет DataSource и пользовательские)
            allTrainers = JsonDatabase.getTrainers()
            applyFilters()
        } catch (e: Exception) {
            e.printStackTrace()
            allTrainers = emptyList()
            filteredTrainers = emptyList()
            adapter.updateTrainers(emptyList())
            Toast.makeText(this, "Ошибка загрузки тренеров", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSearch() {
        applyFilters()
    }
    
    private fun applyFilters() {
        val query = binding.searchEditText.text.toString().lowercase().trim()
        
        // Фильтрация по поисковому запросу
        filteredTrainers = if (query.isEmpty()) {
            allTrainers
        } else {
            allTrainers.filter { trainer ->
                trainer.name.lowercase().contains(query) ||
                trainer.description.lowercase().contains(query) ||
                trainer.category.lowercase().contains(query) ||
                trainer.hobbyName.lowercase().contains(query)
            }
        }
        
        // Фильтрация по категории
        if (selectedCategory != null) {
            filteredTrainers = filteredTrainers.filter { it.category == selectedCategory }
        }
        
        // Фильтрация по полу
        if (selectedGender != null) {
            filteredTrainers = filteredTrainers.filter { it.gender == selectedGender }
        }
        
        applySorting()
    }
    
    private fun applySorting() {
        val sorted = when (currentSortType) {
            SortType.PRICE_ASC -> filteredTrainers.sortedBy { it.price }
            SortType.PRICE_DESC -> filteredTrainers.sortedByDescending { it.price }
            SortType.RATING_ASC -> filteredTrainers.sortedBy { it.rating }
            SortType.RATING_DESC -> filteredTrainers.sortedByDescending { it.rating }
            else -> filteredTrainers
        }
        
        adapter.updateTrainers(sorted)
    }

    private fun openTrainerProfile(trainer: Trainer) {
        val intent = Intent(this, TrainerProfileActivity::class.java)
        intent.putExtra("trainer", trainer)
        startActivity(intent)
    }

    private fun setupNotificationsIcon() {
        val currentUser = com.example.kursach.data.UserManager.getCurrentUser() ?: return
        
        // Создаем иконку уведомлений с бейджем
        val notificationIconView = android.view.LayoutInflater.from(this).inflate(R.layout.menu_notification_badge, null)
        val badgeText = notificationIconView.findViewById<android.widget.TextView>(R.id.badge_text)
        
        val unreadCount = NotificationManager.getUnreadCount(currentUser.id)
        if (unreadCount > 0) {
            badgeText.text = if (unreadCount > 99) "99+" else unreadCount.toString()
            badgeText.visibility = View.VISIBLE
        } else {
            badgeText.visibility = View.GONE
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
        
        // Добавляем кнопку создания анкеты для тренеров рядом с уведомлениями
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
            binding.toolbar.addView(createProfileIconView, binding.toolbar.indexOfChild(notificationIconView))
        }
    }
    
    private fun showNotificationsPopup(anchorView: View) {
        if (notificationPopup?.isShowing() == true) {
            notificationPopup?.dismiss()
            return
        }
        
        notificationPopup = NotificationPopup(this)
        notificationPopup?.show(anchorView)
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        
        // Убираем пункт "Создать анкету" из меню с троеточием
        val createProfileItem = menu?.findItem(R.id.menu_create_profile)
        createProfileItem?.isVisible = false
        
        return true
    }
    
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        binding.toolbar.overflowIcon?.setTint(ContextCompat.getColor(this, android.R.color.white))
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_terms -> {
                DocumentUtils.showDocumentDialog(this, R.string.terms_title, R.raw.terms)
                true
            }
            R.id.menu_privacy -> {
                DocumentUtils.showDocumentDialog(this, R.string.privacy_title, R.raw.privacy)
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
}
