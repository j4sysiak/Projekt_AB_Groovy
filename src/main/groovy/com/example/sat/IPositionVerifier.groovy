package com.example.sat

import com.example.sat.exceptions.PositionMismatchException

interface IPositionVerifier {
    // Weryfikuje, czy przewidywana i zmierzona pozycja są zgodne
    void verifyPosition(String satelliteId) throws PositionMismatchException
}

