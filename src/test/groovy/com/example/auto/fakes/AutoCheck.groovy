package com.example.auto.fakes

import com.example.auto.dto.PhotoCollectionAction
import com.example.auto.dto.PhotoData
import com.example.auto.dto.PhotoTaskStatus
import com.example.auto.simulation.IAutoCheck
import groovy.transform.Synchronized

// Importujemy TYLKO jeden, nadrzędny interfejs


// ZMIANA: Implementujemy tylko IAutoCheck
class AutoCheck implements IAutoCheck {

    // === STAN WEWNĘTRZNY ===
    double batteryLevel = 95.0
    String solarPanelStatus = "CHARGING"
    String thrusterHealth = "NOMINAL"
    boolean powerOn = true
    boolean connected = false
    String cameraMode = "STANDBY"
    List<String> commandHistory = []

    // === NOWA, SPÓJNA LOGIKA ZARZĄDZANIA MISJAMI ===
    PhotoCollectionAction activeManifest
    Map<String, PhotoData> photoStorage = [:]
    final int downlinkBatchSize = 40

    @Synchronized
    @Override
    void storeActionPlan(PhotoCollectionAction manifest) {
        println "checker: Storing photo collection manifest ${manifest.actionId}..."

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
            println "checker: No active manifest. Photo taking process skipped."
            return
        }
        println "checker: Starting photo taking process for action ${activeManifest.actionId}..."
        activeManifest.tasks.each { task ->
            // Sprawdzamy, czy zadanie jest wciąż do wykonania i czy mamy na to energię
            if (task.status == PhotoTaskStatus.PENDING && batteryLevel > 0.2) {
                // Tworzymy NOWY, KOMPLETNY obiekt PhotoData
                def newPhoto = new PhotoData(
                    // Używamy ID zadania jako ID zdjęcia dla spójności
                    photoId: task.taskId,

                    // Używamy ID akcji z aktywnego manifestu
                    actionId: this.activeManifest.actionId,

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

                println "checker: Photo taken for task ${task.taskId}"
                }
            }
        println "PHOTO STORAGE after shooting: ${photoStorage.size()} photos stored."
        println "checker: Photo taking process finished."
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
            it.status == com.example.auto.dto.PhotoTaskStatus.TAKEN_AND_QUEUED
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
            task.status = com.example.auto.dto.PhotoTaskStatus.DOWNLINKED
        }

        // Krok 5: Zwróć paczkę zdjęć.
        return photosToSend
    }*/



    // Metoda do wysyłania danych w paczkach
    // Downlink zdjęć z AUTO (AutoCheckServer) do SERW1 (IntermediateServerApp)
    @Synchronized
    @Override
    List<PhotoData> downlinkQueuedPhotos() {
        println "checker: Checking for photos to downlink..."
        println "==== DEBUG START: downlinkQueuedPhotos ===="
        if (!activeManifest) {
            println "checker: No active manifest. Nothing to downlink."
            println "DEBUG: No active manifest. Zwracam []."
            println "==== DEBUG END ===="
            return [] // Zwróć pustą listę
        }
        println "DEBUG: Active manifest ID: ${activeManifest.actionId}"
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
            println "checker: No tasks queued for downlink."
            println "DEBUG: Brak zadań do wysłania. Zwracam []."
            println "==== DEBUG END ===="
            return [] // Zwróć pustą listę
        }

        // 2. Wybierz paczkę do wysłania, nie większą niż downlinkBatchSize
        def tasksForThisDownlink = allQueuedTasks.take(downlinkBatchSize)
        println "checker: Downlinking a batch of ${tasksForThisDownlink.size()}  tasks to downlink."

        // 3. Pobierz odpowiadające im zdjęcia i wzbogać je o actionId
        def photosToSend = tasksForThisDownlink.collect { task ->
            def photo = photoStorage.get(task.taskId)
            if (photo) {
                // WZBOGACAMY DANE ZDJĘCIA O ID AKCJI
                photo.actionId = this.activeManifest.actionId
            }
            return photo // Ten return jest dla bloku .collect
        }.findAll { it != null } // Usuwamy ewentualne nulle, jeśli zdjęcia nie znaleziono


        // 4. Zmień status wysłanych zadań na DOWNLINKING
        tasksForThisDownlink.each { task ->
            task.status = PhotoTaskStatus.DOWNLINKED
        }

        // 5. Zwróć przygotowaną listę zdjęć
        println "checker: Returning a batch of ${photosToSend.size()} photos to be sent."
        println "==== DEBUG END ===="
        return photosToSend // <-- TEN RETURN JEST KLUCZOWY
    }

    @Synchronized
    @Override
    String sendCommand(String command, Map params = [:]) {
        if (!connected) throw new IllegalStateException("checker: Not connected.")
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
        if (!powerOn) throw new IllegalStateException("checker: Cannot connect, power is off.")
        this.connected = true
        println "checker: Connection established."
    }

    @Synchronized
    @Override
    void disconnect() {
        this.connected = false
        println "checker: Disconnecting."
    }

    @Synchronized
    @Override
    boolean isConnected() { return connected }

    // === Implementacja interfejsu AutoConnection ===
    // === Zmodyfikowane metody, które zależą od nowego stanu ===
    @Synchronized
    @Override
    String fetchData(String autoId) {
        if (!connected) throw new IllegalStateException("checker: Not connected.")
        if (forceFetchDataToFail) throw new RuntimeException("Forced fetch data failure for testing")
        if (batteryLevel < POWER_CONSUMPTION_TAKE_PHOTO + 5.0) throw new RuntimeException("checker: Insufficient power...")
        if (cameraMode != "ACTIVE_IMAGING") throw new RuntimeException("checker: Cannot fetch data, camera is in ${cameraMode} mode.")

        println "checker: Fetching simulated meteorological data..."
        batteryLevel -= POWER_CONSUMPTION_TAKE_PHOTO
        return "<SimulatedImageData>${autoId}-${System.currentTimeMillis()}</SimulatedImageData>"
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
        println "checker: State has been reset."
    }
}