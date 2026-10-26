package com.example.auto

import com.example.auto.domain.Coordinates

class PlatzControlRadioImp implements IPlatzControlRadio {
    private final IOrbitalMechanics truePositionProvider

    PlatzControlRadioImp(IOrbitalMechanics truePositionProvider) {
        this.truePositionProvider = truePositionProvider
    }

    @Override
    Coordinates pingAuto(String autoId) {
        println "Pinging auto ${autoId}..."
        Coordinates truePosition = truePositionProvider.getCurrentPosition()

        // Symulujemy drobny błąd pomiaru
        double measuredLat = truePosition.latitude + (Math.random() - 0.5) * 0.1
        double measuredLon = truePosition.longitude + (Math.random() - 0.5) * 0.1

        println "Ping successful. Measured position: Lat ${String.format('%.4f', measuredLat)}, Lon ${String.format('%.4f', measuredLon)}"
        return new Coordinates(latitude: measuredLat, longitude: measuredLon)
    }
}