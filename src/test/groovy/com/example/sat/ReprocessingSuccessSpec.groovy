package com.example.sat

// Test dziedziczy po SomeBaseTest, zyskuj?c dost?p do wszystkich jego pól i metod.
class ReprocessingSuccessSpec extends SomeBaseTest {

    def "should succeed and save data when reprocessing a valid satellite"() {
        given: "a valid satellite ID"
        def satelliteId = DEFAULT_SATELLITE_ID

        when: "the reprocessing service is called"
        boolean result = service.reprocessDataForSatellite(satelliteId)

        then: "the operation is successful"
        result == true

        and: "the processed data is saved to the repository exactly once"
        // Weryfikujemy, ?e na mocku zosta?a wywo?ana metoda saveReprocessedData
        1 * mockRepository.saveReprocessedData(satelliteId, "PROCESSED: [Raw data for satellite ${satelliteId}]")
    }

    def "should handle another success scenario"() {
        given: "a different satellite ID"
        def satelliteId = "SAT-BETA-02"

        when: "reprocessing is triggered for it"
        def result = service.reprocessDataForSatellite(satelliteId)

        then: "it also succeeds"
        result == true
        1 * mockRepository.saveReprocessedData(satelliteId, "PROCESSED: [Raw data for satellite ${satelliteId}]")
    }
}