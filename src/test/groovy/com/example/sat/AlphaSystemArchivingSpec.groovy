package com.example.sat

import com.example.sat.fakes.InMemoryFileRepository

class AlphaSystemArchivingSpec extends SomeBaseTest {

    def setup() {
        setupSystem("ALPHA")
    }

    def "should correctly archive old data using file-based repository for ALPHA system"() {
        given: "the ALPHA repository already contains old data for a satellite"
        def satelliteId = "ALPHA-SAT-123"
        def oldData = "OLD ALPHA DATA"
        // Musimy rzutować, aby dostać się do wewnętrznego stanu naszej testowej implementacji
        // prykład użycia: Map<String, String>  fileSystem = ["ALPHA-SAT-123" : "OLD ALPHA DATA"]
        (repository as InMemoryFileRepository).fileSystem[satelliteId] = oldData

        and: "the rest of the dependencies are configured for success"
        def alphaApiKey = SYSTEM_CONFIGS["ALPHA"].apiKey
        authService.getAuthToken(alphaApiKey) >> "TOKEN-FOR-${alphaApiKey}"
        connection.fetchData(satelliteId) >> "NEW RAW ALPHA DATA"

        when: "reprocessing is triggered"
        service.reprocessDataForSatellite(satelliteId)

        then: "the old data is now in the file archive"
        def fileRepo = (repository as InMemoryFileRepository)
        fileRepo.archive[satelliteId] == oldData

        and: "the new data is in the main file system"
        fileRepo.fileSystem[satelliteId] == "PROCESSED: [NEW RAW ALPHA DATA]"
    }
}