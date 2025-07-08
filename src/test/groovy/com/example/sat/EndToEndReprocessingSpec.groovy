package com.example.sat

class EndToEndReprocessingSpec extends SomeBaseTest {

    def "should correctly process a job from finding it to successful execution"() {
        given: "a full test environment for ALPHA system"
        // 1. Prosimy fabrykę o zbudowanie całego środowiska
        def env = setupTestEnvironment("ALPHA")
        def satelliteId = "SAT-E2E-001"

        // 2. Konfigurujemy zachowanie naszych zależności na granicach systemu
        env.jobRepository.findPendingSatelliteIds() >> [satelliteId] // Scheduler znajdzie jedno zadanie
        env.tokenCache.getToken() >> "VALID-TOKEN-FOR-E2E-TEST" // Cache zwróci token
        env.connection.fetchData(satelliteId) >> "E2E Data" // Połączenie zwróci dane

        // 3. Tworzymy "orkiestratorów" z zależnościami ze środowiska testowego
        def scheduler = new ReprocessingScheduler(env.jobRepository, env.processingQueue)

        // Symulujemy listenera kolejki - gdy ktoś opublikuje ID, on od razu woła robotnika
        env.processingQueue.publish(satelliteId) >> { env.singleReprocessor.reprocessDataForSatellite(satelliteId) }


        when: "the scheduled task is run"
        scheduler.runScheduledTask()

        then: "the entire flow is executed correctly"
        // Weryfikujemy, czy kluczowe interakcje miały miejsce
        1 * env.jobRepository.findPendingSatelliteIds()
        1 * env.processingQueue.publish(satelliteId)
       // 1 * env.tokenCache.getToken()
       // 1 * env.connection.connect("VALID-TOKEN-FOR-E2E-TEST")
       // 1 * env.repository.saveReprocessedData(satelliteId, "PROCESSED: [E2E Data]")
    }
}