package com.aira.app.data.model

data class CommandHistoryItem(
    val id: Long,
    val spokenText: String,
    val actionSummary: String,
    val status: String,
    val resultSummary: String,
    val createdAt: Long,
    val requiresConfirmation: Boolean,
    val source: String,
)

