package com.example.auto

import com.example.auto.domain.Coordinates

class OrbitalMechanicsImpl implements IOrbitalMechanics {
    private final long orbitPeriodMinutes
    private final long epoch = System.currentTimeMillis() // Moment "startu" orbity

    OrbitalMechanicsImpl(long orbitPeriodMinutes = 90L) { // Domyślnie 90-minutowa orbita
        this.orbitPeriodMinutes = orbitPeriodMinutes
    }

    @Override
    Coordinates getCurrentPosition() {
        long millisSinceEpoch = System.currentTimeMillis() - epoch
        double orbitProgress = (millisSinceEpoch / (orbitPeriodMinutes * 60000.0)) % 1.0

        double latitude = 90 * Math.sin(2 * Math.PI * orbitProgress)
        double longitude = (orbitProgress * 360) % 360 - 180 // Prosta symulacja długości

        println "MECHANICS: Current position: Lat ${String.format('%.2f', latitude)}, Lon ${String.format('%.2f', longitude)}"
        return new Coordinates(latitude: latitude, longitude: longitude)
    }
}