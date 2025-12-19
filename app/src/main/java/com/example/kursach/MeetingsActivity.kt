package com.example.kursach

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase
import com.example.kursach.databinding.ActivityMeetingsBinding
import com.example.kursach.databinding.ItemCalendarDayBinding
import com.example.kursach.databinding.ItemMeetingBinding
import com.example.kursach.model.Meeting
import com.example.kursach.ui.BottomNavigationHelper
import com.example.kursach.utils.MeetingUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MeetingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMeetingsBinding
    private var selectedDate: Calendar? = null
    private var currentCalendar: Calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("ru"))
    private lateinit var meetingsAdapter: MeetingsAdapter
    private var allMeetings: List<Meeting> = emptyList()
    private val daysWithMeetings = mutableSetOf<String>()
    private var notificationPopup: NotificationPopup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeetingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.navigationIcon = null
        // Устанавливаем цвет троеточия сразу
        binding.toolbar.post {
            binding.toolbar.overflowIcon?.setTint(ContextCompat.getColor(this, android.R.color.white))
        }
        
        // Устанавливаем заголовок в зависимости от типа пользователя
        val user = UserManager.getCurrentUser()
        supportActionBar?.title = if (user?.userType == com.example.kursach.model.UserType.TRAINER) {
            "Записи клиентов"
        } else {
            "Мои записи"
        }

        BottomNavigationHelper.setup(binding.bottomNavigation, this, R.id.navigation_calendar)

        setupNotificationsIcon()
        setupCreateProfileButton()
        setupRecyclerView()
        loadMeetings()
        setupCalendar()
    }
    
    private fun setupCreateProfileButton() {
        val currentUser = UserManager.getCurrentUser() ?: return
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
            val notificationView = binding.toolbar.findViewById<TextView>(R.id.badge_text)?.parent as? android.view.View
            if (notificationView != null) {
                binding.toolbar.addView(createProfileIconView, binding.toolbar.indexOfChild(notificationView))
            } else {
                binding.toolbar.addView(createProfileIconView)
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu?.findItem(R.id.menu_create_profile)?.isVisible = false
        return true
    }
    
    override fun onPrepareOptionsMenu(menu: android.view.Menu?): Boolean {
        binding.toolbar.overflowIcon?.setTint(ContextCompat.getColor(this, android.R.color.white))
        return super.onPrepareOptionsMenu(menu)
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_terms -> {
                com.example.kursach.utils.DocumentUtils.showDocumentDialog(this, R.string.terms_title, R.raw.terms)
                true
            }
            R.id.menu_privacy -> {
                com.example.kursach.utils.DocumentUtils.showDocumentDialog(this, R.string.privacy_title, R.raw.privacy)
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
    
    private fun setupNotificationsIcon() {
        val currentUser = UserManager.getCurrentUser() ?: return
        
        // Создаем иконку уведомлений с бейджем
        val notificationIconView = LayoutInflater.from(this).inflate(R.layout.menu_notification_badge, null)
        val badgeText = notificationIconView.findViewById<TextView>(R.id.badge_text)
        
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
    }
    
    private fun showNotificationsPopup(anchorView: View) {
        if (notificationPopup?.isShowing() == true) {
            notificationPopup?.dismiss()
            return
        }
        
        notificationPopup = NotificationPopup(this)
        notificationPopup?.show(anchorView)
    }
    
    private fun updateNotificationsIcon() {
        val currentUser = UserManager.getCurrentUser() ?: return
        val unreadCount = NotificationManager.getUnreadCount(currentUser.id)
        
        // Находим view уведомлений в toolbar
        val notificationView = binding.toolbar.findViewById<View>(R.id.badge_text)?.parent as? View
        if (notificationView != null) {
            val badgeText = notificationView.findViewById<TextView>(R.id.badge_text)
            if (unreadCount > 0) {
                badgeText.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                badgeText.visibility = View.VISIBLE
            } else {
                badgeText.visibility = View.GONE
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateNotificationsIcon()
        // Обновляем записи при возврате на экран
        loadMeetings()
    }

    private fun setupRecyclerView() {
        meetingsAdapter = MeetingsAdapter(emptyList(), ::releasePaymentFromMeeting)
        binding.meetingsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.meetingsRecyclerView.adapter = meetingsAdapter
    }

    private fun loadMeetings() {
        val user = UserManager.getCurrentUser()
        if (user == null) {
            Toast.makeText(this, "Ошибка загрузки пользователя", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Загружаем записи в зависимости от типа пользователя
        // Показываем только подтвержденные записи и для пользователя, и для тренера
        allMeetings = if (user.userType == com.example.kursach.model.UserType.TRAINER) {
            // Для тренера загружаем записи, где он является тренером
            JsonDatabase.getMeetingsForTrainer(user.id).filter { it.status == "confirmed" }
        } else {
            // Для пользователя загружаем только подтвержденные записи
            JsonDatabase.getMeetingsForUser(user.id).filter { it.status == "confirmed" }
        }
        
        // Собираем даты с записями
        daysWithMeetings.clear()
        allMeetings.forEach { meeting ->
            // Используем дату, выбранную тренером, если она есть, иначе исходную дату
            val meetingDate = meeting.trainerSelectedDate ?: meeting.date
            daysWithMeetings.add(meetingDate)
        }
        
        updateCalendar()
        updateMeetingsList()
    }

    private fun setupCalendar() {
        binding.btnPrevMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateCalendar()
        }
        
        binding.btnNextMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateCalendar()
        }
        
        updateCalendar()
    }

    private fun updateCalendar() {
        binding.monthYearText.text = monthFormat.format(currentCalendar.time).replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("ru")) else it.toString() 
        }
        
        binding.calendarTable.removeAllViews()
        
        // Вычисляем размер ячейки для квадратных кнопок
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val paddingDp = 16f + 20f // padding layout + padding карточки
        val paddingPx = (paddingDp * displayMetrics.density).toInt() * 2
        val marginPx = (4f * displayMetrics.density).toInt() * 14 // 7 колонок * 2 отступа
        val availableWidth = screenWidth - paddingPx
        val cellSize = maxOf(48, (availableWidth - marginPx) / 7)
        
        // Создаем строку заголовков дней недели
        val headerRow = TableRow(this)
        val dayHeaders = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        for (dayHeader in dayHeaders) {
            val textView = TextView(this).apply {
                text = dayHeader
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MeetingsActivity, R.color.text_secondary))
                gravity = android.view.Gravity.CENTER
                setPadding(8, 8, 8, 8)
            }
            textView.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
            headerRow.addView(textView)
        }
        binding.calendarTable.addView(headerRow)
        
        // Получаем первый день месяца и количество дней
        val calendar = currentCalendar.clone() as Calendar
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        // Преобразуем день недели (воскресенье = 1 -> воскресенье = 7, понедельник = 2 -> 0)
        var startOffset = firstDayOfWeek - 2
        if (startOffset < 0) startOffset += 7
        
        // Создаем первую строку с пустыми ячейками и днями
        var currentRow = TableRow(this)
        
        // Пустые ячейки до первого дня
        for (i in 0 until startOffset) {
            val emptyView = View(this)
            emptyView.layoutParams = TableRow.LayoutParams(0, cellSize, 1f)
            currentRow.addView(emptyView)
        }
        
        // Дни месяца
        for (day in 1..daysInMonth) {
            val dayBinding = ItemCalendarDayBinding.inflate(layoutInflater)
            val dayCalendar = currentCalendar.clone() as Calendar
            dayCalendar.set(Calendar.DAY_OF_MONTH, day)
            val dateString = dateFormat.format(dayCalendar.time)
            
            dayBinding.dayNumber.text = day.toString()
            
            // Сбрасываем стили по умолчанию
            dayBinding.root.setCardBackgroundColor(Color.TRANSPARENT)
            dayBinding.dayNumber.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            dayBinding.root.strokeWidth = 1
            dayBinding.root.strokeColor = ContextCompat.getColor(this, R.color.text_secondary)
            
            val today = Calendar.getInstance()
            val isToday = dayCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    dayCalendar.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                    dayCalendar.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
            
            val isSelected = selectedDate != null && 
                    dayCalendar.get(Calendar.YEAR) == selectedDate!!.get(Calendar.YEAR) &&
                    dayCalendar.get(Calendar.MONTH) == selectedDate!!.get(Calendar.MONTH) &&
                    dayCalendar.get(Calendar.DAY_OF_MONTH) == selectedDate!!.get(Calendar.DAY_OF_MONTH)
            
            // Проверяем наличие записей на эту дату (используем trainerSelectedDate если есть)
            val hasMeeting = allMeetings.any { meeting ->
                val meetingDate = meeting.trainerSelectedDate ?: meeting.date
                meetingDate == dateString
            }
            
            // Отмечаем дни с записями синим цветом
            if (hasMeeting) {
                dayBinding.root.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary_color))
                dayBinding.dayNumber.setTextColor(ContextCompat.getColor(this, R.color.white))
                dayBinding.root.strokeWidth = 1
                dayBinding.root.strokeColor = ContextCompat.getColor(this, R.color.primary_dark)
            }
            
            // Выделяем выбранную дату (приоритет над остальными)
            if (isSelected) {
                dayBinding.root.strokeWidth = 2
                dayBinding.root.strokeColor = ContextCompat.getColor(this, R.color.accent_color)
            } else if (isToday && !hasMeeting && !isSelected) {
                // Выделяем сегодня только если нет записи и не выбрана дата
                dayBinding.root.strokeWidth = 1
                dayBinding.root.strokeColor = ContextCompat.getColor(this, R.color.accent_color)
            }
            
            dayBinding.root.setOnClickListener {
                selectedDate = dayCalendar.clone() as Calendar
                binding.selectedDateText.text = "Выбранная дата: ${dateFormat.format(dayCalendar.time)}"
                updateCalendar()
                updateMeetingsList()
            }
            
            // Устанавливаем параметры для TableRow с квадратным размером
            val params = TableRow.LayoutParams(0, cellSize, 1f)
            dayBinding.root.layoutParams = params
            currentRow.addView(dayBinding.root)
            
            // Если строка заполнена (7 дней), добавляем её и создаем новую
            if (currentRow.childCount == 7) {
                binding.calendarTable.addView(currentRow)
                currentRow = TableRow(this)
            }
        }
        
        // Добавляем последнюю строку, если она не пустая
        if (currentRow.childCount > 0) {
            // Заполняем оставшиеся ячейки пустыми View
            while (currentRow.childCount < 7) {
                val emptyView = View(this)
                emptyView.layoutParams = TableRow.LayoutParams(0, cellSize, 1f)
                currentRow.addView(emptyView)
            }
            binding.calendarTable.addView(currentRow)
        }
    }

    private fun updateMeetingsList() {
        val filteredMeetings = if (selectedDate != null) {
            val selectedDateString = dateFormat.format(selectedDate!!.time)
            allMeetings.filter { meeting ->
                // Используем дату, выбранную тренером, если она есть, иначе исходную дату
                val meetingDate = meeting.trainerSelectedDate ?: meeting.date
                meetingDate == selectedDateString
            }
        } else {
            emptyList()
        }

        meetingsAdapter.updateMeetings(filteredMeetings)

        if (selectedDate != null) {
            if (filteredMeetings.isEmpty()) {
                binding.emptyMeetingsText.visibility = View.VISIBLE
                binding.meetingsRecyclerView.visibility = View.GONE
                binding.emptyMeetingsText.text = "Нет записей на выбранную дату"
            } else {
                binding.emptyMeetingsText.visibility = View.GONE
                binding.meetingsRecyclerView.visibility = View.VISIBLE
            }
        } else {
            binding.emptyMeetingsText.visibility = View.GONE
            binding.meetingsRecyclerView.visibility = View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun releasePaymentFromMeeting(meeting: Meeting) {
        val currentUser = UserManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentUser.id != meeting.userId) {
            Toast.makeText(this, "Только клиент может подтвердить оплату", Toast.LENGTH_SHORT).show()
            return
        }

        if (!MeetingUtils.isMeetingInPast(meeting)) {
            Toast.makeText(this, "Оплату можно подтвердить после завершения занятия", Toast.LENGTH_SHORT).show()
            return
        }

        val result = JsonDatabase.releasePaymentForMeeting(meeting.id)
        if (result != null) {
            val (updatedMeeting, updatedTrainer) = result
            updatedTrainer?.let {
                if (UserManager.getCurrentUser()?.id == it.id) {
                    UserManager.setCurrentUser(it)
                }
            }
            Toast.makeText(this, getString(R.string.release_payment_success), Toast.LENGTH_SHORT).show()
            // Обновляем список
            loadMeetings()
        } else {
            Toast.makeText(this, getString(R.string.release_payment_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private class MeetingsAdapter(
        private var meetings: List<Meeting>,
        private val onReleasePayment: (Meeting) -> Unit
    ) : RecyclerView.Adapter<MeetingsAdapter.MeetingViewHolder>() {

        fun updateMeetings(newMeetings: List<Meeting>) {
            meetings = newMeetings
            notifyDataSetChanged()
        }

        class MeetingViewHolder(val binding: ItemMeetingBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeetingViewHolder {
            val binding = ItemMeetingBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return MeetingViewHolder(binding)
        }

        override fun onBindViewHolder(holder: MeetingViewHolder, position: Int) {
            val meeting = meetings[position]
            val currentUser = UserManager.getCurrentUser()
            
            // Используем дату и время, выбранные тренером, если они есть
            val meetingDate = meeting.trainerSelectedDate ?: meeting.date
            val meetingTime = meeting.trainerSelectedTime ?: meeting.time
            
            if (currentUser?.userType == com.example.kursach.model.UserType.TRAINER) {
                // Для тренера показываем информацию о пользователе, который записался
                val user = JsonDatabase.getUserById(meeting.userId)
                val userName = user?.name ?: "Пользователь"
                
                // Получаем информацию о типе занятия из профиля тренера
                val trainer = JsonDatabase.getTrainerById(meeting.trainerId)
                val hobbyName = trainer?.hobbyName ?: trainer?.category ?: "Занятие"
                
                holder.binding.trainerNameText.text = "$userName - $hobbyName"
                holder.binding.meetingDateText.text = meetingDate
                holder.binding.meetingTimeText.text = meetingTime
            } else {
                // Для пользователя показываем информацию о тренере
                val trainer = JsonDatabase.getTrainerById(meeting.trainerId)
                val trainerName = trainer?.name ?: "Тренер"
                val hobbyName = trainer?.hobbyName ?: trainer?.category ?: "Занятие"
                
                holder.binding.trainerNameText.text = "$trainerName - $hobbyName"
                holder.binding.meetingDateText.text = meetingDate
                holder.binding.meetingTimeText.text = meetingTime
            }

            val shouldShowRelease = currentUser?.id == meeting.userId &&
                    meeting.status == "confirmed" &&
                    meeting.isPaid &&
                    !meeting.isPaymentReleased &&
                    MeetingUtils.isMeetingInPast(meeting)

            if (shouldShowRelease) {
                holder.binding.releasePaymentButton.visibility = View.VISIBLE
                holder.binding.releasePaymentButton.setOnClickListener {
                    onReleasePayment(meeting)
                }
            } else {
                holder.binding.releasePaymentButton.visibility = View.GONE
                holder.binding.releasePaymentButton.setOnClickListener(null)
            }
        }

        override fun getItemCount() = meetings.size
    }
}

