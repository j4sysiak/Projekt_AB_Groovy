// src/test/groovy/com/example/sat/http/SimpleHttpClientDslSpec.groovy
package com.example.sat.http

import com.example.sat.test.WireMockStubs
import com.github.tomakehurst.wiremock.WireMockServer
import spock.lang.Shared
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.client.WireMock.*

class SimpleHttpClientDslSpec extends Specification {

    @Shared WireMockServer wireMockServer
    @Shared SimpleHttpClient client

    def setupSpec() {
        wireMockServer = new WireMockServer()
        wireMockServer.start()
        client = new SimpleHttpClient(wireMockServer.baseUrl())
    }

    def cleanupSpec() {
        wireMockServer?.stop()
    }

    def setup() {
        wireMockServer.resetAll()
    }

    // === METODA POMOCNICZA (NASZ "KUCHARZ") ===
    // Ta metoda przyjmuje "przepis" (mapę `setup`) i wykonuje całą pracę.
    private Map executeRequest(Map testSetup) {
        // Krok 1: Programujemy WireMocka na podstawie "ResponseTemplate"
        wireMockServer.stubFor(
                // Używamy danych z `testSetup` do skonfigurowania żądania
                request(testSetup.request.method, urlEqualTo(testSetup.request.path))
                        .willReturn(aResponse()
                        // Używamy danych z `testSetup` do skonfigurowania odpowiedzi
                                .withStatus(testSetup.response.status)
                                .withHeader("Content-Type", "application/json")
                                .withBody(testSetup.response.body)
                        )
        )

        // Krok 2: Wykonujemy żądanie na kliencie
        switch (testSetup.request.method) {
            case "GET":
                return client.getData(testSetup.request.path)
            case "POST":
                return client.sendGenericPost(testSetup.request.path, testSetup.request.body ?: [:])
            default:
                throw new IllegalArgumentException("Unsupported method: ${testSetup.request.method}")
        }
    }


    // === TEST NAPISANY W STYLU DSL ===
    def "should allow testing with a descriptive setup block"() {
        given: "a declarative setup for a test scenario"
        // To jest odpowiednik Twojego `server = { ... }`
        // Tworzymy mapę, która w pełni opisuje naszą interakcję.
        def scenario = [
                description: "Successful health check",
                request: [
                        method: "GET",
                        path: "/health"
                        // To jest Twój "RequestTemplate"
                ],
                response: [
                        status: 200,
                        body: '{"status": "UP"}'
                        // To jest Twój "ResponseTemplate"
                ]
        ]

        when: "the interaction is executed"
        // To jest odpowiednik Twojego `Response res = get "" from server`
        // Przekazujemy nasz "przepis" do wykonania.
        def response = executeRequest(scenario)

        then: "the client correctly handles the response"
        response.statusCode == 200
        response.json.status == "UP"
    }

    def "should handle POST requests described in a setup block"() {
        given: "a declarative setup for a POST scenario"
        def scenario = [
                description: "Accepting a mission",
                request: [
                        method: "POST",
                        path: "/missions/M-123/accept",
                        body: [action: "accept"] // Przykładowe ciało żądania
                ],
                response: [
                        status: 200,
                        body: '{"message": "Mission accepted."}'
                ]
        ]

        when: "the interaction is executed"
        def response = executeRequest(scenario)

        then: "the client correctly handles the POST response"
        response.statusCode == 200
        response.json.message == "Mission accepted."
    }
}