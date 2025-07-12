package com.example.sat

import com.example.sat.fakes.FakeBatchReprocessor
import com.example.sat.fakes.ManualMockJobRepository
import com.example.sat.fakes.ManualMockProcessingQueue

class QueueProcessingSpec extends SomeBaseTest {

    def "should correctly schedule, consume and process a job via the queue"() {
        given: "a full setup with our reliable, manual mocks"
        def satelliteId = "SAT-QUEUE-01"

        // 1. Tworzymy instancje naszych RĘCZNYCH mocków
        def jobRepository = new ManualMockJobRepository()
        def processingQueue = new ManualMockProcessingQueue()
        def batchReprocessor = new FakeBatchReprocessor()

        // 2. Konfigurujemy ich stan
        jobRepository.jobsToFind = [satelliteId]
        processingQueue.jobsToConsume = [satelliteId]
        batchReprocessor.results[satelliteId] = true

        // 3. Tworzymy nasze obiekty testowane
        def coordinator = new JobCoordinator(jobRepository, processingQueue)
        def listener = new QueueListener(processingQueue, batchReprocessor, jobRepository)

        when: "the coordinator schedules jobs and the listener processes one message"
        coordinator.scheduleAllPendingJobs()
        listener.processNextMessage()

        then: "the job flows through the system and its status is updated"
        // Weryfikujemy stan naszych ręcznych mocków
        jobRepository.findInvocations == 1
        processingQueue.publishedJobs == [satelliteId]
        processingQueue.consumeInvocations == 1
        batchReprocessor.invocations[satelliteId] == 1
        jobRepository.successJobs == [satelliteId]
        jobRepository.failedJobs.isEmpty()
    }
}