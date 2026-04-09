package com.aira.app.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.aira.app.data.model.AssistantIntent
import com.aira.app.data.model.ExecutionResult
import kotlinx.coroutines.delay

class AiraActionExecutor(
    private val context: Context,
    private val validator: AutomationValidator,
    private val taskerBridge: TaskerBridge,
) {
    suspend fun execute(intent: AssistantIntent): ExecutionResult {
        val validation = validator.validate(intent)
        if (!validation.allowed) {
            return ExecutionResult(
                success = false,
                message = validation.message,
            )
        }

        return when (intent.action) {
            "open_app" -> openApp(intent)
            "navigate" -> withService(
                successMessage = "Navigation completed.",
                failureMessage = "Accessibility service could not navigate.",
            ) { service ->
                service.performNavigation(intent.navigationTarget)
            }

            "click_text" -> withService(
                successMessage = "Clicked ${intent.targetText}.",
                failureMessage = "Could not find a matching UI element.",
            ) { service ->
                service.clickNodeByText(intent.targetText)
            }

            "type_text" -> withService(
                successMessage = "Typed requested text.",
                failureMessage = "Could not find an editable field.",
            ) { service ->
                service.typeIntoFocusedField(intent.inputText, intent.targetText)
            }

            "scroll" -> withService(
                successMessage = "Scroll action completed.",
                failureMessage = "No scrollable container was available.",
            ) { service ->
                service.scroll(intent.direction)
            }

            "send_message" -> sendMessage(intent)
            "trigger_task" -> triggerTask(intent)
            else -> ExecutionResult(false, "That action is not available in the MVP.")
        }
    }

    private fun openApp(intent: AssistantIntent): ExecutionResult {
        val launchIntent = when {
            intent.packageName.isNotBlank() -> {
                context.packageManager.getLaunchIntentForPackage(intent.packageName)
            }

            intent.appName.isNotBlank() -> resolveLaunchIntent(intent.appName)
            else -> null
        }

        if (launchIntent == null) {
            return ExecutionResult(
                success = false,
                message = "Aira could not find ${intent.appName.ifBlank { "that app" }} on this device.",
            )
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return ExecutionResult(
            success = true,
            message = "Opening ${intent.appName.ifBlank { intent.packageName }}.",
        )
    }

    private suspend fun sendMessage(intent: AssistantIntent): ExecutionResult {
        val openResult = openApp(intent)
        if (!openResult.success) return openResult

        val service = AiraAccessibilityService.instance
            ?: return ExecutionResult(false, "Accessibility service is required for messaging automation.")

        delay(900)
        if (intent.contact.isNotBlank()) {
            service.clickNodeByText("Search")
            delay(350)
            service.typeIntoFocusedField(intent.contact, "search")
            delay(700)
            service.clickNodeByText(intent.contact)
            delay(700)
        }

        if (intent.message.isNotBlank()) {
            val typed = service.typeIntoFocusedField(intent.message, "message")
            delay(300)
            if (typed) {
                val sent = service.clickNodeByText("Send") ||
                    service.clickNodeByText("send") ||
                    service.clickNodeByText("Message")
                return if (sent) {
                    ExecutionResult(true, "Message flow completed.")
                } else {
                    ExecutionResult(true, "Message text was prepared, but the send button was not found.")
                }
            }
        }

        return ExecutionResult(
            success = false,
            message = "Aira opened the app, but could not complete the messaging flow automatically.",
        )
    }

    private fun triggerTask(intent: AssistantIntent): ExecutionResult {
        val triggered = taskerBridge.triggerTask(
            taskName = intent.taskName,
            summary = intent.summary,
        )
        return if (triggered) {
            ExecutionResult(true, "Tasker bridge broadcast sent for ${intent.taskName}.")
        } else {
            ExecutionResult(false, "Tasker bridge could not trigger that task.")
        }
    }

    private fun resolveLaunchIntent(appName: String): Intent? {
        val queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = context.packageManager.queryIntentActivities(queryIntent, PackageManager.MATCH_ALL)
        val bestMatch = matches.firstOrNull { match ->
            match.loadLabel(context.packageManager).toString().equals(appName, ignoreCase = true)
        } ?: matches.firstOrNull { match ->
            match.loadLabel(context.packageManager).toString().contains(appName, ignoreCase = true)
        }

        return bestMatch?.activityInfo?.packageName?.let {
            context.packageManager.getLaunchIntentForPackage(it)
        }
    }

    private fun withService(
        successMessage: String,
        failureMessage: String,
        block: (AiraAccessibilityService) -> Boolean,
    ): ExecutionResult {
        val service = AiraAccessibilityService.instance
            ?: return ExecutionResult(
                success = false,
                message = "Accessibility service is not enabled.",
            )

        val success = block(service)
        return ExecutionResult(
            success = success,
            message = if (success) successMessage else failureMessage,
        )
    }
}
