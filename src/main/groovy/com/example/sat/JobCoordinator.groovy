package com.example.sat

import groovy.transform.Canonical

@Canonical
class JobCoordinator {
    // Zależności, których potrzebuje koordynator
    JobRepository jobRepository
    BatchReprocessingService batchReprocessor // Używa serwisu z logiką 'retry'

    // Główna metoda, która uruchamia cały proces
    void processAllPendingJobs() {
        println "COORDINATOR: Starting to process all pending jobs."

        // 1. Pobierz listę zadań do zrobienia
        def pendingJobIds = jobRepository.findPendingJobs()

        if (pendingJobIds.isEmpty()) {
            println "COORDINATOR: No pending jobs to process."
            return
        }

        println "COORDINATOR: Found ${pendingJobIds.size()} jobs. Processing one by one."

        // 2. Dla każdego zadania...
        pendingJobIds.each { satelliteId ->
            // ...uruchom proces z ponawianiem prób...
            boolean success = batchReprocessor.processWithRetries(satelliteId)

            // 3. ...i zaktualizuj status w repozytorium.
            if (success) {
                jobRepository.markJobAsSuccess(satelliteId)
                println "COORDINATOR: Job for ${satelliteId} marked as SUCCESS."
            } else {
                jobRepository.markJobAsFailed(satelliteId)
                println "COORDINATOR: Job for ${satelliteId} marked as FAILED."
            }
        }

        println "COORDINATOR: Finished processing all jobs."
    }
}