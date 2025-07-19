package com.example.sat

interface IPositionVerifier {
    // Weryfikuje, czy przewidywana i zmierzona pozycja są zgodne
    void verifyPosition(String satelliteId) throws PositionMismatchException
}

