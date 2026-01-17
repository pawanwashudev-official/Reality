package com.neubofy.reality.data.model

data class ChatMessage(
    val message: String,
    val isUser: Boolean,
    var isAnimating: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
