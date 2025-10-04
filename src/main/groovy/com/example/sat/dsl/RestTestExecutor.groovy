package com.example.sat.dsl

import com.example.sat.http.SimpleHttpClient
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock

class RestTestExecutor {
    static Map execute(Closure serverConfigClosure, String method, String path) {

        def config = new ServerConfig()
        serverConfigClosure.delegate = config
        serverConfigClosure.call()  // <-- Tutaj pola `host` i `defaultRespondMediaType` są ustawiane w obiekcie `config`

        // Uruchamiamy NOWY, PUSTY serwer WireMock w locie
        def wireMockServer = new WireMockServer()
        wireMockServer.start() // <-- Startuje na LOSOWYM, wolnym porcie
        try {
            // Tworzymy klienta, który wskazuje na adres TEGO TYMCZASOWEGO SERWERA
            def client = new SimpleHttpClient(wireMockServer.baseUrl())

            // ... programujemy WireMocka i wykonujemy żądanie ...
            wireMockServer.stubFor(
                    WireMock.request(method.toUpperCase(), WireMock.urlEqualTo(path))
                            .willReturn(WireMock.aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", config.defaultRespondMediaType)
                                    .withBody('{"status":"OK_FROM_DSL"}')
                            )
            )

            if (method.toUpperCase() == "GET") {
                return client.getData(path)
            } else {
                return client.sendGenericPost(path, [:])
            }
        } finally {
            wireMockServer.stop()
        }
    }
}