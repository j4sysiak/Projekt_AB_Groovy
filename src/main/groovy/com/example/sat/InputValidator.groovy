package com.example.sat

// Prosty interfejs dla naszego walidatora
interface InputValidator {
    // Sprawdza, czy ID satelity jest poprawne.
    // Rzuca wyjątkiem, jeśli nie jest.
    void validateSatelliteId(String satelliteId) throws ValidationException
}

// Definiujemy nasz własny, dedykowany typ wyjątku dla błędów walidacji
class ValidationException extends IllegalArgumentException {
    ValidationException(String message) {
        super(message)
    }
}