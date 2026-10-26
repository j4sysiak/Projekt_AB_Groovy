package com.example.auto.http

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
}