package com.example.sat

// Symuluje połączenie z zasobem zewnętrznym (np. serwerem satelitarnym)
class SatelliteConnection {
    final String host
    final int port
    private boolean connected = false

    SatelliteConnection(String host, int port) {
        this.host = host
        this.port = port
    }

    void connect() {
        println "Connecting to satellite server at ${host}:${port}..."
        // Symulacja operacji sieciowej
        Thread.sleep(50)
        this.connected = true
        println "Connected."
    }

    boolean isConnected() {
        return connected
    }

    // Symuluje pobranie danych z satelity
    String fetchData(String satelliteId) {
        if (!connected) {
            throw new IllegalStateException("Not connected to satellite server!")
        }
        return "Raw data for satellite ${satelliteId}"
    }

    void disconnect() {
        println "Disconnecting from ${host}:${port}..."
        this.connected = false
    }
}