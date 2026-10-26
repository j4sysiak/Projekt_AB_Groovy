package com.example.auto.exceptions

// Definiujemy nasz własny, dedykowany typ wyjątku dla błędów walidacji
class ValidationException extends IllegalArgumentException {
    ValidationException(String message) {
        super(message)
    }
}
