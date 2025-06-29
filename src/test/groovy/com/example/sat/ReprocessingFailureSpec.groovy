package com.example.sat

class ReprocessingFailureSpec extends SomeBaseTest {

    def "should fail gracefully if data fetching throws an exception"() {
        given: "the satellite connection will fail during data fetch"
        // Ta linia teraz zadziała poprawnie, bo 'connection' to mock
        connection.fetchData(_) >> { throw new RuntimeException("Signal Lost") }

        when: "reprocessing is attempted"
        boolean result = service.reprocessDataForSatellite(DEFAULT_SATELLITE_ID)

        then: "the operation should fail"
        result == false

        and: "no data should be saved to the repository"
        // Ta asercja teraz przejdzie, bo wyjątek przerwie wykonanie i metoda nie zostanie wywołana
        0 * repository.saveReprocessedData(_, _)
    }
}