package com.example.auto

// Zmieniamy AuthService na interfejs. To jest kluczowa zmiana.
interface AuthService  {
    String getAuthToken(String apiKey)
}