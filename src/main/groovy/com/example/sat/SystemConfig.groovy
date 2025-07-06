package com.example.sat

// Bez żadnych adnotacji. Czysta, prosta klasa.
class SystemConfig {
    final String satelliteHost
    final int satellitePort
    final String authServiceUrl
    final String telemetryEndpoint
    final String commandHost
    final int commandPort
    final String apiKey

    // Prosty, jawny, pozycyjny konstruktor w stylu Javy. Jest niezawodny.
    SystemConfig(String satelliteHost, int satellitePort, String authServiceUrl,
                 String telemetryEndpoint, String commandHost, int commandPort, String apiKey) {
        this.satelliteHost = satelliteHost
        this.satellitePort = satellitePort
        this.authServiceUrl = authServiceUrl
        this.telemetryEndpoint = telemetryEndpoint
        this.commandHost = commandHost
        this.commandPort = commandPort
        this.apiKey = apiKey
    }
}