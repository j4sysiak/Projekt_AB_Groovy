package com.example.sat

import spock.lang.Specification
import spock.lang.Subject

// PRZYKŁAD TESTU BŁĘDU PRZED REFAKTORYZACJĄ (WERSJA ZŁOŻONA)
class ReprocessingFailureSpec_Before_Complex extends Specification {

    // --- POCZĄTEK MASYWNEGO BLOKU POWIELONEGO KODU (IDENTYCZNY!) ---------------------
    // Wspólne definicje
    static final String TEST_SATELLITE_ID = "SAT-01"
    // Używamy niepoprawnego klucza API do testu błędu
    static final String INVALID_API_KEY = "INVALID-KEY"

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
        println "Setting up COMPLEX test for failure case..."

        // 1. Stwórz obiekt konfiguracji
        config = new SystemConfig("sat.example.com", 9001, "https://auth.example.com/token")

        // 2. Stwórz serwisy na podstawie konfiguracji
        authService = new AuthService(config.authServiceUrl)
        connection = new SatelliteConnection(config.satelliteHost, config.satellitePort)

        // 3. Stwórz mocki
        mockRepository = Mock(DataRepository)

        // 4. Stwórz główny serwis ze wszystkimi zależnościami - tym razem z niepoprawnym kluczem
        service = new ReprocessingService(connection, mockRepository, authService, INVALID_API_KEY)
    }

    def cleanup() {
        println "Cleaned up COMPLEX test for failure case."
    }
    // --- KONIEC MASYWNEGO BLOKU POWIELONEGO KODU --------------------------------------

    def "should fail gracefully when authentication fails due to invalid API key"() {
        when: "reprocessing is triggered with a bad key"
        boolean result = service.reprocessDataForSatellite(TEST_SATELLITE_ID)

        then: "the operation should fail"
        result == false

        and: "no data should be saved to the repository"
        0 * mockRepository.saveReprocessedData(_, _)
    }
}