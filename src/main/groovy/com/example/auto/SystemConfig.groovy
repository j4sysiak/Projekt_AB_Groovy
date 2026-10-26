package com.example.auto

// Bez żadnych adnotacji. Czysta, prosta klasa.
class SystemConfig {
    final String autoHost
    final int autoPort
    final String authServiceUrl
    final String downloadEndpoint
    final String commandHost
    final int commandPort
    // ZMIANA: Usunęliśmy jawny klucz API
    final String apiKeySecretName // Dodajemy identyfikator sekretu
    //final String apiKey

    // Aktualizujemy konstruktor
    SystemConfig(String autoHost, int autoPort, String authServiceUrl, String downloadEndpoint, String commandHost, int commandPort, String apiKeySecretName) {
        this.autoHost = autoHost
        this.autoPort = autoPort
        this.authServiceUrl = authServiceUrl
        this.downloadEndpoint = downloadEndpoint
        this.commandHost = commandHost
        this.commandPort = commandPort
        this.apiKeySecretName = apiKeySecretName
    }
}