package com.example.sat

// Interfejs do prostej symulacji asynchronicznej kolejki komunikatów
interface ProcessingQueue {
    // Producent wysyła wiadomość na kolejkę
    void publishJob(String satelliteId)

    // Konsument pobiera wiadomość z kolejki. Zwraca null, jeśli jest pusta.
    String consumeJob()
}