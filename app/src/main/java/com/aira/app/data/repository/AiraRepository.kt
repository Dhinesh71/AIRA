package com.aira.app.data.repository

import com.aira.app.data.local.CommandHistoryDao
import com.aira.app.data.local.CommandHistoryEntity
import com.aira.app.data.model.AssistantIntent
import com.aira.app.data.model.CommandHistoryItem
import com.aira.app.data.model.CommandSource
import com.aira.app.data.model.InterpretRequest
import com.aira.app.data.model.ShortcutSuggestion
import com.aira.app.data.remote.AiraApi
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

class AiraRepository(
    private val api: AiraApi,
    private val commandDao: CommandHistoryDao,
    private val gson: Gson = Gson(),
) {
    fun observeHistory(): Flow<List<CommandHistoryItem>> =
        commandDao.observeRecentHistory().map { entities ->
            entities.map { entity ->
                CommandHistoryItem(
                    id = entity.id,
                    spokenText = entity.spokenText,
                    actionSummary = entity.actionSummary,
                    status = entity.status,
                    resultSummary = entity.resultSummary,
                    createdAt = entity.createdAt,
                    requiresConfirmation = entity.requiresConfirmation,
                    source = entity.source,
                )
            }
        }

    fun observeShortcutSuggestions(): Flow<List<ShortcutSuggestion>> =
        commandDao.observeShortcutRows().map { rows ->
            rows.map { row ->
                val label = when (row.actionType) {
                    "open_app" -> "Open ${row.appName.ifBlank { "app" }}"
                    "navigate" -> "Navigate"
                    "trigger_task" -> "Run ${row.appName.ifBlank { "task" }}"
                    else -> row.actionType.replace("_", " ")
                        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
                }
                val utterance = when (row.actionType) {
                    "open_app" -> "Open ${row.appName}".trim()
                    "navigate" -> "Go home"
                    "trigger_task" -> "Run ${row.appName}".trim()
                    else -> label
                }
                ShortcutSuggestion(
                    label = label,
                    utterance = utterance,
                    usageCount = row.usageCount,
                )
            }
        }

    suspend fun recentCommands(limit: Int = 5): List<String> = commandDao.recentSpokenTexts(limit)

    suspend fun interpretCommand(
        spokenText: String,
        localeTag: String,
    ): AssistantIntent {
        val response = api.interpret(
            InterpretRequest(
                text = spokenText,
                locale = localeTag,
                recentCommands = recentCommands(),
            ),
        )
        return response.intent
    }

    suspend fun insertCommand(
        spokenText: String,
        source: CommandSource,
        intent: AssistantIntent,
        status: String,
        resultSummary: String = "",
    ): Long {
        return commandDao.insert(
            CommandHistoryEntity(
                spokenText = spokenText,
                actionType = intent.action,
                actionSummary = intent.displayTitle(),
                appName = intent.appName,
                status = status,
                source = source.name,
                requiresConfirmation = intent.requiresConfirmation,
                intentPayload = gson.toJson(intent),
                resultSummary = resultSummary,
            ),
        )
    }

    suspend fun updateCommandStatus(
        commandId: Long,
        status: String,
        resultSummary: String,
    ) {
        commandDao.updateStatus(
            commandId = commandId,
            status = status,
            resultSummary = resultSummary,
        )
    }
}

