package com.aira.app.automation

import com.aira.app.data.model.AssistantIntent

class AutomationValidator {
    fun validate(intent: AssistantIntent): ValidationResult {
        val action = intent.action.trim()
        if (action.isBlank() || action == "unknown") {
            return ValidationResult(
                allowed = false,
                requiresConfirmation = false,
                message = "Aira could not confidently understand that command.",
            )
        }

        return when (action) {
            "open_app" -> validateOpenApp(intent)
            "navigate" -> validateNavigate(intent)
            "click_text" -> validateTargetText(intent, "A UI label is required to click.")
            "type_text" -> validateTypeText(intent)
            "scroll" -> validateScroll(intent)
            "send_message" -> validateSendMessage(intent)
            "trigger_task" -> validateTriggerTask(intent)
            else -> ValidationResult(
                allowed = false,
                requiresConfirmation = false,
                message = "That action is not supported in this build.",
            )
        }
    }

    private fun validateOpenApp(intent: AssistantIntent): ValidationResult =
        if (intent.packageName.isBlank() && intent.appName.isBlank()) {
            ValidationResult(false, false, "Open app requires an app name or package name.")
        } else {
            ValidationResult(true, false, "Ready to open app.")
        }

    private fun validateNavigate(intent: AssistantIntent): ValidationResult =
        if (intent.navigationTarget.isBlank()) {
            ValidationResult(false, false, "Navigation target is missing.")
        } else {
            ValidationResult(true, false, "Ready to navigate.")
        }

    private fun validateTargetText(intent: AssistantIntent, message: String): ValidationResult =
        if (intent.targetText.isBlank()) {
            ValidationResult(false, false, message)
        } else {
            ValidationResult(true, true, "Confirmation recommended before interacting with the UI.")
        }

    private fun validateTypeText(intent: AssistantIntent): ValidationResult =
        if (intent.inputText.isBlank()) {
            ValidationResult(false, false, "There is no text to type.")
        } else {
            ValidationResult(true, true, "Typing text requires confirmation.")
        }

    private fun validateScroll(intent: AssistantIntent): ValidationResult =
        if (intent.direction.isBlank()) {
            ValidationResult(false, false, "Scroll direction is missing.")
        } else {
            ValidationResult(true, false, "Ready to scroll.")
        }

    private fun validateSendMessage(intent: AssistantIntent): ValidationResult =
        if (intent.contact.isBlank()) {
            ValidationResult(false, false, "A contact is required before sending a message.")
        } else {
            ValidationResult(true, true, "Messaging actions always require confirmation.")
        }

    private fun validateTriggerTask(intent: AssistantIntent): ValidationResult =
        if (intent.taskName.isBlank()) {
            ValidationResult(false, false, "Task name is missing.")
        } else {
            ValidationResult(true, false, "Ready to trigger automation task.")
        }
}

data class ValidationResult(
    val allowed: Boolean,
    val requiresConfirmation: Boolean,
    val message: String,
)

