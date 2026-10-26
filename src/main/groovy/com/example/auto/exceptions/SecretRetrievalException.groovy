package com.example.auto.exceptions

// Dedykowany wyjątek dla problemów z pobraniem sekretu
class SecretRetrievalException extends RuntimeException {
    SecretRetrievalException(String message) {
        super(message)
    }
}