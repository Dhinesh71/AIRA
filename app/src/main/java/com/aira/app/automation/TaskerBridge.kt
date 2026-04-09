package com.aira.app.automation

import android.content.Context
import android.content.Intent
import com.aira.app.BuildConfig

class TaskerBridge(
    private val context: Context,
) {
    fun triggerTask(
        taskName: String,
        summary: String,
    ): Boolean {
        if (taskName.isBlank()) return false

        val intent = Intent(BuildConfig.TASKER_BROADCAST_ACTION).apply {
            putExtra("task_name", taskName)
            putExtra("summary", summary)
            putExtra("triggered_at", System.currentTimeMillis())
        }
        context.sendBroadcast(intent)
        return true
    }
}

