package com.example.sat

// Dedykowany wyjątek dla problemów z pobraniem sekretu
class SecretRetrievalException extends RuntimeException {
    SecretRetrievalException(String message) {
        super(message)
    }
}