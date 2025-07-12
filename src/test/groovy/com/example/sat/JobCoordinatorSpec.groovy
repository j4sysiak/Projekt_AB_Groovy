package com.example.sat

class JobCoordinatorSpec extends SomeBaseTest {

    def "should process all found jobs and update their statuses correctly"() {
        given: "a setup with two pending jobs, one succeeding and one failing"
        def successJobId = "SAT-SUCCESS-01"
        def failureJobId = "SAT-FAIL-01"

        // 1. Tworzymy mocki dla zależności niższego rzędu
        def jobRepository = Mock(JobRepository)
        def batchReprocessor = Mock(BatchReprocessingService)

        // 2. Konfigurujemy zachowanie mocków
        // Repozytorium zwróci dwa zadania
        jobRepository.findPendingJobs() >> [successJobId, failureJobId]

        // Serwis od 'retry' powie, że pierwsze zadanie się udało, a drugie nie
        batchReprocessor.processWithRetries(successJobId) >> true
        batchReprocessor.processWithRetries(failureJobId) >> false

        // 3. Tworzymy nasz obiekt testowany - Koordynatora
        def coordinator = new JobCoordinator(jobRepository, batchReprocessor)

        when: "the coordinator processes all pending jobs"
        coordinator.processAllPendingJobs()

        then: "the statuses of the jobs are updated correctly in the repository"
        // Sprawdzamy, czy odpowiednie metody zostały wywołane na mocku repozytorium
        1 * jobRepository.markJobAsSuccess(successJobId)
        1 * jobRepository.markJobAsFailed(failureJobId)

        and: "no other status updates were made"
        0 * jobRepository.markJobAsSuccess(failureJobId)
        0 * jobRepository.markJobAsFailed(successJobId)
    }
}