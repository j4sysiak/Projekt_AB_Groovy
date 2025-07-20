package com.example.sat

// Interfejs dla modułu komend
interface CommandModule {
    String sendCommand(String command, Map params)
}