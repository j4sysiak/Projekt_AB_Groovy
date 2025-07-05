package com.example.sat

import groovy.transform.Canonical

// Wersja Ostateczna: Bez żadnych adnotacji, z prostym, jawnym konstruktorem.
@Canonical
class ReprocessingService {
    // Stare zależności
    final SatelliteConnection connection
    final DataRepository repository
    final AuthService authService

    // Nowe zależności
    TelemetryModule telemetryModule
    CommandModule commandModule

    // Klucz API nie jest już przekazywany bezpośrednio
    final String apiKey

    // Używamy "ręcznego" konstruktora, aby pobrać apiKey z obiektu config
    ReprocessingService(
            SatelliteConnection connection,
            DataRepository repository,
            AuthService authService,
            TelemetryModule telemetryModule,
            CommandModule commandModule,
            SystemConfig config) {
        this.connection = connection
        this.repository = repository
        this.authService = authService
        this.telemetryModule = telemetryModule
        this.commandModule = commandModule
        this.apiKey = config.apiKey
    }

    boolean reprocessDataForSatellite(String satelliteId) {
        telemetryModule.sendEvent("REPROCESSING_STARTED", [satelliteId: satelliteId])
        boolean connectedByThisService = false
        try {
            // Krok 1: Znajdź i zarchiwizuj stare dane (NOWA LOGIKA)
            String oldData = repository.findDataById(satelliteId)
            if (oldData != null) {
                repository.archiveData(satelliteId, oldData)
            }

            // Kroki 2-4: Uzyskaj token, połącz się, pobierz nowe dane
            String token = authService.getAuthToken(apiKey)
            connection.connect(token)
            connectedByThisService = true

            commandModule.sendCommand("SET_MODE:REPROCESS")
            String rawData = connection.fetchData(satelliteId)
            String processedData = "PROCESSED: [${rawData}]"

            // Krok 5: Zapisz nowe dane
            repository.saveReprocessedData(satelliteId, processedData)

            // 6. Wyślij dane (telemetria) do satelity
            telemetryModule.sendEvent("REPROCESSING_SUCCESS", [satelliteId: satelliteId, dataSize: processedData.length()])

            return true
        } catch (Exception e) {
            telemetryModule.sendEvent("REPROCESSING_FAILURE", [satelliteId: satelliteId, error: e.message])
            println "Error during reprocessing: ${e.message}"
            return false
        } finally {
            if (connectedByThisService) {
                connection.disconnect()
            }
        }
    }
}