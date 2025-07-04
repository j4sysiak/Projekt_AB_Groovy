package com.example.sat

import spock.lang.Specification

// Wersja Ostateczna: Używamy Spy na obu niestabilnych zależnościach.
abstract class SomeBaseTest extends Specification {

    // Będą one dostępne dla wszystkich testów dziedziczących.
    protected static final String DEFAULT_SATELLITE_ID = "SAT-ALPHA-01"
    protected static final String VALID_API_KEY = "VALID-API-KEY"
    protected static final String INVALID_API_KEY = "INVALID-KEY"
    protected static final String VALID_AUTH_TOKEN = "TOKEN-FOR-${VALID_API_KEY}"

    protected AuthService authService
    protected SatelliteConnection connection
    protected DataRepository repository

    protected ReprocessingService service

    def setup() {
        println "--- SomeBaseTest.setup() ---"

        // Sprawdzony wzorzec dla AuthService
        authService = Spy(AuthServiceImpl, constructorArgs: ["http://dummy.auth.url"])

        // Stosujemy ten sam wzorzec dla SatelliteConnection
        connection = Spy(SatelliteConnectionImpl, constructorArgs: ["dummy.host", 1234])

        // DataRepository jest interfejsem, więc Mock jest dla niego idealny i stabilny.
        repository = Mock(DataRepository)
    }
}