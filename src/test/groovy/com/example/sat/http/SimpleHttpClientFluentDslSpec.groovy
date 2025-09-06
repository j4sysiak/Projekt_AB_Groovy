package com.example.sat.http

import com.github.tomakehurst.wiremock.WireMockServer
import spock.lang.Shared
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.client.WireMock.*

class SimpleHttpClientFluentDslSpec extends Specification {

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

    // =======================================================
    // ===          NASZ NOWY, PŁYNNY "SILNIK" DSL         ===
    // =======================================================

    /**
     * Metoda pomocnicza dla żądań GET.
     * Użycie: get "/path", from: serverConfig
     * @param path Ścieżka, na którą wysyłamy żądanie
     * @param params Mapa parametrów, która MUSI zawierać klucz 'from' z konfiguracją serwera.
     * @return Odpowiedź od klienta.
     */
    private Map get(String path, Map params) {
        def serverConfig = params.from
        if (!serverConfig) {
            throw new IllegalArgumentException("`from` parameter is required, e.g., get '/path', from: serverConfig")
        }

        // Krok 1: Programujemy WireMocka na podstawie `responseTemplate`
        wireMockServer.stubFor(
                get(urlEqualTo(path))
                        .willReturn(aResponse()
                                .withStatus(serverConfig.response.status)
                                .withHeader("Content-Type", "application/json")
                                .withBody(serverConfig.response.body)
                        )
        )

        // Krok 2: Wykonujemy żądanie
        return client.getData(path)
    }


    // ===========================================
    // ===      TEST NAPISANY W NOWYM STYLU      ===
    // ===========================================
    def "should allow testing GET requests with a fluent DSL syntax"() {
        given: "a server configuration described as a map"
        // To jest Twój `server = { ... }`
        def server = [
                // `requestTemplate` nie jest tu potrzebny, bo ścieżka jest w `get`
                response: [
                        status: 200,
                        // To jest Twój "ResponseTemplate"
                        body: '{"status": "UP", "components": ["db", "cache"]}'
                ]
        ]

        when: "a GET request is made using the DSL"
        // To jest Twoje `Response res = get "" from server`
        def response = get("/health", [from: server])

        then: "the client correctly parses the status code and JSON body"
        response.statusCode == 200
        response.json.status == "UP"
        response.json.components.size() == 2
    }
}