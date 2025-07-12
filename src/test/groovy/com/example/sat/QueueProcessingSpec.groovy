package com.example.sat

class QueueProcessingSpec extends SomeBaseTest {

    def "should correctly schedule, consume and process a job via the queue"() {
        given: "a full setup with a job waiting in the repository"
        def satelliteId = "SAT-QUEUE-01"

        // 1. Tworzymy wszystkie potrzebne mocki
        def jobRepository = Mock(JobRepository)
        def processingQueue = Mock(ProcessingQueue)
        def batchReprocessor = Mock(BatchReprocessingService)

        // 2. Konfigurujemy zachowanie mocków dla całego przepływu
        // Koordynator znajdzie jedno zadanie
        jobRepository.findPendingJobs() >> [satelliteId]

        // Listener pobierze to zadanie z kolejki
        processingQueue.consumeJob() >> satelliteId

        // Serwis przetwarzający zakończy zadanie sukcesem
        batchReprocessor.processWithRetries(satelliteId) >> true

        // 3. Tworzymy nasze obiekty testowane
        def coordinator = new JobCoordinator(jobRepository, processingQueue)
        def listener = new QueueListener(processingQueue, batchReprocessor, jobRepository)

        when: "the coordinator schedules jobs and the listener processes one message"
        coordinator.scheduleAllPendingJobs()
        listener.processNextMessage()

        then: "the job flows through the system and its status is updated"
        // Sprawdzamy, czy kluczowe interakcje miały miejsce we właściwej kolejności
        1 * jobRepository.findPendingJobs()
        1 * processingQueue.publishJob(satelliteId)
        1 * processingQueue.consumeJob()
        1 * batchReprocessor.processWithRetries(satelliteId)
        1 * jobRepository.markJobAsSuccess(satelliteId)
        0 * jobRepository.markJobAsFailed(_) // Upewniamy się, że nie został oznaczony jako błąd
    }
}