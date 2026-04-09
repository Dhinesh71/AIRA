package com.aira.app.data.model

data class InterpretRequest(
    val text: String,
    val locale: String,
    val recentCommands: List<String>,
)

