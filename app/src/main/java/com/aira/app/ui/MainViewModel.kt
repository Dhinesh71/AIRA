package com.aira.app.ui

import android.app.Application
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aira.app.AiraApplication
import com.aira.app.automation.AiraAccessibilityService
import com.aira.app.automation.AutomationValidator
import com.aira.app.data.model.AssistantIntent
import com.aira.app.data.model.CommandSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val container = (application as AiraApplication).container
    private val repository = container.repository
    private val actionExecutor = container.actionExecutor
    private val validator = AutomationValidator()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeHistory().collectLatest { history ->
                _uiState.update { current -> current.copy(history = history) }
            }
        }

        viewModelScope.launch {
            repository.observeShortcutSuggestions().collectLatest { suggestions ->
                _uiState.update { current -> current.copy(suggestions = suggestions) }
            }
        }

        viewModelScope.launch {
            AiraAccessibilityService.connectionState.collectLatest { connected ->
                _uiState.update { current -> current.copy(accessibilityEnabled = connected) }
            }
        }

        refreshSystemState()
    }

    fun onContinuousListeningChanged(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                continuousListeningEnabled = enabled,
                phase = if (enabled && !current.isListening) AssistantPhase.LISTENING else current.phase,
                statusHeadline = when {
                    enabled && current.isListening -> "Listening"
                    enabled -> "Standby"
                    else -> current.statusHeadline
                },
                statusBody = when {
                    enabled && current.isListening -> "Say \"Hey Aira\" or speak a command."
                    enabled -> "Continuous mode is armed while this screen stays open."
                    else -> current.statusBody
                },
            )
        }
    }

    fun onListeningStateChanged(isListening: Boolean) {
        _uiState.update { current ->
            val nextPhase = when {
                isListening -> AssistantPhase.LISTENING
                current.phase == AssistantPhase.LISTENING && current.continuousListeningEnabled -> AssistantPhase.LISTENING
                current.phase == AssistantPhase.LISTENING -> AssistantPhase.IDLE
                else -> current.phase
            }
            current.copy(
                isListening = isListening,
                phase = nextPhase,
                statusHeadline = when {
                    isListening -> "Listening"
                    current.continuousListeningEnabled -> "Standby"
                    current.phase == AssistantPhase.LISTENING -> "Ready"
                    else -> current.statusHeadline
                },
                statusBody = when {
                    isListening -> "Speak your command."
                    current.continuousListeningEnabled -> "Waiting for a wake phrase."
                    current.phase == AssistantPhase.LISTENING -> "Tap the mic to start another command."
                    else -> current.statusBody
                },
            )
        }
    }

    fun onVoiceHint(message: String) {
        _uiState.update { current ->
            current.copy(
                statusHeadline = if (current.continuousListeningEnabled) "Standby" else current.statusHeadline,
                statusBody = message,
            )
        }
    }

    fun onVoiceError(message: String) {
        _uiState.update { current ->
            current.copy(
                phase = AssistantPhase.ERROR,
                isListening = false,
                statusHeadline = "Voice input failed",
                statusBody = message,
            )
        }
    }

    fun onCommandCaptured(
        spokenText: String,
        source: CommandSource,
    ) {
        val currentState = _uiState.value
        if (currentState.phase == AssistantPhase.EXECUTING ||
            currentState.phase == AssistantPhase.INTERPRETING ||
            currentState.phase == AssistantPhase.AWAITING_CONFIRMATION
        ) {
            _uiState.update { current ->
                current.copy(
                    statusHeadline = "Busy",
                    statusBody = "Finish the current task before starting another one.",
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    phase = AssistantPhase.INTERPRETING,
                    statusHeadline = "Interpreting",
                    statusBody = "Turning your request into a device action.",
                    lastTranscript = spokenText,
                )
            }

            try {
                val interpretedIntent = repository.interpretCommand(
                    spokenText = spokenText,
                    localeTag = Locale.getDefault().toLanguageTag(),
                )

                val validation = validator.validate(interpretedIntent)
                val resolvedIntent = interpretedIntent.copy(
                    requiresConfirmation = interpretedIntent.requiresConfirmation || validation.requiresConfirmation,
                )

                val commandId = repository.insertCommand(
                    spokenText = spokenText,
                    source = source,
                    intent = resolvedIntent,
                    status = "INTERPRETED",
                    resultSummary = interpretedIntent.reason,
                )

                if (!validation.allowed) {
                    repository.updateCommandStatus(
                        commandId = commandId,
                        status = "BLOCKED",
                        resultSummary = validation.message,
                    )
                    _uiState.update { current ->
                        current.copy(
                            phase = AssistantPhase.ERROR,
                            statusHeadline = "Command blocked",
                            statusBody = validation.message,
                        )
                    }
                    return@launch
                }

                if (resolvedIntent.requiresConfirmation) {
                    repository.updateCommandStatus(
                        commandId = commandId,
                        status = "PENDING_CONFIRMATION",
                        resultSummary = "Waiting for user confirmation.",
                    )
                    _uiState.update { current ->
                        current.copy(
                            phase = AssistantPhase.AWAITING_CONFIRMATION,
                            statusHeadline = "Awaiting confirmation",
                            statusBody = resolvedIntent.displayTitle(),
                            pendingConfirmation = PendingCommandConfirmation(
                                commandId = commandId,
                                spokenText = spokenText,
                                source = source,
                                intent = resolvedIntent,
                            ),
                        )
                    }
                } else {
                    executeResolvedIntent(
                        commandId = commandId,
                        intent = resolvedIntent,
                    )
                }
            } catch (error: Exception) {
                _uiState.update { current ->
                    current.copy(
                        phase = AssistantPhase.ERROR,
                        statusHeadline = "Backend unavailable",
                        statusBody = error.message ?: "Aira could not reach the interpretation service.",
                    )
                }
            }
        }
    }

    fun confirmPendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        viewModelScope.launch {
            executeResolvedIntent(
                commandId = pending.commandId,
                intent = pending.intent,
            )
        }
    }

    fun rejectPendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        viewModelScope.launch {
            repository.updateCommandStatus(
                commandId = pending.commandId,
                status = "CANCELLED",
                resultSummary = "User rejected confirmation request.",
            )
            _uiState.update { current ->
                current.copy(
                    phase = AssistantPhase.IDLE,
                    statusHeadline = "Cancelled",
                    statusBody = "The action was not executed.",
                    pendingConfirmation = null,
                )
            }
        }
    }

    fun runShortcut(utterance: String) {
        onCommandCaptured(
            spokenText = utterance,
            source = CommandSource.SHORTCUT,
        )
    }

    fun refreshSystemState() {
        val appContext = getApplication<Application>().applicationContext
        val powerManager = appContext.getSystemService(PowerManager::class.java)
        _uiState.update { current ->
            current.copy(
                overlayEnabled = Settings.canDrawOverlays(appContext),
                batteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) == true,
            )
        }
    }

    private suspend fun executeResolvedIntent(
        commandId: Long,
        intent: AssistantIntent,
    ) {
        _uiState.update { current ->
            current.copy(
                phase = AssistantPhase.EXECUTING,
                statusHeadline = "Executing",
                statusBody = intent.displayTitle(),
                pendingConfirmation = null,
            )
        }
        repository.updateCommandStatus(
            commandId = commandId,
            status = "EXECUTING",
            resultSummary = "Action execution started.",
        )

        val result = actionExecutor.execute(intent)
        repository.updateCommandStatus(
            commandId = commandId,
            status = if (result.success) "COMPLETED" else "FAILED",
            resultSummary = result.message,
        )

        _uiState.update { current ->
            current.copy(
                phase = if (result.success) AssistantPhase.COMPLETED else AssistantPhase.ERROR,
                statusHeadline = if (result.success) "Done" else "Action failed",
                statusBody = result.message,
                pendingConfirmation = null,
            )
        }
        refreshSystemState()
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(application) as T
        }
    }
}

