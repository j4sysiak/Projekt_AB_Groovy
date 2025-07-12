package com.example.sat

// Interfejs do naszego magazynu sekretów
interface SecretVault {
    // Pobiera sekret na podstawie jego unikalnej nazwy/ścieżki
    String getSecret(String secretName) throws SecretRetrievalException
}

// Dedykowany wyjątek dla problemów z pobraniem sekretu
class SecretRetrievalException extends RuntimeException {
    SecretRetrievalException(String message) {
        super(message)
    }
}