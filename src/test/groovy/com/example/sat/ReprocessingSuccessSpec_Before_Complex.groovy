package com.example.sat

import spock.lang.Specification
import spock.lang.Subject

// PRZYK?AD TESTU PRZED REFAKTORYZACJ? (WERSJA Z?O?ONA)
class ReprocessingSuccessSpec_Before_Complex extends Specification {

    // --- POCZ?TEK MASYWNEGO BLOKU POWIELONEGO KODU ------------------------------------
    // Wsp�lne definicje
    static final String TEST_SATELLITE_ID = "SAT-01"
    static final String VALID_API_KEY = "VALID-API-KEY"

    // Wsp�lne zasoby i obiekty konfiguracyjne
    SystemConfig config
    AuthService authService
    SatelliteConnection connection
    DataRepository mockRepository

    // Wsp�lny obiekt testowany
    @Subject
    ReprocessingService service

    // Wsp�lna, rozbudowana konfiguracja
    def setup() {
        println "Setting up COMPLEX test for success case..."

        // 1. Stw�rz obiekt konfiguracji
        config = new SystemConfig("sat.example.com", 9001, "https://auth.example.com/token")

        // 2. Stw�rz serwisy na podstawie konfiguracji
        authService = new AuthServiceImpl(config.authServiceUrl)
        connection = new SatelliteConnectionImpl(config.satelliteHost, config.satellitePort)

        // 3. Stw�rz mocki
        mockRepository = Mock(DataRepository) // Mock, bo weryfikujemy interakcj?

        // 4. Stw�rz g?�wny serwis ze wszystkimi zale?no?ciami
        service = new ReprocessingService(connection, mockRepository, authService, VALID_API_KEY)
    }

    def cleanup() {
        println "Cleaned up COMPLEX test for success case."
    }
    // --- KONIEC MASYWNEGO BLOKU POWIELONEGO KODU --------------------------------------

    def "should successfully reprocess data with valid credentials"() {
        when: "reprocessing is triggered"
        boolean result = service.reprocessDataForSatellite(TEST_SATELLITE_ID)

        then: "the operation should succeed"
        result == true

        and: "the data should be saved"
        1 * mockRepository.saveReprocessedData(TEST_SATELLITE_ID, "PROCESSED: [Raw data for satellite ${TEST_SATELLITE_ID}]")
    }
}