package com.example.sat

class ReprocessingServiceImpl implements ISingleReprocessor {
    final SatelliteConnection connection
    final DataRepository repository
    final TelemetryModule telemetryModule
    final CommandModule commandModule

    // ZMIANA: Zależność od TokenCache
    final TokenCache tokenCache
    // private final String apiKey
    // final AuthService authService

    // Flaga tylko do celów testowych, aby wymusić błąd
       public boolean forceConnectionFailure = false

    // Usunęliśmy AuthService i apiKey z konstruktora
    ReprocessingServiceImpl(
            SatelliteConnection connection, DataRepository repository,
            TokenCache tokenCache, TelemetryModule telemetryModule,
            CommandModule commandModule) {
        this.connection = connection
        this.repository = repository
        this.tokenCache = tokenCache
        this.telemetryModule = telemetryModule
        this.commandModule = commandModule
    }

    // Implementujemy metodę z interfejsu
    @Override
    boolean reprocessDataForSatellite(String satelliteId) {
        telemetryModule.sendEvent("REPROCESSING_STARTED", [satelliteId: satelliteId])
        boolean connectedByThisService = false
        try {
            // ZMIANA: Pobieramy token z cache'u, a nie bezpośrednio z AuthService
            String token = tokenCache.getToken()
            //String token = authService.getAuthToken(apiKey)

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