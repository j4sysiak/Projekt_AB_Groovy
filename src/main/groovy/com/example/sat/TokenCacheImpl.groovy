package com.example.sat

class TokenCacheImpl implements TokenCache {
    // Zależność od serwisu, który jest "opakowywany"
    private final AuthService authService
    private final String apiKey

    // Prywatny stan cache
    private String cachedToken
    private long lastFetchTimeMillis = 0

    // Możemy skonfigurować, jak długo token jest ważny w cache
    private final long cacheDurationMillis

    TokenCacheImpl(AuthService authService, String apiKey, long cacheDurationMillis = 60000L) { // Domyślnie 1 minuta
        this.authService = authService
        this.apiKey = apiKey
        this.cacheDurationMillis = cacheDurationMillis
    }

    @Override
    String getToken() {
        long currentTime = System.currentTimeMillis()
        // Sprawdź, czy cache jest pusty LUB czy token wygasł
        if (cachedToken == null || (currentTime - lastFetchTimeMillis) > cacheDurationMillis) {
            println "CACHE MISS: Fetching new token from AuthService..."
            cachedToken = authService.getAuthToken(apiKey)
            lastFetchTimeMillis = currentTime
        } else {
            println "CACHE HIT: Returning cached token."
        }
        return cachedToken
    }
}