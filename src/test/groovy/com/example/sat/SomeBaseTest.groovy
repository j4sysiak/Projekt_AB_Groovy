package com.example.sat

import spock.lang.Specification
import spock.lang.Subject

// Poprawiona, abstrakcyjna klasa bazowa
abstract class SomeBaseTest extends Specification {

    // Wspólne definicje i stałe
    protected static final String SATELLITE_HOST = "sat.internal.net"
    protected static final int SATELLITE_PORT = 9001
    protected static final String DEFAULT_SATELLITE_ID = "SAT-ALPHA-01"

    // ZASOBY - teraz obie zależności są mockami
    // Dzięki temu mamy pełną kontrolę nad ich zachowaniem w każdym teście.
    protected SatelliteConnection connection // To już nie jest @Shared, będzie to mock
    protected DataRepository repository

    @Subject
    protected ReprocessingService service

    // setup() jest wywoływane przed każdą metodą testową.
    def setup() {
        println "--- SomeBaseTest.setup() [Corrected] ---"

        // Tworzymy świeże mocki dla każdej zależności przed każdym testem
        connection = Mock(SatelliteConnection)
        repository = Mock(DataRepository)

        // Tworzymy instancję serwisu z naszymi mockami
        service = new ReprocessingService(connection, repository)

        // Definiujemy domyślne, "szczęśliwe" zachowanie dla naszych mocków.
        // Testy, które potrzebują innego zachowania (np. błędu), będą mogły to nadpisać.
        connection.isConnected() >> true
    }

}