package com.example.sat

class ReprocessingService {
    private final SatelliteConnection connection
    private final DataRepository repository
    private final AuthService authService
    private final String apiKey // Klucz API jest częścią konfiguracji serwisu

    ReprocessingService(SatelliteConnection connection, DataRepository repository, AuthService authService, String apiKey) {
        this.connection = connection
        this.repository = repository
        this.authService = authService
        this.apiKey = apiKey
    }

    boolean reprocessDataForSatellite(String satelliteId) {
        try {
            // Krok 1: Uzyskaj token
            String token = authService.getAuthToken(apiKey)

            // Krok 2: Połącz się używając tokena
            connection.connect(token)

            // Krok 3: Pobierz dane
            String rawData = connection.fetchData(satelliteId)
            String processedData = "PROCESSED: [${rawData}]"

            // Krok 4: Zapisz dane
            repository.saveReprocessedData(satelliteId, processedData)

            return true
        } catch (Exception e) {
            println "Error during reprocessing: ${e.message}"
            return false
        } finally {
            // Krok 5: Zawsze się rozłącz, jeśli połączenie było aktywne
            if (connection.isConnected()) {
                connection.disconnect()
            }
        }
    }
}