package com.example.sat

class QueueListener {
    final ProcessingQueue processingQueue
    final BatchReprocessingService batchReprocessor
    final JobRepository jobRepository // Potrzebny do aktualizacji statusu

    QueueListener(ProcessingQueue processingQueue, BatchReprocessingService batchReprocessor, JobRepository jobRepository) {
        this.processingQueue = processingQueue
        this.batchReprocessor = batchReprocessor
        this.jobRepository = jobRepository
    }

    // Ta metoda symuluje nieskończoną pętlę, która sprawdza kolejkę.
    // W teście wywołamy ją raz, aby przetworzyć jedną wiadomość.
    void processNextMessage() {
        println "LISTENER: Checking queue for a new message..."

        // 1. Spróbuj pobrać wiadomość z kolejki
        String satelliteId = processingQueue.consumeJob()

        if (satelliteId != null) {
            println "LISTENER: Received job for ${satelliteId}. Starting processing."

            // 2. Jeśli jest wiadomość, uruchom proces z ponawianiem prób
            boolean success = batchReprocessor.processWithRetries(satelliteId)

            // 3. Zaktualizuj status w repozytorium
            if (success) {
                jobRepository.markJobAsSuccess(satelliteId)
                println "LISTENER: Job for ${satelliteId} marked as SUCCESS."
            } else {
                jobRepository.markJobAsFailed(satelliteId)
                println "LISTENER: Job for ${satelliteId} marked as FAILED."
            }
        } else {
            println "LISTENER: Queue is empty. Nothing to do."
        }
    }
}