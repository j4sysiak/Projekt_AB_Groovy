package com.example.auto

import groovy.transform.Canonical

// To jest konkretna implementacja, której używały testy "Before"
@Canonical
class AutoConnectionImpl implements AutoConnection {
    String host
    int port
    private boolean connected = false

    @Override
    void connect(String authToken) {
        if (authToken == null || !authToken.startsWith("TOKEN-")) {
            throw new IllegalStateException("Connection refused: Invalid auth token.")
        }
        println "Connecting to auto server at ${host}:${port} with a valid token..."
        this.connected = true
        println "Connected."
    }

    @Override
    boolean isConnected() {
        return connected
    }

    @Override
    String fetchData(String autoIdId) {
        if (!connected) {
            throw new IllegalStateException("Not connected to auto server!")
        }
        return "Raw data for auto ${autoIdId}"
    }

    @Override
    void disconnect() {
        println "Disconnecting from ${host}:${port}..."
        this.connected = false
    }
}