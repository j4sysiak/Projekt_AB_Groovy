package com.example.sat

import com.example.sat.domain.Coordinates
import com.example.sat.exceptions.PositionMismatchException

class PositionVerificationServiceImpl implements IPositionVerifier {
    private final IOrbitalMechanics orbitalModel
    private final IGroundControlRadar radar
    private final double maxAllowedDelta // Maksymalna dopuszczalna różnica w stopniach

    PositionVerificationServiceImpl(IOrbitalMechanics orbitalModel, IGroundControlRadar radar, double maxAllowedDelta = 0.5) {
        this.orbitalModel = orbitalModel
        this.radar = radar
        this.maxAllowedDelta = maxAllowedDelta
    }

    @Override
    void verifyPosition(String satelliteId) throws PositionMismatchException {
        println "POSITION VERIFIER: Starting position verification for ${satelliteId}..."

        // 1. Pobierz przewidywaną pozycję z modelu
        Coordinates predictedPosition = orbitalModel.getCurrentPosition()

        // 2. Pobierz zmierzoną pozycję z radaru
        Coordinates measuredPosition = radar.pingSatellite(satelliteId)

        // 3. Oblicz różnicę
        double delta = Math.sqrt(
                Math.pow(predictedPosition.latitude - measuredPosition.latitude, 2) +
                        Math.pow(predictedPosition.longitude - measuredPosition.longitude, 2)
        )
        println "POSITION VERIFIER: Predicted vs Measured delta is ${String.format('%.4f', delta)}. Max allowed: ${maxAllowedDelta}"

        // 4. Jeśli różnica jest zbyt duża, rzuć wyjątkiem
        if (delta > maxAllowedDelta) {
            throw new PositionMismatchException("Position mismatch for ${satelliteId}. Delta of ${delta} exceeds threshold of ${maxAllowedDelta}.")
        }

        println "POSITION VERIFIER: Position verified successfully."
    }
}