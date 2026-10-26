package com.example.auto

import com.example.auto.domain.Coordinates
import com.example.auto.exceptions.PositionMismatchException

class PositionVerificationServiceImpl implements IPositionVerifier {
    private final IOrbitalMechanics orbitalModel
    private final IPlatzControlRadio radio
    private final double maxAllowedDelta // Maksymalna dopuszczalna różnica w stopniach

    PositionVerificationServiceImpl(IOrbitalMechanics orbitalModel, IPlatzControlRadio radio, double maxAllowedDelta = 0.5) {
        this.orbitalModel = orbitalModel
        this.radio = radio
        this.maxAllowedDelta = maxAllowedDelta
    }

    @Override
    void verifyPosition(String autoId) throws PositionMismatchException {
        println "POSITION VERIFIER: Starting position verification for ${autoId}..."

        // 1. Pobierz przewidywaną pozycję z modelu
        Coordinates predictedPosition = orbitalModel.getCurrentPosition()

        Coordinates measuredPosition = radio.pingAuto(autoId)

        // 3. Oblicz różnicę
        double delta = Math.sqrt(
                Math.pow(predictedPosition.latitude - measuredPosition.latitude, 2) +
                        Math.pow(predictedPosition.longitude - measuredPosition.longitude, 2)
        )
        println "POSITION VERIFIER: Predicted vs Measured delta is ${String.format('%.4f', delta)}. Max allowed: ${maxAllowedDelta}"

        // 4. Jeśli różnica jest zbyt duża, rzuć wyjątkiem
        if (delta > maxAllowedDelta) {
            throw new PositionMismatchException("Position mismatch for ${autoId}. Delta of ${delta} exceeds threshold of ${maxAllowedDelta}.")
        }

        println "POSITION VERIFIER: Position verified successfully."
    }
}