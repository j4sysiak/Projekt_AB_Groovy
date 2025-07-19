package com.example.sat

// Interfejs do naszego magazynu sekretów
interface SecretVault {
    // Pobiera sekret na podstawie jego unikalnej nazwy/ścieżki
    String getSecret(String secretName) throws SecretRetrievalException
}

