package com.example.auto

// Interfejs dla modułu komend
interface CommandModule {
    String sendCommand(String command, Map params)
}