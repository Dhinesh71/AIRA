package com.aira.app.voice

import android.os.SystemClock
import com.aira.app.data.model.CommandSource

class WakeWordDetector(
    private val wakePhrase: String = "hey aira",
    private val armWindowMillis: Long = 6_000L,
) {
    private var armedUntilMillis: Long = 0L

    fun process(
        transcript: String,
        manualTrigger: Boolean,
    ): WakeWordOutcome {
        val sanitized = transcript.trim()
        if (sanitized.isBlank()) return WakeWordOutcome.Ignored

        if (manualTrigger) {
            clear()
            return WakeWordOutcome.Actionable(
                command = sanitized,
                source = CommandSource.MANUAL,
            )
        }

        val normalized = normalized(sanitized)
        if (normalized.contains(wakePhrase)) {
            val wakeIndex = sanitized.indexOf(wakePhrase, ignoreCase = true)
            val remainder = if (wakeIndex >= 0) {
                sanitized.substring(wakeIndex + wakePhrase.length)
                    .trim(' ', ',', '.', '!', '?')
            } else {
                ""
            }
            return if (remainder.isNotBlank()) {
                clear()
                WakeWordOutcome.Actionable(
                    command = remainder,
                    source = CommandSource.WAKE_WORD,
                )
            } else {
                armedUntilMillis = SystemClock.elapsedRealtime() + armWindowMillis
                WakeWordOutcome.Armed
            }
        }

        if (SystemClock.elapsedRealtime() <= armedUntilMillis) {
            clear()
            return WakeWordOutcome.Actionable(
                command = sanitized,
                source = CommandSource.WAKE_WORD,
            )
        }

        return WakeWordOutcome.Ignored
    }

    fun clear() {
        armedUntilMillis = 0L
    }

    private fun normalized(value: String): String =
        value.lowercase()
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
}

sealed interface WakeWordOutcome {
    data class Actionable(
        val command: String,
        val source: CommandSource,
    ) : WakeWordOutcome

    data object Armed : WakeWordOutcome
    data object Ignored : WakeWordOutcome
}
