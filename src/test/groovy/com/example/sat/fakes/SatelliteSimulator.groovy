package com.example.sat.fakes

import com.example.sat.CommandModule
import com.example.sat.SatelliteConnection

// Ten symulator udaje zarówno moduł komend, jak i połączenie z satelitą.
class SatelliteSimulator implements SatelliteConnection, CommandModule {

    // === NOWY, DYNAMICZNY STAN WEWNĘTRZNY ===
    double batteryLevel = 95.0 // w procentach
    String solarPanelStatus = "CHARGING" // CHARGING (dzień) lub IDLE (noc)
    String thrusterHealth = "NOMINAL" // NOMINAL, DEGRADED, FAILED
    private static final double POWER_CONSUMPTION_PER_COMMAND = 0.5 // % baterii
    private static final double POWER_CONSUMPTION_TAKE_PHOTO = 5.0 // % baterii

    // === Wewnętrzny Stan Symulatora ===
    boolean powerOn = true
    boolean connected = false
    String cameraMode = "STANDBY"
    String lastReceivedAuthToken
    int commandsReceived = 0
    List<String> commandHistory = []

    SatelliteSimulator(){
        println ("konstruktor niemy of SatelliteSimulator")
    }

    // === NOWA METODA: Symulacja upływu czasu i logiki wewnętrznej ===
    // Ta metoda pozwala nam dynamicznie zmieniać stan symulatora w teście.
    void simulateTimePassage(boolean isDaylight) {
        if (isDaylight) {
            solarPanelStatus = "CHARGING"
            batteryLevel = Math.min(100.0, batteryLevel + 10.0) // Ładujemy baterię
            println "SIMULATOR: Entering daylight. Battery charging. Level: ${batteryLevel}%"
        } else {
            solarPanelStatus = "IDLE"
            batteryLevel -= 2.0 // Podstawowe zużycie energii w nocy
            println "SIMULATOR: Entering darkness. Battery discharging. Level: ${batteryLevel}%"
        }
    }

    // === Implementacja interfejsu SatelliteConnection ===
    // === Zmodyfikowane metody, które zależą od nowego stanu ===
    @Override
    String fetchData(String satelliteId) {
        if (!connected) throw new IllegalStateException("SIMULATOR: Not connected.")
        if (batteryLevel < POWER_CONSUMPTION_TAKE_PHOTO + 5.0) {
            throw new RuntimeException("SIMULATOR: Insufficient power to take and fetch photo. Battery at ${batteryLevel}%.")
        }
        if (cameraMode != "ACTIVE_IMAGING") {
            throw new RuntimeException("SIMULATOR: Cannot fetch data, camera is in ${cameraMode} mode.")
        }

        println "SIMULATOR: Fetching simulated meteorological data..."
        batteryLevel -= POWER_CONSUMPTION_TAKE_PHOTO
        return "<SimulatedImageData>${satelliteId}-${System.currentTimeMillis()}</SimulatedImageData>"
    }

    // === Implementacja interfejsu CommandModule ===
    @Override
    String sendCommand(String command) {
        if (!connected) throw new IllegalStateException("SIMULATOR: Not connected, cannot send command.")

        println "SIMULATOR: Received command '${command}'"
        commandHistory.add(command)

        // Sprawdzanie stanu przed wykonaniem komendy
        if (command.contains("THRUSTER") && thrusterHealth == "FAILED") {
            return "NACK: Command '${command}' failed. Thrusters are in FAILED state."
        }
        if (batteryLevel < 10.0) {
            return "NACK: Command '${command}' refused. Critically low power: ${batteryLevel}%."
        }

        // Logika symulatora reagująca na komendy
        switch (command) {
            case "SET_CAMERA_MODE:ACTIVE_IMAGING":
                this.cameraMode = "ACTIVE_IMAGING"
                return "ACK: Camera set to ACTIVE_IMAGING"
            case "SET_CAMERA_MODE:STANDBY":
                this.cameraMode = "STANDBY"
                return "ACK: Camera set to STANDBY"
                // NOWE KOMENDY do sprawdzania stanu
            case "CHECK_POWER_STATUS":
                return "STATUS: Battery: ${String.format('%.2f', batteryLevel)}%, Panels: ${solarPanelStatus}"
            case "CHECK_SYSTEM_HEALTH":
                return "STATUS: Thrusters: ${thrusterHealth}"
            default:
                return "NACK: Unknown command"
        }
    }

    // ... reszta metod (connect, isConnected, disconnect) bez zmian ...
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
    void disconnect() {
        println "SIMULATOR: Disconnecting."
        this.connected = false
    }
}