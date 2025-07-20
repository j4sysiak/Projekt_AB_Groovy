package com.example.sat

import com.example.sat.domain.Coordinates

class GroundStationPassPredictorImpl implements IPassPredictor {
    private final IOrbitalMechanics orbitalMechanics
    private final Coordinates groundStationLocation
    private final double requiredMinLatitude // Jak "wysoko" musi być satelita, żebyśmy go widzieli

    GroundStationPassPredictorImpl(IOrbitalMechanics orbitalMechanics,
                                   Coordinates groundStationLocation,
                                   double requiredMinLatitude = 10.0) {
        this.orbitalMechanics = orbitalMechanics
        this.groundStationLocation = groundStationLocation
        this.requiredMinLatitude = requiredMinLatitude
    }

    @Override
    boolean isCommunicationWindowOpen() {
        Coordinates satellitePosition = orbitalMechanics.getCurrentPosition()

        // Prosta logika symulacji: okno jest otwarte, jeśli satelita jest
        // na północnej półkuli (dodatnia szerokość geograficzna) i powyżej pewnego progu.
        boolean isOpen = satellitePosition.latitude > requiredMinLatitude

        println "PASS PREDICTOR: Satellite at lat ${String.format('%.2f', satellitePosition.latitude)}. Required > ${requiredMinLatitude}. Window open: ${isOpen}"
        return isOpen
    }
}