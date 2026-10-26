package com.example.auto

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
        String autoId = processingQueue.consumeJob()

        if (autoId != null) {
            println "LISTENER: Received job for ${autoId}. Starting processing."

            // 2. Jeśli jest wiadomość, uruchom proces z ponawianiem prób
            boolean success = batchReprocessor.processWithRetries(autoId)

            // 3. Zaktualizuj status w repozytorium
            if (success) {
                jobRepository.markJobAsSuccess(autoId)
                println "LISTENER: Job for ${autoId} marked as SUCCESS."
            } else {
                jobRepository.markJobAsFailed(autoId)
                println "LISTENER: Job for ${autoId} marked as FAILED."
            }
        } else {
            println "LISTENER: Queue is empty. Nothing to do."
        }
    }
}