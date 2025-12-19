package com.example.kursach

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase
import com.example.kursach.databinding.ActivityScheduleBinding
import com.example.kursach.databinding.ItemTimeSlotBinding
import com.example.kursach.model.Meeting
import com.example.kursach.model.Trainer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import androidx.appcompat.app.AlertDialog
import com.example.kursach.NotificationManager

class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding
    private lateinit var trainer: Trainer
    private var selectedDate: Calendar? = null
    private var selectedTime: String? = null
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private lateinit var timeSlotsAdapter: TimeSlotsAdapter
    private val availableTimeSlots = mutableListOf<String>()
    private var hasPaidForSession = false
    private val dayNamesShort = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        trainer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("trainer", Trainer::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Trainer>("trainer")
        } ?: run {
            Toast.makeText(this, "Ошибка загрузки тренера", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        binding.trainerNameSchedule.text = trainer.name
        
        if (trainer.hobbyName.isNotEmpty()) {
            binding.trainerHobbySchedule.text = trainer.hobbyName
            binding.trainerHobbySchedule.visibility = android.view.View.VISIBLE
        } else {
            binding.trainerHobbySchedule.visibility = android.view.View.GONE
        }
        
        val daysText = formatAllowedDays()
        val timeRangeText = getTimeRangeForDisplay()
        binding.availableTimeInfo.text = if (!timeRangeText.isNullOrEmpty()) {
            "Дни: $daysText\nВремя: $timeRangeText"
        } else {
            "Дни: $daysText"
        }

        // Парсим доступное время тренера
        parseAvailableTime()
        
        // Настраиваем RecyclerView для временных слотов
        timeSlotsAdapter = TimeSlotsAdapter(availableTimeSlots) { time ->
            selectedTime = time
            timeSlotsAdapter.selectedTime = time
            timeSlotsAdapter.notifyDataSetChanged()
            hasPaidForSession = false
            updateBookingSummary()
        }
        
        binding.timeSlotsRecyclerView.layoutManager = GridLayoutManager(this, 3)
        binding.timeSlotsRecyclerView.adapter = timeSlotsAdapter
        
        hasPaidForSession = false
        updateBookingSummary()
    }
    
    private fun parseAvailableTime() {
        availableTimeSlots.clear()
        
        if (trainer.availableTime.isEmpty()) {
            // Если время не указано, предлагаем стандартные слоты
            for (hour in 9..21) {
                availableTimeSlots.add(String.format(Locale.getDefault(), "%02d:00", hour))
            }
            return
        }
        
        // Парсим формат типа "Пн-Пт: 18:00-21:00" или "Сб-Вс: 11:00-17:00"
        val timePattern = Regex("""(\d{2}):(\d{2})\s*-\s*(\d{2}):(\d{2})""")
        val match = timePattern.find(trainer.availableTime)
        
        if (match != null) {
            val startHour = match.groupValues[1].toInt()
            val startMinute = match.groupValues[2].toInt()
            val endHour = match.groupValues[3].toInt()
            val endMinute = match.groupValues[4].toInt()
            
            // Генерируем временные слоты каждый час
            var currentHour = startHour
            while (currentHour <= endHour) {
                if (currentHour == startHour && startMinute > 0) {
                    // Пропускаем если начало не на целый час
                    currentHour++
                    continue
                }
                if (currentHour == endHour && endMinute < 30) {
                    // Не добавляем если конец меньше получаса
                    break
                }
                availableTimeSlots.add(String.format(Locale.getDefault(), "%02d:00", currentHour))
                currentHour++
            }
        } else {
            // Если формат не распознан, предлагаем стандартные слоты
            for (hour in 9..21) {
                availableTimeSlots.add(String.format(Locale.getDefault(), "%02d:00", hour))
            }
        }
        
        if (availableTimeSlots.isEmpty()) {
            // Если ничего не получилось, предлагаем стандартные слоты
            for (hour in 9..21) {
                availableTimeSlots.add(String.format(Locale.getDefault(), "%02d:00", hour))
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnPay.isEnabled = false
        binding.btnPay.setOnClickListener {
            showPaymentDialog()
        }

        binding.btnConfirmBooking.isEnabled = false
        binding.btnConfirmBooking.setOnClickListener {
            confirmBooking()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(selectedYear, selectedMonth, selectedDay)
                
                // Проверяем, что дата не в прошлом
                val today = Calendar.getInstance()
                today.set(Calendar.HOUR_OF_DAY, 0)
                today.set(Calendar.MINUTE, 0)
                today.set(Calendar.SECOND, 0)
                today.set(Calendar.MILLISECOND, 0)
                
                selectedCalendar.set(Calendar.HOUR_OF_DAY, 0)
                selectedCalendar.set(Calendar.MINUTE, 0)
                selectedCalendar.set(Calendar.SECOND, 0)
                selectedCalendar.set(Calendar.MILLISECOND, 0)
                
                if (selectedCalendar.before(today)) {
                    Toast.makeText(this, "Нельзя выбрать прошедшую дату", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }
                
                val dayOfWeek = selectedCalendar.get(Calendar.DAY_OF_WEEK)
                val dayIndex = mapDayOfWeekToIndex(dayOfWeek)
                if (!getAllowedDays().contains(dayIndex)) {
                    Toast.makeText(this, "Доступные дни: ${formatAllowedDays()}", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }
 
                selectedDate = selectedCalendar
                binding.selectedDateText.text = "Выбранная дата: ${dateFormat.format(selectedCalendar.time)}"
                binding.selectedDateText.visibility = android.view.View.VISIBLE
                hasPaidForSession = false
                updateBookingSummary()
            },
            year,
            month,
            day
        )
        
        // Устанавливаем минимальную дату (сегодня)
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    private fun updateBookingSummary() {
        val hasSelection = selectedDate != null && selectedTime != null
        binding.bookingSummaryCard.visibility = if (hasSelection) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnPay.isEnabled = hasSelection

        if (hasSelection) {
            binding.bookingDateText.text = "Дата: ${dateFormat.format(selectedDate!!.time)}"
            binding.bookingTimeText.text = "Время: $selectedTime"
        }

        binding.paymentStatusText.text = if (hasPaidForSession) {
            "Оплата: подтверждена"
        } else {
            "Оплата: не выполнена"
        }

        binding.btnConfirmBooking.isEnabled = hasSelection && hasPaidForSession
    }

    private fun confirmBooking() {
        if (selectedDate == null) {
            Toast.makeText(this, "Пожалуйста, выберите дату", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedTime == null) {
            Toast.makeText(this, "Пожалуйста, выберите время", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dateString = dateFormat.format(selectedDate!!.time)
        
        // Сохраняем запись в базу данных со статусом "pending"
        val user = UserManager.getCurrentUser()
        if (user != null) {
            val trainerOwnerId = trainer.userId.ifEmpty { trainer.id }
            if (user.id == trainerOwnerId) {
                Toast.makeText(this, "Вы не можете записаться на собственное занятие", Toast.LENGTH_SHORT).show()
                return
            }

            if (!hasPaidForSession) {
                Toast.makeText(this, "Перед подтверждением необходимо оплатить занятие", Toast.LENGTH_SHORT).show()
                return
            }

            val meeting = Meeting(
                id = UUID.randomUUID().toString(),
                trainerId = trainer.id,
                userId = user.id,
                date = dateString,
                time = selectedTime!!,
                status = "pending",
                isPaid = true,
                amountPaid = trainer.price
            )
            JsonDatabase.saveMeeting(meeting)
            
            // Отправляем сообщение в чат о запросе записи
            val chatId = JsonDatabase.generateChatId(user.id, trainerOwnerId)
            val message = com.example.kursach.model.Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                senderId = user.id,
                receiverId = trainerOwnerId,
                text = "Запрос на запись:\nДата: $dateString\nВремя: $selectedTime\nОплата: подтверждена (демо)\n\nСтатус: На рассмотрении\nТренер ответит в ближайшее время.",
                timestamp = System.currentTimeMillis(),
                isRead = false,
                meetingId = meeting.id
            )
            JsonDatabase.saveMessage(message)
            NotificationManager.showNotification(this, message)
        }
        
        Toast.makeText(
            this,
            "Запрос на запись отправлен!\nТренер ответит в ближайшее время.",
            Toast.LENGTH_LONG
        ).show()
        
        // Обновляем summary
        updateBookingSummary()
        
        // Закрываем активность после успешной записи
        finish()
    }
    
    // Адаптер для временных слотов
    private class TimeSlotsAdapter(
        private val timeSlots: List<String>,
        private val onTimeSelected: (String) -> Unit
    ) : RecyclerView.Adapter<TimeSlotsAdapter.TimeSlotViewHolder>() {
        
        var selectedTime: String? = null
        
        class TimeSlotViewHolder(val binding: ItemTimeSlotBinding) : RecyclerView.ViewHolder(binding.root)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeSlotViewHolder {
            val binding = ItemTimeSlotBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return TimeSlotViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: TimeSlotViewHolder, position: Int) {
            val time = timeSlots[position]
            holder.binding.timeSlotButton.text = time
            
            // Выделяем выбранное время
            if (time == selectedTime) {
                // Выбранная кнопка - залитая
                holder.binding.timeSlotButton.setBackgroundColor(
                    holder.itemView.context.getColor(com.example.kursach.R.color.primary_color)
                )
                holder.binding.timeSlotButton.setTextColor(
                    holder.itemView.context.getColor(android.R.color.white)
                )
                holder.binding.timeSlotButton.strokeWidth = 0
            } else {
                // Невыбранная кнопка - только обводка
                holder.binding.timeSlotButton.setBackgroundColor(
                    holder.itemView.context.getColor(android.R.color.transparent)
                )
                holder.binding.timeSlotButton.setTextColor(
                    holder.itemView.context.getColor(com.example.kursach.R.color.primary_color)
                )
                holder.binding.timeSlotButton.strokeWidth = 2
            }
            
            holder.binding.timeSlotButton.setOnClickListener {
                onTimeSelected(time)
            }
        }
        
        override fun getItemCount() = timeSlots.size
    }

    private fun formatAllowedDays(): String {
        val days = getAllowedDays()
        return if (days.size == 7) {
            "ежедневно"
        } else {
            days.sorted().map { dayNamesShort[it] }.joinToString(", ")
        }
    }

    private fun getTimeRangeForDisplay(): String? {
        val parts = trainer.availableTime.split(": ")
        return parts.getOrNull(1)?.trim()
    }

    private fun mapDayOfWeekToIndex(dayOfWeek: Int): Int {
        return when (dayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
    }

    private fun showPaymentDialog() {
        if (selectedDate == null || selectedTime == null) {
            Toast.makeText(this, "Сначала выберите дату и время", Toast.LENGTH_SHORT).show()
            return
        }

        val user = UserManager.getCurrentUser()
        if (user == null) {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
            return
        }

        val price = trainer.price
        val message = buildString {
            append("Стоимость занятия: ")
            append(price)
            append(" ₽\n")
            append("Оплата будет списана с баланса.")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Оплата занятия")
            .setMessage(message)
            .setPositiveButton("Оплатить", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val currentUser = UserManager.getCurrentUser()
                if (currentUser == null) {
                    Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setOnClickListener
                }
                if (currentUser.balance < price) {
                    Toast.makeText(this, getString(R.string.insufficient_funds), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val updatedUser = JsonDatabase.updateUserBalance(currentUser.id, currentUser.balance - price)
                if (updatedUser == null) {
                    Toast.makeText(this, "Не удалось списать оплату", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                UserManager.setCurrentUser(updatedUser)
                hasPaidForSession = true
                updateBookingSummary()
                AlertDialog.Builder(this)
                    .setTitle(R.string.payment_success_title)
                    .setMessage(getString(R.string.payment_success_message))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun getAllowedDays(): List<Int> {
        val explicit = trainer.availableDays.distinct().sorted()
        if (explicit.isNotEmpty()) return explicit

        val dayPart = trainer.availableTime.substringBefore(":").trim()
        val parsed = parseDays(dayPart)
        return if (parsed.isNotEmpty()) parsed else (0..6).toList()
    }

    private fun parseDays(dayPart: String): List<Int> {
        if (dayPart.isEmpty()) return emptyList()
        val compact = dayPart.replace(" ", "")
        return when {
            compact.contains(",") -> compact.split(",")
                .mapNotNull { dayLabelToIndex(it) }
                .distinct()
                .sorted()
            compact.contains("-") -> {
                val parts = compact.split("-")
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
            else -> dayLabelToIndex(compact)?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun dayLabelToIndex(label: String): Int? {
        val lower = label.lowercase()
        return when {
            lower.startsWith("пн") -> 0
            lower.startsWith("вт") -> 1
            lower.startsWith("ср") -> 2
            lower.startsWith("чт") -> 3
            lower.startsWith("пт") -> 4
            lower.startsWith("сб") -> 5
            lower.startsWith("вс") -> 6
            else -> null
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

