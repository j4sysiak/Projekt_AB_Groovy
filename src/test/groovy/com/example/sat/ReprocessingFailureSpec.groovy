package com.example.sat

// Test błędu również jest bardzo prosty.
// Nadpisuje tylko te elementy, które są potrzebne do symulacji błędu.
class ReprocessingFailureSpec extends SomeBaseTest {

    def "should fail gracefully when authentication service throws an exception"() {
        given: "the authentication service will fail"
        // Nadpisujemy domyślne zachowanie zdefiniowane w SomeBaseTest.setup()
        authService.getAuthToken(_) >> { throw new IllegalArgumentException("Invalid API Key provided") }

        when: "reprocessing is attempted"
        boolean result = service.reprocessDataForSatellite(DEFAULT_SATELLITE_ID)

        then: "the operation should fail"
        result == false

        and: "no downstream services should be called"
        // Weryfikujemy, że proces zatrzymał się na etapie autoryzacji
        0 * connection.connect(_)
        0 * connection.fetchData(_)
        0 * repository.saveReprocessedData(_, _)
    }

    def "should fail if connection cannot be established"() {
        given: "the connection will fail to establish, even with a valid token"
        // Symulujemy błąd na etapie connect()
        connection.connect(VALID_AUTH_TOKEN) >> { throw new IllegalStateException("Connection refused by host") }

        when: "reprocessing is attempted"
        boolean result = service.reprocessDataForSatellite(DEFAULT_SATELLITE_ID)

        then: "the operation should fail"
        result == false

        and: "data fetching and saving should not be attempted"
        1 * authService.getAuthToken(VALID_API_KEY) // Autoryzacja się powiodła
        0 * connection.fetchData(_)
        0 * repository.saveReprocessedData(_, _)
    }
}