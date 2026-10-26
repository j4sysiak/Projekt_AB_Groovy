package com.example.auto

// Bez żadnych adnotacji. Czysta, prosta klasa.
class BatchReprocessingService {
    // Zależymy od interfejsu, a nie od konkretnej klasy
    final ISingleReprocessor singleReprocessor
    int maxRetries = 3 // Domyślna wartość, którą możemy nadpisać w teście

    // Prosty, jawny, jednoargumentowy konstruktor.
    BatchReprocessingService(ISingleReprocessor singleReprocessor) {
        this.singleReprocessor = singleReprocessor
    }

    boolean processWithRetries(String autoId) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            println "Processing ${autoId}, attempt ${attempt}/${maxRetries}..."

            // Wołamy metodę z interfejsu
            if (singleReprocessor.reprocessDataForAuto(autoId)) {
                println "Processing ${autoId} succeeded on attempt ${attempt}."
                return true
            }

            println "Attempt ${attempt} failed for ${autoId}. Retrying..."
        }
        println "Max retries reached. Giving up on ${autoId}."
        return false
    }
}