package com.example.sat

import groovy.transform.Canonical

@Canonical
class ReprocessingScheduler {
    JobRepository jobRepository
    ProcessingQueue processingQueue

    // Metoda, która byłaby wołana np. co 5 minut przez system harmonogramowania
    void runScheduledTask() {
        println "SCHEDULER: Checking for pending jobs..."
        def pendingIds = jobRepository.findPendingSatelliteIds()
        if (pendingIds) {
            println "SCHEDULER: Found ${pendingIds.size()} jobs. Publishing to queue."
            pendingIds.each { id -> processingQueue.publish(id) }
        } else {
            println "SCHEDULER: No pending jobs found."
        }
    }
}