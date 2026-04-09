package com.aira.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CommandHistoryEntity): Long

    @Query(
        """
        SELECT * FROM command_history
        ORDER BY created_at DESC
        LIMIT 50
        """,
    )
    fun observeRecentHistory(): Flow<List<CommandHistoryEntity>>

    @Query(
        """
        UPDATE command_history
        SET status = :status,
            result_summary = :resultSummary
        WHERE id = :commandId
        """,
    )
    suspend fun updateStatus(
        commandId: Long,
        status: String,
        resultSummary: String,
    )

    @Query(
        """
        SELECT spoken_text
        FROM command_history
        ORDER BY created_at DESC
        LIMIT :limit
        """,
    )
    suspend fun recentSpokenTexts(limit: Int = 5): List<String>

    @Query(
        """
        SELECT
            action_type AS actionType,
            app_name AS appName,
            COUNT(*) AS usageCount
        FROM command_history
        WHERE action_type != ''
        GROUP BY action_type, app_name
        ORDER BY usageCount DESC, MAX(created_at) DESC
        LIMIT 4
        """,
    )
    fun observeShortcutRows(): Flow<List<ShortcutSuggestionRow>>
}

data class ShortcutSuggestionRow(
    @ColumnInfo(name = "actionType") val actionType: String,
    @ColumnInfo(name = "appName") val appName: String,
    @ColumnInfo(name = "usageCount") val usageCount: Int,
)

