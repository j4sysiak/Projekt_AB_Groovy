package com.example.auto

import com.example.auto.exceptions.SecretRetrievalException

// Interfejs do naszego magazynu sekretów
interface SecretVault {
    // Pobiera sekret na podstawie jego unikalnej nazwy/ścieżki
    String getSecret(String secretName) throws SecretRetrievalException
}

