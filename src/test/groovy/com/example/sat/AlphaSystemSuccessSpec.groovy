package com.example.sat

class AlphaSystemSuccessSpec extends SomeBaseTest {


    // W metodzie setup, po prostu prosimy klasę bazową o przygotowanie środowiska "ALPHA"
    def setup() {
        setupSystem("ALPHA")
    }

    def "should successfully reprocess data for ALPHA system"() {
        given: "the ALPHA system dependencies are correctly stubbed for a success scenario"
        def alphaApiKey = SYSTEM_CONFIGS["ALPHA"].apiKey
        def alphaAuthToken = "TOKEN-FOR-${alphaApiKey}"

        authService.getAuthToken(alphaApiKey) >> alphaAuthToken
        connection.fetchData(_) >> "Raw ALPHA Data"
        commandModule.sendCommand("SET_MODE:REPROCESS") >> "ALPHA_ACK"

        when: "reprocessing is triggered"
        boolean result = service.reprocessDataForSatellite("ALPHA-SAT-123")

        then: "the operation succeeds"
        result == true

        and: "all ALPHA system modules were called correctly"
        1 * telemetryModule.sendEvent("REPROCESSING_STARTED", _)
       1 * authService.getAuthToken(alphaApiKey)
       1 * connection.connect(alphaAuthToken)
       1 * commandModule.sendCommand("SET_MODE:REPROCESS")
        1 * repository.saveReprocessedData("ALPHA-SAT-123", "PROCESSED: [Raw ALPHA Data]")
        1 * telemetryModule.sendEvent("REPROCESSING_SUCCESS", _)
     1 * connection.disconnect()
    }

}