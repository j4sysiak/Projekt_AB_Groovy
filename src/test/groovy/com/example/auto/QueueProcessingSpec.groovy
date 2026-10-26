package com.example.auto

import com.example.auto.fakes.FakeBatchReprocessor
import com.example.auto.fakes.ManualMockJobRepository
import com.example.auto.fakes.ManualMockProcessingQueue

class QueueProcessingSpec extends SomeBaseTest {

    def "should correctly schedule, consume and process a job via the queue"() {
        given: "a full setup with our reliable, manual mocks"
        def autoId = "AUTO-QUEUE-01"

        // 1. Tworzymy instancje naszych RĘCZNYCH mocków
        def jobRepository = new ManualMockJobRepository()
        def processingQueue = new ManualMockProcessingQueue()
        def batchReprocessor = new FakeBatchReprocessor()

        // 2. Konfigurujemy ich stan
        jobRepository.jobsToFind = [autoId]
        processingQueue.jobsToConsume = [autoId]
        batchReprocessor.results[autoId] = true

        // 3. Tworzymy nasze obiekty testowane
        def coordinator = new JobCoordinator(jobRepository, processingQueue)
        def listener = new QueueListener(processingQueue, batchReprocessor, jobRepository)

        when: "the coordinator schedules jobs and the listener processes one message"
        coordinator.scheduleAllPendingJobs()
        listener.processNextMessage()

        then: "the job flows through the system and its status is updated"
        // Weryfikujemy stan naszych ręcznych mocków
        jobRepository.findInvocations == 1
        processingQueue.publishedJobs == [autoId]
        processingQueue.consumeInvocations == 1
        batchReprocessor.invocations[autoId] == 1
        jobRepository.successJobs == [autoId]
        jobRepository.failedJobs.isEmpty()
    }
}