package com.example.sat

// Wersja Ostateczna: Bez żadnych adnotacji, z prostym, jawnym konstruktorem.
class ReprocessingService {
    final SatelliteConnection connection
    final DataRepository repository
    final AuthService authService
    // Serwis ma teraz znacznie więcej zależności (dodalismy doduly : telemetrii i commad)
    TelemetryModule telemetryClient
    CommandModule commandClient
    final String apiKey

    // Prosty, "głupi", niezawodny konstruktor pozycyjny.
    ReprocessingService(
            SatelliteConnection connection,
            DataRepository repository,
            AuthService authService,
            String apiKey) {
        this.connection = connection
        this.repository = repository
        this.authService = authService
        this.apiKey = apiKey
    }

    boolean reprocessDataForSatellite(String satelliteId) {
        boolean connectedByThisService = false
        try {
            String token = authService.getAuthToken(apiKey)
            connection.connect(token)
            connectedByThisService = true

            String rawData = connection.fetchData(satelliteId)
            String processedData = "PROCESSED: [${rawData}]"
            repository.saveReprocessedData(satelliteId, processedData)


            return true
        } catch (Exception e) {
            println "Error during reprocessing: ${e.message}"
            return false
        } finally {
            if (connectedByThisService) {
                connection.disconnect()
            }
        }
    }
}