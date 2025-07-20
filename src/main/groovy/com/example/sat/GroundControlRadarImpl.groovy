package com.example.sat

import com.example.sat.domain.Coordinates

class GroundControlRadarImpl implements IGroundControlRadar {
    // W prawdziwym świecie to byłaby skomplikowana komunikacja z anteną radarową.
    // Tutaj symulujemy, że radar ma "prawdziwą" wiedzę o pozycji satelity.
    // Możemy tu zasymulować np. drobne zakłócenia atmosferyczne.

    private final IOrbitalMechanics truePositionProvider // Radar ma dostęp do "prawdy"

    GroundControlRadarImpl(IOrbitalMechanics truePositionProvider) {
        this.truePositionProvider = truePositionProvider
    }

    @Override
    Coordinates pingSatellite(String satelliteId) {
        println "RADAR: Pinging satellite ${satelliteId}..."
        Coordinates truePosition = truePositionProvider.getCurrentPosition()

        // Symulujemy drobny błąd pomiaru
        double measuredLat = truePosition.latitude + (Math.random() - 0.5) * 0.1
        double measuredLon = truePosition.longitude + (Math.random() - 0.5) * 0.1

        println "RADAR: Ping successful. Measured position: Lat ${String.format('%.4f', measuredLat)}, Lon ${String.format('%.4f', measuredLon)}"
        return new Coordinates(latitude: measuredLat, longitude: measuredLon)
    }
}