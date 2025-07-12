package com.example.sat.fakes

import com.example.sat.CommandModule
import com.example.sat.SatelliteConnection

// Ten symulator udaje zarówno moduł komend, jak i połączenie z satelitą.
class SatelliteSimulator implements SatelliteConnection, CommandModule {

    // === Wewnętrzny Stan Symulatora ===
    boolean powerOn = true
    boolean connected = false
    String cameraMode = "STANDBY"
    String lastReceivedAuthToken
    int commandsReceived = 0
    List<String> commandHistory = []

    // === Implementacja interfejsu SatelliteConnection ===

    @Override
    void connect(String authToken) {
        if (!powerOn) throw new IllegalStateException("SIMULATOR: Cannot connect, power is off.")
        if (authToken == null) throw new IllegalStateException("SIMULATOR: Auth token is required.")
        println "SIMULATOR: Connection established with token ${authToken.take(10)}..."
        this.connected = true
        this.lastReceivedAuthToken = authToken
    }

    @Override
    boolean isConnected() {
        return connected
    }

    @Override
    String fetchData(String satelliteId) {
        if (!connected) throw new IllegalStateException("SIMULATOR: Not connected.")
        if (cameraMode != "ACTIVE_IMAGING") {
            // Symulujemy błąd: nie można pobrać danych, jeśli aparat nie robi zdjęcia
            throw new RuntimeException("SIMULATOR: Cannot fetch data, camera is in ${cameraMode} mode.")
        }
        println "SIMULATOR: Fetching simulated meteorological data for ${satelliteId}..."
        return "<SimulatedImageData>${satelliteId}-${System.currentTimeMillis()}</SimulatedImageData>"
    }

    @Override
    void disconnect() {
        println "SIMULATOR: Disconnecting."
        this.connected = false
    }

    // === Implementacja interfejsu CommandModule ===

    @Override
    String sendCommand(String command) {
        if (!connected) throw new IllegalStateException("SIMULATOR: Not connected, cannot send command.")

        println "SIMULATOR: Received command '${command}'"
        commandsReceived++
        commandHistory.add(command)

        // Logika symulatora reagująca na komendy
        switch (command) {
            case "SET_CAMERA_MODE:ACTIVE_IMAGING":
                this.cameraMode = "ACTIVE_IMAGING"
                return "ACK: Camera set to ACTIVE_IMAGING"
            case "SET_CAMERA_MODE:STANDBY":
                this.cameraMode = "STANDBY"
                return "ACK: Camera set to STANDBY"
            case "CHECK_STATUS":
                return "STATUS: OK, Power: ${powerOn}, Camera: ${cameraMode}"
            default:
                return "NACK: Unknown command"
        }
    }
}