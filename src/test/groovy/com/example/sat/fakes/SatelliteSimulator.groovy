package com.example.sat.fakes

import com.example.sat.CommandModule
import com.example.sat.SatelliteConnection
import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoData
import com.example.sat.dto.PhotoTaskStatus

// Ten symulator udaje zarówno moduł komend, jak i połączenie z satelitą.
class SatelliteSimulator implements SatelliteConnection, CommandModule {

    // === STAN WEWNĘTRZNY ===
    double batteryLevel = 95.0
    String solarPanelStatus = "CHARGING"
    String thrusterHealth = "NOMINAL"
    boolean powerOn = true
    boolean connected = false
    String cameraMode = "STANDBY"
    List<String> commandHistory = []

    // === NOWA, SPÓJNA LOGIKA ZARZĄDZANIA MISJAMI ===
    PhotoCollectionManifest activeManifest
    Map<String, PhotoData> photoStorage = [:]
    final int downlinkBatchSize = 40


    void storeMissionPlan(PhotoCollectionManifest manifest) {
        println "SIMULATOR: Storing photo collection manifest ${manifest.missionId}..."

        // Ustawiamy wszystkie zadania na status PENDING w momencie zapisywania manifestu
        manifest.tasks.each { task ->
            task.status = PhotoTaskStatus.PENDING
        }
        this.activeManifest = manifest
    }



    // Ta metoda TYLKO robi zdjęcia na podstawie zapisanego planu.
    void simulatePhotoTakingProcess() {
        if (activeManifest) {
            println "SIMULATOR: Starting photo taking process..."
            activeManifest.tasks.each { task ->
                if (task.status == PhotoTaskStatus.PENDING && batteryLevel > 0.2) {
                    photoStorage[task.taskId] = new PhotoData(
                            photoId: task.taskId,
                            quality: "HIGH",
                            content: "Data for ${task.taskId}"
                    )
                    task.status = PhotoTaskStatus.TAKEN_AND_QUEUED
                }
            }
            println "PHOTO STORAGE after shooting: ${photoStorage.keySet()}"
            println "SIMULATOR: Photo taking process finished."
        }
    }

    // Metoda do wysyłania danych w paczkach
    List<PhotoData> downlinkQueuedPhotos() {
        if (!activeManifest) return []

        def queuedTasks = activeManifest.tasks
                .findAll { it.status == PhotoTaskStatus.TAKEN_AND_QUEUED }
        if (queuedTasks.isEmpty()) return []

        def tasksToDownlink = queuedTasks.take(downlinkBatchSize)
        println "SIMULATOR: Downlinking a batch of ${tasksToDownlink.size()} photos..."

        def photosToSend = tasksToDownlink.collect { task ->
            task.status = PhotoTaskStatus.DOWNLINKED
            photoStorage[task.taskId]
        }
        return photosToSend
    }

    @Override
    String sendCommand(String command, Map params = [:]) {
        if (!connected) throw new IllegalStateException("SIMULATOR: Not connected.")
        commandHistory.add(command)
        batteryLevel -= 0.5

        // Logika dla komendy TAKE_PHOTOS (używana w prostszych testach)
        if (command == "TAKE_PHOTOS") {
            int count = params.get("numberOfPhotos", 1) as int
            count.times {
                // Ta stara kolejka jest już niepotrzebna, ale zostawmy ją dla kompatybilności wstecznej
                // photoQueue.add(new PhotoData(quality: "HIGH", content: "..."))
            }
            return "ACK: ${count} photos taken."
        }

        switch (command) {
            case "CHECK_POWER_STATUS":
                return String.format(Locale.US, "STATUS: Battery: %.2f%%, Panels: %s", batteryLevel, solarPanelStatus)
            case "CHECK_SYSTEM_HEALTH":
                return "STATUS: Thrusters: ${thrusterHealth}"
            default:
                return "NACK: Unknown command"
        }
    }

    @Override
    void connect(String authToken) {
        if (!powerOn) throw new IllegalStateException("SIMULATOR: Cannot connect, power is off.")
        this.connected = true
        println "SIMULATOR: Connection established."
    }

    @Override
    void disconnect() {
        this.connected = false
        println "SIMULATOR: Disconnecting."
    }

    @Override
    boolean isConnected() { return connected }

    // === Implementacja interfejsu SatelliteConnection ===
    // === Zmodyfikowane metody, które zależą od nowego stanu ===
    @Override
    String fetchData(String satelliteId) {
        if (!connected) throw new IllegalStateException("SIMULATOR: Not connected.")
        if (forceFetchDataToFail) throw new RuntimeException("Forced fetch data failure for testing")
        if (batteryLevel < POWER_CONSUMPTION_TAKE_PHOTO + 5.0) throw new RuntimeException("SIMULATOR: Insufficient power...")
        if (cameraMode != "ACTIVE_IMAGING") throw new RuntimeException("SIMULATOR: Cannot fetch data, camera is in ${cameraMode} mode.")

        println "SIMULATOR: Fetching simulated meteorological data..."
        batteryLevel -= POWER_CONSUMPTION_TAKE_PHOTO
        return "<SimulatedImageData>${satelliteId}-${System.currentTimeMillis()}</SimulatedImageData>"
    }

    void resetState() {
        this.batteryLevel = 95.0
        this.cameraMode = "STANDBY"
        this.connected = false
        this.commandHistory = []
        this.activeManifest = null
        this.photoStorage.clear()
        this.thrusterHealth = "NOMINAL"
        this.powerOn = true
        println "SIMULATOR: State has been reset."
    }
}