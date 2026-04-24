package com.eddam.heysary

data class AutoProtocol(
    val id: String,
    val name: String,
    val defaultTime: String, // HH:mm format
    var currentTime: String,
    var isEnabled: Boolean = false,
    val description: String
)
