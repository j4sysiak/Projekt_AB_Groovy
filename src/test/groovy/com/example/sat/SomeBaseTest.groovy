package com.example.sat

import spock.lang.Specification
import spock.lang.Subject

// FINALNA WERSJA KLASY BAZOWEJ - centrum konfiguracji dla testów
abstract class SomeBaseTest extends Specification {

    // 1. WSPÓLNE DEFINICJE I STAŁE
    protected static final String DEFAULT_SATELLITE_ID = "SAT-ALPHA-01"
    protected static final String VALID_API_KEY = "VALID-API-KEY"
    protected static final String VALID_AUTH_TOKEN = "TOKEN-FOR-${VALID_API_KEY}"

    // 2. MOCKI DLA WSZYSTKICH ZEWNĘTRZNYCH ZALEŻNOŚCI
    // Dzięki temu mamy pełną kontrolę nad zachowaniem całego otoczenia serwisu.
    protected AuthService authService
    protected SatelliteConnection connection
    protected DataRepository repository

    // 3. OBIEKT TESTOWANY
    @Subject
    protected ReprocessingService service

    // 4. WSPÓLNA KONFIGURACJA W METODZIE setup()
    // Ta metoda jest wywoływana przed każdym testem, zapewniając czyste i izolowane środowisko.
    def setup() {
        println "--- SomeBaseTest.setup() [Final Version] ---"

        // Tworzymy świeże mocki dla każdej zależności
        authService = Mock(AuthService)
        connection = Mock(SatelliteConnection)
        repository = Mock(DataRepository)

        // Tworzymy instancję serwisu, wstrzykując nasze mocki.
        // Używamy domyślnie poprawnego klucza API. Testy, które potrzebują
        // niepoprawnego klucza, mogą go nadpisać w swoim bloku `given`.
        service = new ReprocessingService(connection, repository, authService, VALID_API_KEY)

        // Definiujemy domyślne, "szczęśliwe" zachowanie dla naszych mocków.
        // To jest tzw. "happy path". Testy scenariuszy błędów będą mogły
        // nadpisać te zachowania, aby symulować problemy.
        authService.getAuthToken(VALID_API_KEY) >> VALID_AUTH_TOKEN
        connection.isConnected() >> false // Domyślnie połączenie nie jest aktywne
    }

    def cleanup() {
        println "--- SomeBaseTest.cleanup() ---"
    }
}