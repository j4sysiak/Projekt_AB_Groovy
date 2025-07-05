package com.example.sat

class AuthServiceImpl implements AuthService {
    final String authUrl

    AuthServiceImpl(String authUrl) {
        this.authUrl = authUrl
    }

    @Override
    String getAuthToken(String apiKey) {
        println "Authenticating with API key at ${authUrl}..."
        if (apiKey == "ALPHA-VALID-API-KEY") {
            return "TOKEN-FOR-${apiKey}"
        }
        throw new IllegalArgumentException("Invalid API Key provided")
    }
}