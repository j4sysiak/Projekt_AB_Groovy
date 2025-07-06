package com.example.sat

// Bez żadnych adnotacji. Czysta, prosta klasa.
class BatchReprocessingService {
    // Zależymy od interfejsu, a nie od konkretnej klasy
    final ISingleReprocessor singleReprocessor
    int maxRetries = 3 // Domyślna wartość, którą możemy nadpisać w teście

    // Prosty, jawny, jednoargumentowy konstruktor.
    BatchReprocessingService(ISingleReprocessor singleReprocessor) {
        this.singleReprocessor = singleReprocessor
    }

    boolean processWithRetries(String satelliteId) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            println "Processing ${satelliteId}, attempt ${attempt}/${maxRetries}..."

            // Wołamy metodę z interfejsu
            if (singleReprocessor.reprocessDataForSatellite(satelliteId)) {
                println "Processing ${satelliteId} succeeded on attempt ${attempt}."
                return true
            }

            println "Attempt ${attempt} failed for ${satelliteId}. Retrying..."
        }
        println "Max retries reached. Giving up on ${satelliteId}."
        return false
    }
}