package com.example.sat

import spock.lang.Specification
import spock.lang.Subject

// PRZYK?AD TESTU PRZED REFAKTORYZACJ? (WERSJA Z?O?ONA)
class ReprocessingSuccessSpec_Before_Complex extends Specification {

    // --- POCZ?TEK MASYWNEGO BLOKU POWIELONEGO KODU ------------------------------------
    // Wspólne definicje
    static final String TEST_SATELLITE_ID = "SAT-01"
    static final String VALID_API_KEY = "VALID-API-KEY"

    // Wspólne zasoby i obiekty konfiguracyjne
    SystemConfig config
    AuthService authService
    SatelliteConnection connection
    DataRepository mockRepository

    // Wspólny obiekt testowany
    @Subject
    ReprocessingService service

    // Wspólna, rozbudowana konfiguracja
    def setup() {
        println "Setting up COMPLEX test for success case..."

        // 1. Stwórz obiekt konfiguracji
        config = new SystemConfig("sat.example.com", 9001, "https://auth.example.com/token")

        // 2. Stwórz serwisy na podstawie konfiguracji
        authService = new AuthService(config.authServiceUrl)
        connection = new SatelliteConnection(config.satelliteHost, config.satellitePort)

        // 3. Stwórz mocki
        mockRepository = Mock(DataRepository) // Mock, bo weryfikujemy interakcj?

        // 4. Stwórz g?ówny serwis ze wszystkimi zale?no?ciami
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