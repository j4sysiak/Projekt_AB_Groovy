package com.example.sat.dsl2

import com.example.sat.http.SimpleHttpClient
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock

class RestTestExecutor {
    static Map executeWithConfig(ServerConfig config, String method, String path) {
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
                                    .withStatus(config.response.status)
                                    .withHeader("Content-Type", config.response.mediaType)
                                    .withBody(config.response.body)
                            )
            )

            if (method.toUpperCase() == "GET") {
                return client.getData(path)
            } else {
                return client.sendGenericPost(path, config.request?.body ?: [:])
            }
        } finally {
            wireMockServer.stop()
        }
    }
}
