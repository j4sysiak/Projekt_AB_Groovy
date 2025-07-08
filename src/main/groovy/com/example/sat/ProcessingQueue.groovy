package com.example.sat

// Prosta symulacja kolejki komunikatów
interface ProcessingQueue {
    void publish(String satelliteId)
    String consume() // Zwraca ID satelity lub null, jeśli kolejka jest pusta
}