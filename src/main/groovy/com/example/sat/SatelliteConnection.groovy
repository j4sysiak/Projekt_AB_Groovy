package com.example.sat

class SatelliteConnection {
    final String host
    final int port
    private boolean connected = false

    SatelliteConnection(String host, int port) {
        this.host = host
        this.port = port
    }

    // Metoda connect wymaga teraz tokena
    void connect(String authToken) {
        if (authToken == null || !authToken.startsWith("TOKEN-")) {
            throw new IllegalStateException("Connection refused: Invalid auth token.")
        }
        println "Connecting to satellite server at ${host}:${port} with a valid token..."
        Thread.sleep(50)
        this.connected = true
        println "Connected."
    }

    boolean isConnected() {
        return connected
    }

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