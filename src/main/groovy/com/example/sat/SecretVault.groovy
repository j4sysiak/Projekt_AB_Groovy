package com.example.sat

import com.example.sat.exceptions.SecretRetrievalException

// Interfejs do naszego magazynu sekretów
interface SecretVault {
    // Pobiera sekret na podstawie jego unikalnej nazwy/ścieżki
    String getSecret(String secretName) throws SecretRetrievalException
}

