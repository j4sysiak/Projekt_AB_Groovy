package com.example.sat

import spock.lang.Specification
import spock.lang.Subject

// PRZYKŁAD TESTU PRZED REFAKTORYZACJĄ
class ReprocessingSuccessSpec_Before extends Specification {

    // --- POCZĄTEK POWIELONEGO KODU ------------------------------------------------------
    // Wspólne definicje
    static final String SATELLITE_HOST = "sat.example.com"
    static final int SATELLITE_PORT = 9001
    static final String TEST_SATELLITE_ID = "SAT-01"

    // Wspólne zasoby
    SatelliteConnection connection
    DataRepository mockRepository

    // Wspólny obiekt testowany
    @Subject
    ReprocessingService service

    // Wspólna konfiguracja
    def setup() {
        println "Setting up test for success case..."
        connection = new SatelliteConnection(SATELLITE_HOST, SATELLITE_PORT)
        connection.connect() // Każdy test potrzebuje aktywnego połączenia

        mockRepository = Stub(DataRepository) // Używamy Stub, bo nie weryfikujemy interakcji
        service = new ReprocessingService(connection, mockRepository)
    }

    def cleanup() {
        connection.disconnect()
        println "Cleaned up test for success case."
    }
    // --- KONIEC POWIELONEGO KODU ----------------------------------------------------------------------

    def "should successfully reprocess data when connection is active"() {
        when: "reprocessing is triggered"
        boolean result = service.reprocessDataForSatellite(TEST_SATELLITE_ID)

        then: "the operation should succeed"
        result == true
    }
}