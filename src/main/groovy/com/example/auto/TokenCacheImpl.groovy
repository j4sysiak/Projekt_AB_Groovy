package com.example.auto

class TokenCacheImpl implements TokenCache {
    // Zależność od serwisu, który jest "opakowywany"
    private final AuthService authService

    // NOWA ZALEŻNOŚĆ
    private final SecretVault secretVault
    // Przechowujemy nazwę sekretu, a nie sam sekret
    private final String apiKeySecretName
    //private final String apiKey

    // Prywatny stan cache
    private String cachedToken
    private long lastFetchTimeMillis = 0

    // Możemy skonfigurować, jak długo token jest ważny w cache
    private final long cacheDurationMillis

    // Aktualizujemy konstruktor
    TokenCacheImpl(AuthService authService, SecretVault secretVault, String apiKeySecretName, long cacheDurationMillis = 60000L) {
        this.authService = authService
        this.secretVault = secretVault
        this.apiKeySecretName = apiKeySecretName
        this.cacheDurationMillis = cacheDurationMillis
    }

    @Override
    String getToken() {
        long currentTime = System.currentTimeMillis()
        // Sprawdź, czy cache jest pusty LUB czy token wygasł
        if (cachedToken == null || (currentTime - lastFetchTimeMillis) > cacheDurationMillis) {
            println "CACHE MISS: Fetching new token from AuthService..."
            // ZMIANA: Najpierw pobierz sekret, potem użyj go do autoryzacji
            String apiKey = secretVault.getSecret(apiKeySecretName)
            cachedToken = authService.getAuthToken(apiKey)
            lastFetchTimeMillis = currentTime
        } else {
            println "CACHE HIT: Returning cached token."
        }
        return cachedToken
    }
}