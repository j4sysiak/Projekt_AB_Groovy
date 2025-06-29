package com.example.sat

// Główna usługa, której funkcjonalność testujemy
class ReprocessingService {
    private final SatelliteConnection connection
    private final DataRepository repository

    ReprocessingService(SatelliteConnection connection, DataRepository repository) {
        this.connection = connection
        this.repository = repository
    }

    // Metoda wykonująca główną logikę biznesową
    boolean reprocessDataForSatellite(String satelliteId) {
        if (!connection.isConnected()) {
            println "Service cannot reprocess: connection is down."
            return false
        }

        try {
            String rawData = connection.fetchData(satelliteId)
            String processedData = "PROCESSED: [${rawData}]"
            repository.saveReprocessedData(satelliteId, processedData)
            return true
        } catch (Exception e) {
            println "Error during reprocessing: ${e.message}"
            return false
        }
    }
}