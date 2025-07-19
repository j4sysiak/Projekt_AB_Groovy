package com.example.sat

// Prosty interfejs dla naszego walidatora
interface InputValidator {
    // Sprawdza, czy ID satelity jest poprawne.
    // Rzuca wyjątkiem, jeśli nie jest.
    void validateSatelliteId(String satelliteId) throws ValidationException
}