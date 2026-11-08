package com.example.data.model

data class GameApp(
    val packageName: String,
    val appName: String,
    val iconBase64: String = "",
    val isPinned: Boolean = false,
    val isEmulator: Boolean = false,
    val category: String = "App"
)
