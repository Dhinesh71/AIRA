package com.aira.app.data.model

data class AssistantIntent(
    val action: String,
    val summary: String,
    val appName: String,
    val packageName: String,
    val targetText: String,
    val inputText: String,
    val contact: String,
    val message: String,
    val direction: String,
    val navigationTarget: String,
    val taskName: String,
    val requiresConfirmation: Boolean,
    val sensitive: Boolean,
    val confidence: Double,
    val reason: String,
) {
    fun normalizedActionKey(): String = buildString {
        append(action)
        if (appName.isNotBlank()) {
            append(":")
            append(appName.lowercase())
        }
    }

    fun displayTitle(): String = summary.ifBlank {
        action.replace("_", " ").replaceFirstChar { char -> char.titlecase() }
    }
}

