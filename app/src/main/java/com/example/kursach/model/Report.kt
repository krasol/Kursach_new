package com.example.kursach.model

data class Report(
    val id: String,
    val reporterId: String?,
    val reporterName: String,
    val targetId: String,
    val targetType: ReportTargetType,
    val targetName: String,
    val reason: String,
    val status: ReportStatus = ReportStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val resolvedBy: String? = null,
    val resolvedByName: String? = null,
    val action: String? = null,
    val notes: String? = null,
    val chatType: String? = null
)

