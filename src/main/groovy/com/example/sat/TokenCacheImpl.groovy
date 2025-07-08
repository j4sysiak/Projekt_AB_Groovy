package com.example.sat

import groovy.transform.Canonical

@Canonical
class TokenCacheImpl implements TokenCache {
    AuthService authService // Zależność od serwisu autoryzacji
    String apiKey

    private String cachedToken
    private long lastFetchTime = 0
    private static final long CACHE_DURATION_MS = 60000 // Cache ważny 1 minutę

    @Override
    String getToken() {
        long currentTime = System.currentTimeMillis()
        if (cachedToken == null || (currentTime - lastFetchTime) > CACHE_DURATION_MS) {
            println "CACHE MISS: Fetching new token from AuthService..."
            cachedToken = authService.getAuthToken(apiKey)
            lastFetchTime = currentTime
        } else {
            println "CACHE HIT: Returning cached token."
        }
        return cachedToken
    }
}