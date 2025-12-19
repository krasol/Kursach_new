package com.example.kursach

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kursach.data.UserManager
import com.example.kursach.database.JsonDatabase
import com.example.kursach.databinding.ActivityAdminBinding
import com.example.kursach.model.Report
import com.example.kursach.model.ReportStatus
import com.example.kursach.model.ReportTargetType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private lateinit var adapter: ReportsAdapter
    private var currentFilter: ReportStatus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Проверяем авторизацию и права админа
        val currentUser = UserManager.getCurrentUser()
        if (currentUser == null || currentUser.userType != com.example.kursach.model.UserType.TECH_ADMIN) {
            Toast.makeText(this, "Доступ запрещен", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Панель администратора"
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        binding.toolbar.navigationIcon = null

        setupRecyclerView()
        setupFilterButtons()
        loadReports()

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Выйти из аккаунта")
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
    }

    private fun setupRecyclerView() {
        adapter = ReportsAdapter(emptyList()) { report ->
            showReportDetails(report)
        }
        binding.reportsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.reportsRecyclerView.adapter = adapter
    }

    private fun setupFilterButtons() {
        binding.btnAll.setOnClickListener {
            currentFilter = null
            loadReports()
        }
        
        binding.btnPending.setOnClickListener {
            currentFilter = ReportStatus.PENDING
            loadReports()
        }
        
        binding.btnResolved.setOnClickListener {
            currentFilter = ReportStatus.ACTION_TAKEN
            loadReports()
        }
        
        binding.btnDismissed.setOnClickListener {
            currentFilter = ReportStatus.DISMISSED
            loadReports()
        }
    }

    private fun loadReports() {
        val reports = JsonDatabase.getReports(currentFilter)
        adapter.updateReports(reports)
        
        if (reports.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            binding.reportsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyText.visibility = View.GONE
            binding.reportsRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showReportDetails(report: Report) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_report_details, null)
        val targetTypeText = dialogView.findViewById<TextView>(R.id.targetTypeText)
        val targetNameText = dialogView.findViewById<TextView>(R.id.targetNameText)
        val reporterNameText = dialogView.findViewById<TextView>(R.id.reporterNameText)
        val reasonText = dialogView.findViewById<TextView>(R.id.reasonText)
        val statusText = dialogView.findViewById<TextView>(R.id.statusText)
        val createdAtText = dialogView.findViewById<TextView>(R.id.createdAtText)
        val btnViewTarget = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewTarget)
        val btnDelete = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDelete)
        val btnBan = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBan)
        val btnDismiss = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDismiss)
        
        targetTypeText.text = when (report.targetType) {
            ReportTargetType.TRAINER_PROFILE -> "Анкета тренера"
            ReportTargetType.USER_PROFILE -> "Профиль пользователя"
            ReportTargetType.CHAT -> "Чат"
        }
        targetNameText.text = report.targetName
        reporterNameText.text = report.reporterName
        reasonText.text = report.reason
        statusText.text = when (report.status) {
            ReportStatus.PENDING -> "На рассмотрении"
            ReportStatus.ACTION_TAKEN -> "Приняты меры"
            ReportStatus.DISMISSED -> "Отклонена"
        }
        
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        createdAtText.text = dateFormat.format(Date(report.createdAt))
        
        // Показываем кнопки только для pending жалоб
        val isPending = report.status == ReportStatus.PENDING
        btnDelete.visibility = if (isPending && report.targetType == ReportTargetType.TRAINER_PROFILE) View.VISIBLE else View.GONE
        btnBan.visibility = if (isPending && (report.targetType == ReportTargetType.USER_PROFILE || report.targetType == ReportTargetType.CHAT)) View.VISIBLE else View.GONE
        btnDismiss.visibility = if (isPending) View.VISIBLE else View.GONE
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Детали жалобы")
            .setNegativeButton("Закрыть", null)
            .create()
        
        btnViewTarget.setOnClickListener {
            when (report.targetType) {
                ReportTargetType.TRAINER_PROFILE -> {
                    val trainer = JsonDatabase.getTrainerById(report.targetId)
                    if (trainer != null) {
                        val intent = Intent(this, TrainerProfileActivity::class.java)
                        intent.putExtra("trainer", trainer)
                        intent.putExtra("fromAdmin", true)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Анкета не найдена", Toast.LENGTH_SHORT).show()
                    }
                }
                ReportTargetType.USER_PROFILE -> {
                    val intent = Intent(this, UserProfileActivity::class.java)
                    intent.putExtra("viewUserId", report.targetId)
                    intent.putExtra("fromAdmin", true)
                    startActivity(intent)
                }
                ReportTargetType.CHAT -> {
                    // Для чата можно открыть чат или профиль пользователя
                    if (report.chatType == "group") {
                        val intent = Intent(this, GroupMessengerActivity::class.java)
                        intent.putExtra("groupChatId", report.targetId)
                        intent.putExtra("fromAdmin", true)
                        startActivity(intent)
                    } else {
                        // Для приватного чата открываем MessengerActivity
                        val chatId = report.targetId
                        val adminUser = UserManager.getCurrentUser()
                        if (adminUser != null) {
                            // Получаем trainer из chatId
                            val trainer = JsonDatabase.getTrainerFromChatId(chatId, adminUser.id)
                            if (trainer != null) {
                                val intent = Intent(this, MessengerActivity::class.java)
                                intent.putExtra("trainer", trainer)
                                intent.putExtra("fromAdmin", true)
                                startActivity(intent)
                            } else {
                                Toast.makeText(this, "Чат не найден", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
        
        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Удалить анкету")
                .setMessage("Вы уверены, что хотите удалить эту анкету?")
                .setPositiveButton("Удалить") { _, _ ->
                    JsonDatabase.deleteTrainer(report.targetId)
                    val updatedReport = report.copy(
                        status = ReportStatus.ACTION_TAKEN,
                        resolvedAt = System.currentTimeMillis(),
                        resolvedBy = UserManager.getCurrentUser()?.id,
                        resolvedByName = UserManager.getCurrentUser()?.name,
                        action = "Анкета удалена"
                    )
                    JsonDatabase.updateReport(updatedReport)
                    Toast.makeText(this, "Анкета удалена", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadReports()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
        
        btnBan.setOnClickListener {
            // Определяем userId для бана
            val userIdToBan = when (report.targetType) {
                ReportTargetType.USER_PROFILE -> report.targetId
                ReportTargetType.CHAT -> {
                    // Для чата получаем userId из chatId
                    val adminUser = UserManager.getCurrentUser()
                    if (adminUser != null && report.chatType == "private") {
                        val chatId = report.targetId
                        val parts = chatId.split("_")
                        if (parts.size == 2) {
                            // Получаем оба userId из chatId
                            val userId1 = parts[0]
                            val userId2 = parts[1]
                            // Показываем диалог выбора пользователя
                            val users = listOf(
                                JsonDatabase.getUserById(userId1),
                                JsonDatabase.getUserById(userId2)
                            ).filterNotNull()
                            
                            if (users.size == 2) {
                                val userNames = users.map { it.name }.toTypedArray()
                                AlertDialog.Builder(this)
                                    .setTitle("Выберите пользователя для бана")
                                    .setItems(userNames) { _, which ->
                                        banUserFromChat(users[which].id, report, dialog)
                                    }
                                    .setNegativeButton("Отмена", null)
                                    .show()
                                return@setOnClickListener
                            } else if (users.size == 1) {
                                banUserFromChat(users[0].id, report, dialog)
                                return@setOnClickListener
                            }
                        }
                    }
                    null
                }
                else -> null
            }
            
            if (userIdToBan == null) {
                Toast.makeText(this, "Не удалось определить пользователя", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            banUserFromChat(userIdToBan, report, dialog)
        }
        
        btnDismiss.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Отклонить жалобу")
                .setMessage("Вы уверены, что хотите отклонить эту жалобу?")
                .setPositiveButton("Отклонить") { _, _ ->
                    val updatedReport = report.copy(
                        status = ReportStatus.DISMISSED,
                        resolvedAt = System.currentTimeMillis(),
                        resolvedBy = UserManager.getCurrentUser()?.id,
                        resolvedByName = UserManager.getCurrentUser()?.name,
                        action = "Жалоба отклонена"
                    )
                    JsonDatabase.updateReport(updatedReport)
                    Toast.makeText(this, "Жалоба отклонена", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadReports()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
        
        dialog.show()
    }
    
    private fun banUserFromChat(userId: String, report: Report, dialog: AlertDialog) {
        AlertDialog.Builder(this)
            .setTitle("Забанить пользователя")
            .setMessage("Вы уверены, что хотите забанить этого пользователя?")
            .setPositiveButton("Забанить") { _, _ ->
                val bannedUser = JsonDatabase.banUser(userId)
                if (bannedUser != null) {
                    val updatedReport = report.copy(
                        status = ReportStatus.ACTION_TAKEN,
                        resolvedAt = System.currentTimeMillis(),
                        resolvedBy = UserManager.getCurrentUser()?.id,
                        resolvedByName = UserManager.getCurrentUser()?.name,
                        action = "Пользователь забанен"
                    )
                    JsonDatabase.updateReport(updatedReport)
                    Toast.makeText(this, "Пользователь забанен", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadReports()
                } else {
                    Toast.makeText(this, "Не удалось забанить пользователя", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private class ReportsAdapter(
        private var reports: List<Report>,
        private val onReportClick: (Report) -> Unit
    ) : RecyclerView.Adapter<ReportsAdapter.ReportViewHolder>() {

        class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val targetType: TextView = itemView.findViewById(R.id.targetType)
            val targetName: TextView = itemView.findViewById(R.id.targetName)
            val reporterName: TextView = itemView.findViewById(R.id.reporterName)
            val reason: TextView = itemView.findViewById(R.id.reason)
            val status: TextView = itemView.findViewById(R.id.status)
            val createdAt: TextView = itemView.findViewById(R.id.createdAt)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_report, parent, false)
            return ReportViewHolder(view)
        }

        override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
            val report = reports[position]
            
            holder.targetType.text = when (report.targetType) {
                ReportTargetType.TRAINER_PROFILE -> "Анкета"
                ReportTargetType.USER_PROFILE -> "Пользователь"
                ReportTargetType.CHAT -> "Чат"
            }
            holder.targetName.text = report.targetName
            holder.reporterName.text = "От: ${report.reporterName}"
            holder.reason.text = report.reason
            holder.status.text = when (report.status) {
                ReportStatus.PENDING -> "На рассмотрении"
                ReportStatus.ACTION_TAKEN -> "Приняты меры"
                ReportStatus.DISMISSED -> "Отклонена"
            }
            
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            holder.createdAt.text = dateFormat.format(Date(report.createdAt))
            
            // Цвет статуса
            holder.status.setTextColor(
                ContextCompat.getColor(
                    holder.itemView.context,
                    when (report.status) {
                        ReportStatus.PENDING -> android.R.color.holo_orange_dark
                        ReportStatus.ACTION_TAKEN -> android.R.color.holo_red_dark
                        ReportStatus.DISMISSED -> android.R.color.darker_gray
                    }
                )
            )
            
            holder.itemView.setOnClickListener {
                onReportClick(report)
            }
        }

        override fun getItemCount() = reports.size

        fun updateReports(newReports: List<Report>) {
            reports = newReports
            notifyDataSetChanged()
        }
    }
}

