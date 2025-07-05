package com.example.sat

class AlphaSystemFailureSpec extends SomeBaseTest {

    // Prosimy klasę bazową o przygotowanie środowiska "ALPHA"
    def setup() {
        setupSystem("ALPHA")
    }

    def "should fail gracefully on ALPHA system when satellite connection fails"() {
        given: "the ALPHA satellite connection will throw an exception"
        def alphaApiKey = SYSTEM_CONFIGS["ALPHA"].apiKey
        def alphaAuthToken = "TOKEN-FOR-${alphaApiKey}"

        // Krok 1: Autoryzacja przechodzi pomyślnie
        authService.getAuthToken(alphaApiKey) >> alphaAuthToken

        // Krok 2: Symulujemy błąd na etapie connect() dla systemu ALPHA
        connection.connect(alphaAuthToken) >> { throw new java.net.ConnectException("Connection timed out to sat.alpha-partner.com") }

        when: "reprocessing is triggered"
        boolean result = service.reprocessDataForSatellite("ALPHA-SAT-456")

        then: "the operation fails"
        result == false

        and: "a failure event is sent to ALPHA telemetry"
        1 * telemetryModule.sendEvent("REPROCESSING_FAILURE", [satelliteId: "ALPHA-SAT-456", error: "Connection timed out to sat.alpha-partner.com"])

        and: "the process was aborted before sending commands or saving data"
        0 * commandModule.sendCommand(_)
        0 * repository.saveReprocessedData(_,_)

        and: "the connection was never established, so disconnect is not called"
        0 * connection.disconnect()
    }
}