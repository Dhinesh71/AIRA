package com.aira.app.ui

import com.aira.app.data.model.AssistantIntent
import com.aira.app.data.model.CommandHistoryItem
import com.aira.app.data.model.CommandSource
import com.aira.app.data.model.ShortcutSuggestion

data class MainUiState(
    val phase: AssistantPhase = AssistantPhase.IDLE,
    val statusHeadline: String = "Ready",
    val statusBody: String = "Enable Accessibility to let Aira control the device.",
    val continuousListeningEnabled: Boolean = false,
    val isListening: Boolean = false,
    val lastTranscript: String = "",
    val history: List<CommandHistoryItem> = emptyList(),
    val suggestions: List<ShortcutSuggestion> = emptyList(),
    val pendingConfirmation: PendingCommandConfirmation? = null,
    val accessibilityEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
)

enum class AssistantPhase {
    IDLE,
    LISTENING,
    INTERPRETING,
    AWAITING_CONFIRMATION,
    EXECUTING,
    COMPLETED,
    ERROR,
}

data class PendingCommandConfirmation(
    val commandId: Long,
    val spokenText: String,
    val source: CommandSource,
    val intent: AssistantIntent,
)

