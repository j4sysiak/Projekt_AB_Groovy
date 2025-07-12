package com.example.sat

// Bez żadnych adnotacji. Czysta, prosta klasa.
class JobCoordinator {
    final JobRepository jobRepository
    final ProcessingQueue processingQueue

    // Prosty, jawny, niezawodny konstruktor.
    JobCoordinator(JobRepository jobRepository, ProcessingQueue processingQueue) {
        this.jobRepository = jobRepository
        this.processingQueue = processingQueue
    }

    void scheduleAllPendingJobs() {
        println "COORDINATOR: Starting to schedule all pending jobs."

        def pendingJobIds = jobRepository.findPendingJobs()

        // Teraz, gdy stub zadziała, pendingJobIds nie będzie nullem.
        if (pendingJobIds.isEmpty()) {
            println "COORDINATOR: No pending jobs to schedule."
            return
        }

        println "COORDINATOR: Found ${pendingJobIds.size()} jobs. Publishing to queue."

        pendingJobIds.each { satelliteId ->
            processingQueue.publishJob(satelliteId)
            println "COORDINATOR: Job for ${satelliteId} has been published to the queue."
        }

        println "COORDINATOR: Finished publishing all jobs."
    }
}