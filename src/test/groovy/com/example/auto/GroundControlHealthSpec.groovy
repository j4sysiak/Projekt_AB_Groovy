// src/test/groovy/com/example/auto/server/GroundControlHealthSpec.groovy
package com.example.auto.server

import com.example.auto.fakes.FakeRelationalDB
import com.example.auto.fakes.HttpServerFactory
import com.example.auto.http.SimpleHttpClient
import spock.lang.Specification

class GroundControlHealthSpec extends Specification {

    // Uruchomimy prawdziwy serwer i klienta na potrzeby tego testu
    AutoControlServerApp platzServer
    SimpleHttpClient platzClient

    def setup() {
        // === ZMIANA: Nowy, poprawny sposób tworzenia serwera ===

        // 1. Stwórz testowe ("fałszywe") implementacje zależności
        def fakeDb = new FakeRelationalDB()
        def fakeHttpServer = HttpServerFactory.create()

        // 2. Wstrzyknij te zależności do konstruktora serwera
        platzServer = new AutoControlServerApp(fakeDb, fakeHttpServer)

        // 3. Uruchom serwer na losowym porcie
        platzServer.start(0, "")

        // 4. Stwórz klienta, który będzie z nim rozmawiał
        def serverUrl = "http://localhost:${platzServer.getPort()}"
        platzClient = new SimpleHttpClient(serverUrl)
    }

    // Metoda cleanup() jest uruchamiana PO każdym teście
    def cleanup() {
        // To jest bardzo ważne, żeby zamykać serwer po teście!
        platzServer?.stop()
    }


    // === Metody testowe pozostają BEZ ZMIAN! ===
    // Dobra architektura sprawia, że same testy nie muszą być modyfikowane,
    // a jedynie sposób, w jaki przygotowujemy testowany obiekt.

    def "should return a successful and simple health status when healthy"() {
        given: "the AutoControlServerApp is running and is healthy"
        // Nic więcej nie musimy robić

        when: "a client requests the /health endpoint"
        def response = platzClient.getData("/health")

        then: "the server responds with status 200 and the correct, simple JSON body"
        response.statusCode == 200
        response.json.status == "UP"
        response.json.components == null
    }

    def "should return an unhealthy status when forced"() {
        given: "the server has been forced into an unhealthy state"
        platzServer.forceHealthStatus(true)

        when: "a client requests the /health endpoint"
        def response = platzClient.getData("/health")

        then: "the server responds with status 503 and the correct error body"
        response.statusCode == 503
        response.json.status == "DEGRADED"
    }
}