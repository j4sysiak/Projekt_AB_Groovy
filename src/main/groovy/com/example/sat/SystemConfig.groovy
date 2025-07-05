package com.example.sat

import groovy.transform.Canonical

@Canonical
class SystemConfig {
    final String satelliteHost
    final int satellitePort
    final String authServiceUrl

    // Nowe pola
    String telemetryEndpoint
    String commandHost
    int commandPort
    String apiKey // Przenosimy klucz API do centralnej konfiguracji


//    SystemConfig (String satelliteHost, int satellitePort, String authServiceUrl,
//                  String telemetryEndpoint, String commandHost, int commandPort, String apiKey) {
//        this.satelliteHost = satelliteHost
//        this.satellitePort = satellitePort
//        this.authServiceUrl = authServiceUrl
//        this.telemetryEndpoint = telemetryEndpoint
//        this.commandHost = commandHost
//        this.commandPort = commandPort
//        this.apiKey = apiKey
//    }
}