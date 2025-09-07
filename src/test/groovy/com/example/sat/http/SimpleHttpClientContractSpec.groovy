package com.example.sat.http

import com.github.tomakehurst.wiremock.WireMockServer
import groovy.json.JsonOutput
import spock.lang.Shared
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.client.WireMock.*

class SimpleHttpClientContractSpec extends Specification {

    // Nasz fałszywy serwer, w pełni kontrolowany przez WireMock
    @Shared WireMockServer wireMockServer

    // Testowany obiekt - nasz klient HTTP
    @Shared SimpleHttpClient client

    // Uruchamiamy serwer przed wszystkimi testami w tej klasie
    def setupSpec() {
        // Uruchom WireMock na losowym, wolnym porcie
        wireMockServer = new WireMockServer()
        wireMockServer.start()

        // Skonfiguruj klienta, aby rozmawiał z naszym serwerem WireMock
        client = new SimpleHttpClient(wireMockServer.baseUrl())
    }

    // Zamykamy serwer po wszystkich testach
    def cleanupSpec() {
        wireMockServer?.stop()
    }

    // Przed każdym testem resetujemy "scenariusz" serwera, żeby testy były niezależne
    def setup() {
        wireMockServer.resetAll()
    }


    def "should correctly parse a successful GET response"() {
        given: "a server that is programmed to return a successful health check"
        // === "Programujemy" zachowanie serwera ===
        // To jest odpowiednik Twojego "TemplateResponse"
        wireMockServer.stubFor(
                get(urlEqualTo("/health"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody('{"status": "UP", "components": ["db", "cache"]}')
                    )
        )



        when: "the client makes a GET request to the health endpoint"
        // To jest odpowiednik Twojego "get from server"
        def response = client.getData("/health")

        then: "the client correctly parses the status code and JSON body"
        response.statusCode == 200
        response.json.status == "UP"
        response.json.components.size() == 2
    }
/*
    def "should correctly handle a POST request and response"() {
        given: "a server that expects a mission and returns an acceptance message"
        // Przygotowujemy dane, które klient wyśle
        def missionRequest = [photoCount: 120]

        // "Programujemy" serwer:
        // "Gdy dostaniesz POST na '/missions' z DOKŁADNIE takim ciałem..."
        wireMockServer.stubFor(post(urlEqualTo("/missions"))
        // Matchowanie po ciele żądania - to jest super potężne!
                .withRequestBody(equalToJson('{"photoCount": 120}'))
                .willReturn(aResponse()
                        .withStatus(202) // Accepted
                        .withHeader("Content-Type", "application/json")
                        .withBody('{"status": "ACCEPTED", "missionId": "MOCK-MISSION-123"}')
                )
        )

        when: "the client sends the mission request"
        def response = client.sendGenericPost("/missions", missionRequest)

        then: "the client receives and parses the acceptance response"
        response.statusCode == 202
        response.json.status == "ACCEPTED"
        response.json.missionId == "MOCK-MISSION-123"

        and: "we can verify that the server received the request exactly once"
        // WireMock pozwala nam też sprawdzić, co się stało na serwerze
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/missions")))
    }

    def "should handle a 404 Not Found error gracefully"() {
        given: "a server that doesn't have the requested endpoint"
        // Nie musimy niczego "stubować". Domyślnie WireMock zwróci 404 dla nieznanych ścieżek.

        when: "the client requests a non-existent resource"
        def response = client.getData("/this/path/does/not/exist")

        then: "the client correctly reports the 404 status code"
        response.statusCode == 404
    }

 */
}