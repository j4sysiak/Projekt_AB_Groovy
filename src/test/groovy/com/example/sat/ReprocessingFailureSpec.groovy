package com.example.sat

class ReprocessingFailureSpec extends SomeBaseTest {

    def "should fail gracefully if data fetching throws an exception"() {
        given: "the satellite connection will fail during data fetch"
        // Definiujemy zachowanie mocka - rzuci wyjątkiem, gdy ktoś go zapyta o dane
        connection.fetchData(_) >> { throw new RuntimeException("Signal Lost") }

        when: "reprocessing is attempted"
        boolean result = service.reprocessDataForSatellite(DEFAULT_SATELLITE_ID)

        then: "the operation should fail"
        result == false

        and: "no data should be saved to the repository"
        0 * mockRepository.saveReprocessedData(_, _)
    }
}