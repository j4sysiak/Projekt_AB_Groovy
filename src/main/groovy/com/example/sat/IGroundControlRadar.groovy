package com.example.sat

import com.example.sat.domain.Coordinates

// Interfejs dla naszego radaru
interface IGroundControlRadar {
    // Pingujemy satelitę o danym ID i oczekujemy jego zmierzonych współrzędnych
    Coordinates pingSatellite(String satelliteId)
}