package com.example.sat

// Definiujemy nasz własny, dedykowany typ wyjątku dla błędów walidacji
class ValidationException extends IllegalArgumentException {
    ValidationException(String message) {
        super(message)
    }
}
