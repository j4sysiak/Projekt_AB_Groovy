package com.example.sat

import com.example.sat.exceptions.ValidationException

class InputValidatorImpl implements InputValidator {
    private static final String ID_PREFIX = "SAT-"
    // ZMIANA: Minimalna długość to teraz 6 ("SAT-" plus co najmniej jeden znak)
    private static final int MIN_LENGTH = 6

    @Override
    void validateSatelliteId(String satelliteId) throws ValidationException {
        println "VALIDATOR: Validating ID: ${satelliteId}"
        if (satelliteId == null) {
            throw new ValidationException("Satellite ID cannot be null.")
        }
        if (!satelliteId.startsWith(ID_PREFIX)) {
            throw new ValidationException("Invalid format: Satellite ID must start with '${ID_PREFIX}'.")
        }
        // Teraz warunek będzie poprawny dla ID "SAT-1" (długość 5)
        if (satelliteId.length() < MIN_LENGTH) {
            throw new ValidationException("Invalid format: Satellite ID is too short.")
        }
        println "VALIDATOR: ID '${satelliteId}' is valid."
    }
}