package com.example.sat

class ReprocessingSuccessSpec extends SomeBaseTest {

    def "should successfully reprocess data when all dependencies work correctly"() {
        given:
        authService.getAuthToken(VALID_API_KEY) >> VALID_AUTH_TOKEN
        connection.fetchData(DEFAULT_SATELLITE_ID) >> "Raw data for satellite ${DEFAULT_SATELLITE_ID}"
        def service = new ReprocessingService(connection, repository, authService, VALID_API_KEY)

        when:
        boolean result = service.reprocessDataForSatellite(DEFAULT_SATELLITE_ID)

        then:
        result == true
        1 * authService.getAuthToken(VALID_API_KEY)
        1 * connection.connect(VALID_AUTH_TOKEN)
        1 * connection.fetchData(DEFAULT_SATELLITE_ID)
        1 * repository.saveReprocessedData(DEFAULT_SATELLITE_ID, "PROCESSED: [Raw data for satellite ${DEFAULT_SATELLITE_ID}]")
        1 * connection.disconnect()
    }
}