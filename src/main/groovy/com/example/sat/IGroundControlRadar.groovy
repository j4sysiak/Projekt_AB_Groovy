package com.example.sat

// Interfejs dla naszego radaru
interface IGroundControlRadar {
    // Pingujemy satelitę o danym ID i oczekujemy jego zmierzonych współrzędnych
    Coordinates pingSatellite(String satelliteId)
}