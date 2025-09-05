// src/test/groovy/com/example/sat/server/GroundControlHealthSpec.groovy
package com.example.sat.server

import com.example.sat.http.SimpleHttpClient
import spock.lang.Specification

class GroundControlHealthSpec extends Specification {

    // Uruchomimy prawdziwy serwer i klienta na potrzeby tego testu
    GroundControlServerApp grundServer
    SimpleHttpClient grundClient

    // Metoda setup() jest uruchamiana PRZED każdym testem
    def setup() {
        // 1. Uruchom prawdziwą instancję serwera na losowym porcie
        grundServer = new GroundControlServerApp()
        grundServer.start(0, "") // Nie potrzebujemy URL-a do satelity

        // 2. Stwórz klienta, który będzie z nim rozmawiał
        def serverUrl = "http://localhost:${grundServer.getPort()}"
        grundClient = new SimpleHttpClient(serverUrl)
    }

    // Metoda cleanup() jest uruchamiana PO każdym teście
    def cleanup() {
        // To jest bardzo ważne, żeby zamykać serwer po teście!
        grundServer?.stop()
    }


    def "should return a successful and simple health status when healthy"() {
        given: "the GroundControlServerApp is running and is healthy"
        // Nic więcej nie musimy robić, serwer jest domyślnie zdrowy
        // a wszystko zostało przygotowane w metodzie setup()

        when: "a client requests the /health endpoint"
        def response = grundClient.getData("/health")

        then: "the server responds with status 200 and the correct, simple JSON body"
        response.statusCode == 200
        response.json.status == "UP"

        // Dodajmy asercję, która jawnie sprawdzi, że pole 'components' nie istnieje
        response.json.components == null
    }

    def "should return an unhealthy status when forced"() {
        given: "the server has been forced into an unhealthy state"
        grundServer.forceHealthStatus(true) // Używamy metody do symulowania awarii

        when: "a client requests the /health endpoint"
        def response = grundClient.getData("/health")

        then: "the server responds with status 503 and the correct error body"
        response.statusCode == 503
        response.json.status == "DEGRADED" // Zgodnie z tym, co jest w Twoim kodzie
    }
}