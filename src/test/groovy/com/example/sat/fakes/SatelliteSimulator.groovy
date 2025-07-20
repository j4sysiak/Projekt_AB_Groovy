package com.example.sat.fakes

import com.example.sat.CommandModule
import com.example.sat.SatelliteConnection
import com.example.sat.dto.PhotoData
import com.example.sat.dto.MissionPlan

import java.util.Locale // <-- DODAJ TEN IMPORT

// Ten symulator udaje zarówno moduł komend, jak i połączenie z satelitą.
class SatelliteSimulator implements SatelliteConnection, CommandModule {

    // STAN: Pamięć na otrzymane plany misji
    Map<String, MissionPlan> storedMissionPlans = [:]

    // Dodajmy flagę, aby móc kontrolować, kiedy fetchData ma zawieść.
    boolean forceFetchDataToFail = false

    // DYNAMICZNY STAN WEWNĘTRZNY ===
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
    // STAN: Kolejka zdjęć do wysłania
    List<PhotoData> photoQueue = []

    SatelliteSimulator(){
        println ("konstruktor niemy of SatelliteSimulator")
    }

    // Do przyjmowania i zapisywania planu misji
    void storeMissionPlan(MissionPlan plan) {
        if (!powerOn || batteryLevel < 1.0) {
            throw new IllegalStateException("SIMULATOR: Insufficient power to store mission plan.")
        }
        println "SIMULATOR: Storing mission plan ${plan.missionId}..."
        storedMissionPlans[plan.missionId] = plan
        batteryLevel -= 0.1 // Małe zużycie energii na zapis
    }

    // Symulacja upływu czasu i logiki wewnętrznej
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
        if (forceFetchDataToFail) {
            throw new RuntimeException("Forced fetch data failure for testing")
        }

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
    String sendCommand(String command, Map params = [:]) {  // Dodajemy opcjonalne parametry
        if (!connected) throw new IllegalStateException("SIMULATOR: Not connected.")

        println "SIMULATOR: Received command '${command}'"
        commandHistory.add(command)
        batteryLevel -= POWER_CONSUMPTION_PER_COMMAND

        if (command.contains("THRUSTER") && thrusterHealth == "FAILED") {
            return "NACK: Command '${command}' failed. Thrusters are in FAILED state."
        }
        if (batteryLevel < 10.0) {
            return "NACK: Command '${command}' refused. Critically low power: ${batteryLevel}%."
        }

        // KOMENDA: Robienie zdjęć
        if (command == "TAKE_PHOTOS") {
            int count = params.get("numberOfPhotos", 1)
            String quality = params.get("quality", "HIGH")

            if (quality != "HIGH") {
                return "NACK: Only HIGH quality photos are supported by this firmware."
            }

            count.times {
                println "SIMULATOR: Taking a ${quality} quality photo..."
                photoQueue.add(new PhotoData(quality: quality, content: "Simulated high-quality image data..."))
            }
            return "ACK: ${count} photos taken and queued for downlink."
        }

        switch (command) {
            case "SET_CAMERA_MODE:ACTIVE_IMAGING":
                this.cameraMode = "ACTIVE_IMAGING"
                return "ACK: Camera set to ACTIVE_IMAGING"
            case "SET_CAMERA_MODE:STANDBY":
                this.cameraMode = "STANDBY"
                return "ACK: Camera set to STANDBY"
            case "CHECK_POWER_STATUS":
                // KLUCZOWA ZMIANA: Używamy Locale.US, aby ZAWSZE generować kropkę
                return String.format(Locale.US, "STATUS: Battery: %.2f%%, Panels: %s", batteryLevel, solarPanelStatus)
            case "CHECK_SYSTEM_HEALTH":
                return "STATUS: Thrusters: ${thrusterHealth}"
            default:
                return "NACK: Unknown command"
        }
    }

    //METODA: Wysyłanie danych na ziemię
    List<PhotoData> downlinkQueuedPhotos() {
        if (photoQueue.isEmpty()) {
            println "SIMULATOR: No photos in queue to downlink."
            return []
        }
        println "SIMULATOR: Downlinking ${photoQueue.size()} photos..."
        def photosToSend = new ArrayList<>(photoQueue)
        photoQueue.clear() // Opróżniamy kolejkę po wysłaniu
        return photosToSend
    }

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
    void resetState() {
        this.batteryLevel = 95.0
        this.cameraMode = "STANDBY"
        this.connected = false
        this.commandHistory = []
        this.photoQueue = []
        this.thrusterHealth = "NOMINAL"
        this.powerOn = true
        println "SIMULATOR: State has been reset."
    }
}