package com.example.sat

// Interfejs dla modułu komend
interface CommandModule {
    // Definiujemy metodę z opcjonalnym drugim argumentem
    String sendCommand(String command, Map params)
}