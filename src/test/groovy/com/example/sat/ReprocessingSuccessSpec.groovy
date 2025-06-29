package com.example.sat

class ReprocessingSuccessSpec extends SomeBaseTest {

    def "should succeed and save data when reprocessing a valid satellite"() {
        given: "a valid satellite ID"
        def satelliteId = DEFAULT_SATELLITE_ID

        // Ponieważ 'connection' jest teraz mockiem, musimy zdefiniować jego zachowanie.
        connection.fetchData(satelliteId) >> "Raw data for satellite ${satelliteId}"

        when: "the reprocessing service is called"
        boolean result = service.reprocessDataForSatellite(satelliteId)

        then: "the operation is successful"
        result == true

        and: "the processed data is saved to the repository exactly once"
        1 * repository.saveReprocessedData(satelliteId, "PROCESSED: [Raw data for satellite ${satelliteId}]")
    }

    def "should handle another success scenario"() {
        given: "a different satellite ID"
        def satelliteId = "SAT-BETA-02"
        // Definiujemy zachowanie mocka dla tego konkretnego przypadku
        connection.fetchData(satelliteId) >> "Raw data for satellite ${satelliteId}"

        when: "reprocessing is triggered for it"
        def result = service.reprocessDataForSatellite(satelliteId)

        then: "it also succeeds"
        result == true
        1 * repository.saveReprocessedData(satelliteId, "PROCESSED: [Raw data for satellite ${satelliteId}]")
    }
}