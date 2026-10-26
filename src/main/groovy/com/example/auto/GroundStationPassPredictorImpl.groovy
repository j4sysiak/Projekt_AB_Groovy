package com.example.auto

import com.example.auto.domain.Coordinates

class GroundStationPassPredictorImpl implements IPassPredictor {
    private final IOrbitalMechanics orbitalMechanics
    private final Coordinates groundStationLocation
    private final double requiredMinLatitude // Jak "wysoko" musi być, żebyśmy go widzieli

    GroundStationPassPredictorImpl(IOrbitalMechanics orbitalMechanics,
                                   Coordinates groundStationLocation,
                                   double requiredMinLatitude = 10.0) {
        this.orbitalMechanics = orbitalMechanics
        this.groundStationLocation = groundStationLocation
        this.requiredMinLatitude = requiredMinLatitude
    }

    @Override
    boolean isCommunicationWindowOpen() {
        Coordinates autoPosition = orbitalMechanics.getCurrentPosition()
        boolean isOpen = autoPosition.latitude > requiredMinLatitude

        println "PASS PREDICTOR: Auto at lat ${String.format('%.2f', autoPosition.latitude)}. Required > ${requiredMinLatitude}. Window open: ${isOpen}"
        return isOpen
    }
}