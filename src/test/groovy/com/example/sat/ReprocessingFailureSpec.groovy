package com.example.sat

class ReprocessingFailureSpec extends SomeBaseTest {

    def "should fail gracefully when an invalid API key is used"() {
        given: "the authentication service will reject invalid keys"
        authService.getAuthToken(INVALID_API_KEY) >> { throw new IllegalArgumentException("Invalid API Key") }

        // Tworzymy serwis używając prostego, pozycyjnego konstruktora
        service = new ReprocessingService(connection, repository, authService, INVALID_API_KEY)

        when: "reprocessing is attempted with the service configured with a bad key"
        boolean result = service.reprocessDataForSatellite(DEFAULT_SATELLITE_ID)

        then: "the operation should fail"
        result == false

        and: "the process should stop at authentication"
        1 * authService.getAuthToken(INVALID_API_KEY)
        0 * connection.connect(_)
        0 * connection.fetchData(_)
        0 * repository.saveReprocessedData(_, _)
    }
}