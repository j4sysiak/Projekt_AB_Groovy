package com.example.sat

import groovy.transform.Canonical

// To jest konkretna implementacja, której używały testy "Before"
@Canonical
class SatelliteConnectionImpl implements SatelliteConnection {
    String host
    int port
    private boolean connected = false

    @Override
    void connect(String authToken) {
        if (authToken == null || !authToken.startsWith("TOKEN-")) {
            throw new IllegalStateException("Connection refused: Invalid auth token.")
        }
        println "Connecting to satellite server at ${host}:${port} with a valid token..."
        this.connected = true
        println "Connected."
    }

    @Override
    boolean isConnected() {
        return connected
    }

    @Override
    String fetchData(String satelliteId) {
        if (!connected) {
            throw new IllegalStateException("Not connected to satellite server!")
        }
        return "Raw data for satellite ${satelliteId}"
    }

    @Override
    void disconnect() {
        println "Disconnecting from ${host}:${port}..."
        this.connected = false
    }
}