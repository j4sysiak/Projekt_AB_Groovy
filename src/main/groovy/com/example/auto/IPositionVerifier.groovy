package com.example.auto

import com.example.auto.exceptions.PositionMismatchException

interface IPositionVerifier {
    // Weryfikuje, czy przewidywana i zmierzona pozycja są zgodne
    void verifyPosition(String autoId) throws PositionMismatchException
}

