package com.aira.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "spoken_text") val spokenText: String,
    @ColumnInfo(name = "action_type") val actionType: String,
    @ColumnInfo(name = "action_summary") val actionSummary: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "requires_confirmation") val requiresConfirmation: Boolean,
    @ColumnInfo(name = "intent_payload") val intentPayload: String,
    @ColumnInfo(name = "result_summary") val resultSummary: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

