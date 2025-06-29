package com.example.sat

// Test sukcesu jest teraz bardzo zwięzły.
// Dziedziczy całą konfigurację z SomeBaseTest.
class ReprocessingSuccessSpec extends SomeBaseTest {

    def "should successfully reprocess data when all dependencies work correctly"() {
        given: "a specific satellite ID to process"
        def satelliteId = "SAT-GAMMA-03"

        // Konfiguracja specyficzna dla tego testu:
        // Musimy zdefiniować, co zwróci `fetchData`, bo klasa bazowa tego nie robi.
        connection.fetchData(satelliteId) >> "Raw data for satellite ${satelliteId}"

        when: "reprocessing is triggered"
        boolean result = service.reprocessDataForSatellite(satelliteId)

        then: "the operation is successful"
        result == true

        and: "the full chain of interactions happened correctly"
        // Weryfikujemy, czy wszystkie mocki zostały użyte zgodnie z oczekiwaniami
        1 * authService.getAuthToken(VALID_API_KEY)
        1 * connection.connect(VALID_AUTH_TOKEN)
        1 * connection.fetchData(satelliteId)
        1 * repository.saveReprocessedData(satelliteId, "PROCESSED: [Raw data for satellite ${satelliteId}]")
        1 * connection.disconnect()
    }
}