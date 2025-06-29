package com.example.sat

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

// Abstrakcyjna klasa bazowa dla testów funkcjonalnych Projektu_AB (reprocesowanie)
abstract class SomeBaseTest extends Specification {

    // 1. WSPÓLNE DEFINICJE I STAŁE
    // Przeniesione z poszczególnych testów
    protected static final String SATELLITE_HOST = "sat.internal.net"
    protected static final int SATELLITE_PORT = 9001
    protected static final String DEFAULT_SATELLITE_ID = "SAT-ALPHA-01"

    // 2. WSPÓLNE ZASOBY
    // @Shared oznacza, że obiekt będzie współdzielony przez wszystkie metody testowe w klasie dziedziczącej.
    // Jest tworzony raz w setupSpec().
    @Shared
    SatelliteConnection connection

    // Te obiekty będą tworzone na nowo dla każdej metody testowej w setup()
    protected DataRepository mockRepository

    @Subject // @Subject wskazuje, który obiekt jest głównym przedmiotem testów
    protected ReprocessingService service

    // 3. WSPÓLNE METODY CYKLU ŻYCIA
    // setupSpec() jest wywoływane raz przed wszystkimi testami w specyfikacji.
    // Idealne miejsce na inicjalizację "drogich", współdzielonych zasobów.
    def setupSpec() {
        println "--- SomeBaseTest.setupSpec() ---"
        connection = new SatelliteConnection(SATELLITE_HOST, SATELLITE_PORT)
        connection.connect()
    }

    // cleanupSpec() jest wywoływane raz po wszystkich testach w specyfikacji.
    // Sprzątamy po współdzielonych zasobach.
    def cleanupSpec() {
        println "--- SomeBaseTest.cleanupSpec() ---"
        if (connection?.isConnected()) {
            connection.disconnect()
        }
    }

    // setup() jest wywoływane przed każdą metodą testową (feature method).
    // Zapewnia izolację testów przez tworzenie świeżych instancji mocków i serwisu.
    def setup() {
        println "--- SomeBaseTest.setup() ---"
        // Używamy Mock, bo będziemy weryfikować interakcje (np. czy metoda została wywołana)
        mockRepository = Mock(DataRepository)
        service = new ReprocessingService(connection, mockRepository)
    }

    // cleanup() jest wywoływane po każdej metodzie testowej.
    def cleanup() {
        println "--- SomeBaseTest.cleanup() ---"
        // W tym przypadku nie ma potrzeby sprzątania, ale pokazujemy, gdzie to zrobić.
    }
}