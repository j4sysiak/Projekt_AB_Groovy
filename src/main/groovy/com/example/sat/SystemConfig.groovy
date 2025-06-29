package com.example.sat

// Przechowuje konfiguracj? dla ca?ego systemu
class SystemConfig {
    final String satelliteHost
    final int satellitePort
    final String authServiceUrl

    SystemConfig (String satelliteHost, int satellitePort, String authServiceUrl) {
        this.satelliteHost = satelliteHost
        this.satellitePort = satellitePort
        this.authServiceUrl = authServiceUrl
    }
}