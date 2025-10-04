package com.example.sat.fakes

import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoData
import com.example.sat.dto.PhotoTaskStatus
import com.example.sat.simulation.ISatelliteSimulator
import groovy.transform.Synchronized

// Importujemy TYLKO jeden, nadrzędny interfejs


// ZMIANA: Implementujemy tylko ISatelliteSimulator
class SatelliteSimulator implements ISatelliteSimulator {

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

    @Synchronized
    @Override
    void storeMissionPlan(PhotoCollectionManifest manifest) {
        println "SIMULATOR: Storing photo collection manifest ${manifest.missionId}..."

        // Ustawiamy wszystkie zadania na status PENDING w momencie zapisywania manifestu
        manifest.tasks.each { task ->
            task.status = PhotoTaskStatus.PENDING
        }
        this.activeManifest = manifest
    }

    // Ta metoda TYLKO robi zdjęcia na podstawie zapisanego planu.
    @Synchronized
    void simulatePhotoTakingProcess() {
        if (!activeManifest) {
            println "SIMULATOR: No active manifest. Photo taking process skipped."
            return
        }
        println "SIMULATOR: Starting photo taking process for mission ${activeManifest.missionId}..."
        activeManifest.tasks.each { task ->
            // Sprawdzamy, czy zadanie jest wciąż do wykonania i czy mamy na to energię
            if (task.status == PhotoTaskStatus.PENDING && batteryLevel > 0.2) {
                // Tworzymy NOWY, KOMPLETNY obiekt PhotoData
                def newPhoto = new PhotoData(
                    // Używamy ID zadania jako ID zdjęcia dla spójności
                    photoId: task.taskId,

                    // Używamy ID misji z aktywnego manifestu
                    missionId: this.activeManifest.missionId,

                    // Generujemy timestamp w momencie "zrobienia" zdjęcia
                    timestamp: System.currentTimeMillis(),

                    // Pozostałe dane
                    quality: 95, // Użyjmy liczby zamiast stringa "HIGH" dla łatwiejszego przetwarzania
                    content: "Raw data for ${task.taskId}"
                )

                // Zapisujemy gotowy obiekt w naszym "magazynie"
                photoStorage[task.taskId] = newPhoto

                // Aktualizujemy status zadania
                task.status = PhotoTaskStatus.TAKEN_AND_QUEUED

                println "SIMULATOR: Photo taken for task ${task.taskId}"
                }
            }
        println "PHOTO STORAGE after shooting: ${photoStorage.size()} photos stored."
        println "SIMULATOR: Photo taking process finished."
        println "DEBUG PHOTO PROCESS: Status pierwszego zadania: ${activeManifest?.tasks?.first()?.status}"
        }
/*
    @Synchronized
    @Override
    List<PhotoData> downlinkQueuedPhotos() {
        // Twoje logi debugowania tutaj...
        if (!activeManifest) { return [] }

        // Krok 1: Znajdź wszystkie zadania, które czekają w kolejce do wysłania.
        def allQueuedTasks = activeManifest.tasks.findAll {
            it.status == com.example.sat.dto.PhotoTaskStatus.TAKEN_AND_QUEUED
        }

        if (allQueuedTasks.isEmpty()) {
            return []
        }

        // Krok 2: Z tej kolejki, weź tylko jedną paczkę o rozmiarze `downlinkBatchSize`.
        def tasksForThisBatch = allQueuedTasks.take(downlinkBatchSize)

        // Krok 3: Dla tej paczki, zbierz odpowiadające im obiekty zdjęć.
        def photosToSend = tasksForThisBatch.collect { task ->
            photoStorage.get(task.taskId)
        }.findAll { it != null }

        // Krok 4: Zmień status TYLKO TYCH ZADAŃ, które są w tej paczce.
        tasksForThisBatch.each { task ->
            task.status = com.example.sat.dto.PhotoTaskStatus.DOWNLINKED
        }

        // Krok 5: Zwróć paczkę zdjęć.
        return photosToSend
    }*/



    // Metoda do wysyłania danych w paczkach
    // Downlink zdjęć z SAT (SateliteSimulatorSever) do SERW1 (IntermediateSerwerApp)
    @Synchronized
    @Override
    List<PhotoData> downlinkQueuedPhotos() {
        println "SIMULATOR: Checking for photos to downlink..."
        println "==== DEBUG START: downlinkQueuedPhotos ===="
        if (!activeManifest) {
            println "SIMULATOR: No active manifest. Nothing to downlink."
            println "DEBUG: No active manifest. Zwracam []."
            println "==== DEBUG END ===="
            return [] // Zwróć pustą listę
        }
        println "DEBUG: Active manifest ID: ${activeManifest.missionId}"
        println "DEBUG: Liczba zadań w manifeście: ${activeManifest.tasks.size()}"

        // Zobaczmy statusy WSZYSTKICH zadań
        activeManifest.tasks.eachWithIndex { task, i ->
            println "DEBUG: Zadanie #${i}, ID=${task.taskId}, Status=${task.status}"
        }


        // 1. Znajdź zadania, które są gotowe do wysłania
        def allQueuedTasks = activeManifest.tasks.findAll { // <-- TUTAJ TWORZYSZ LOKALNĄ ZMIENNĄ
            it.status == PhotoTaskStatus.TAKEN_AND_QUEUED
        }

        if (allQueuedTasks.isEmpty()) {
            println "SIMULATOR: No tasks queued for downlink."
            println "DEBUG: Brak zadań do wysłania. Zwracam []."
            println "==== DEBUG END ===="
            return [] // Zwróć pustą listę
        }

        // 2. Wybierz paczkę do wysłania, nie większą niż downlinkBatchSize
        def tasksForThisDownlink = allQueuedTasks.take(downlinkBatchSize)
        println "SIMULATOR: Downlinking a batch of ${tasksForThisDownlink.size()}  tasks to downlink."

        // 3. Pobierz odpowiadające im zdjęcia i wzbogać je o missionId
        def photosToSend = tasksForThisDownlink.collect { task ->
            def photo = photoStorage.get(task.taskId)
            if (photo) {
                // WZBOGACAMY DANE ZDJĘCIA O ID MISJI
                photo.missionId = this.activeManifest.missionId
            }
            return photo // Ten return jest dla bloku .collect
        }.findAll { it != null } // Usuwamy ewentualne nulle, jeśli zdjęcia nie znaleziono


        // 4. Zmień status wysłanych zadań na DOWNLINKING
        tasksForThisDownlink.each { task ->
            task.status = PhotoTaskStatus.DOWNLINKED
        }

        // 5. Zwróć przygotowaną listę zdjęć
        println "SIMULATOR: Returning a batch of ${photosToSend.size()} photos to be sent."
        println "==== DEBUG END ===="
        return photosToSend // <-- TEN RETURN JEST KLUCZOWY
    }

    @Synchronized
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

    @Synchronized
    @Override
    void connect(String authToken) {
        if (!powerOn) throw new IllegalStateException("SIMULATOR: Cannot connect, power is off.")
        this.connected = true
        println "SIMULATOR: Connection established."
    }

    @Synchronized
    @Override
    void disconnect() {
        this.connected = false
        println "SIMULATOR: Disconnecting."
    }

    @Synchronized
    @Override
    boolean isConnected() { return connected }

    // === Implementacja interfejsu SatelliteConnection ===
    // === Zmodyfikowane metody, które zależą od nowego stanu ===
    @Synchronized
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

    @Synchronized
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