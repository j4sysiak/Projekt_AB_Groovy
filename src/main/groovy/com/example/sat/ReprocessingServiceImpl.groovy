package com.example.sat

class ReprocessingServiceImpl implements ISingleReprocessor {
    final SatelliteConnection connection
    final DataRepository repository
    final AuthService authService
    final TelemetryModule telemetryModule
    final CommandModule commandModule
    private final String apiKey

    // Flaga tylko do celów testowych, aby wymusić błąd
    public boolean forceConnectionFailure = false

    ReprocessingServiceImpl(
            SatelliteConnection connection, DataRepository repository,
            AuthService authService, TelemetryModule telemetryModule,
            CommandModule commandModule, SystemConfig config) {
        this.connection = connection
        this.repository = repository
        this.authService = authService
        this.telemetryModule = telemetryModule
        this.commandModule = commandModule
        this.apiKey = config.apiKey
    }

    // Implementujemy metodę z interfejsu
    @Override
    boolean reprocessDataForSatellite(String satelliteId) {
        telemetryModule.sendEvent("REPROCESSING_STARTED", [satelliteId: satelliteId])
        boolean connectedByThisService = false
        try {
            String token = authService.getAuthToken(apiKey)

            // KLUCZOWA ZMIANA: Ręcznie symulujemy błąd, jeśli flaga jest ustawiona
            if (forceConnectionFailure) {
                throw new IllegalStateException("Forced connection failure for testing")
            }

            connection.connect(token)
            connectedByThisService = true

            // ... reszta logiki bez zmian ...
            commandModule.sendCommand("SET_MODE:REPROCESS")
            String rawData = connection.fetchData(satelliteId)
            String processedData = "PROCESSED: [${rawData}]"
            repository.saveReprocessedData(satelliteId, processedData)
            telemetryModule.sendEvent("REPROCESSING_SUCCESS", [satelliteId: satelliteId, dataSize: processedData.length()])
            return true

        } catch (Exception e) {
            telemetryModule.sendEvent("REPROCESSING_FAILURE", [satelliteId: satelliteId, error: e.message])
            println "Error during single reprocessing attempt: ${e.message}"
            return false
        } finally {
            if (connectedByThisService) {
                connection.disconnect()
            }
        }
    }
}