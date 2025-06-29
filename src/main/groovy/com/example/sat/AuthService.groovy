package com.example.sat

// Symuluje serwis autoryzacji
class AuthService {
    private final String authUrl

    AuthService(String authUrl) {
        this.authUrl = authUrl
    }

    // Symuluje uzyskanie tokena na podstawie klucza API
    String getAuthToken(String apiKey) {
        println "Authenticating with API key at ${authUrl}..."
        if (apiKey == "VALID-API-KEY") {
            return "TOKEN-FOR-${apiKey}"
        }
        throw new IllegalArgumentException("Invalid API Key provided")
    }
}