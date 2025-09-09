package com.example.sat.test.dsl

import com.example.sat.http.SimpleHttpClient
import com.github.tomakehurst.wiremock.WireMockServer
import groovy.transform.Canonical
import sun.net.httpserver.ServerConfig

import static com.github.tomakehurst.wiremock.client.WireMock.*


/* Usuwamy klasę ServerConfig, bo mapa w zupełności wystarczy
@Canonical
class ServerConfig {
    String host
    String defaultRequestMediaType
    String defaultRespondMediaType
}*/

class RestTestExecutor {
    // PRZYJMUJEMY  { CLOSURE }
    static Map execute(Closure serverConfigClosure, String method, String path) {

        def config = new ServerConfig()
        serverConfigClosure.delegate = config
        serverConfigClosure.call()

        def wireMockServer = new WireMockServer()
        wireMockServer.start()
        try {
            // Używamy baseUrl z WireMocka, a nie z konfiguracji, bo jest dynamiczny
            def client = new SimpleHttpClient(wireMockServer.baseUrl())

            wireMockServer.stubFor(
                    request(method.toUpperCase(), urlEqualTo(path))
                            .willReturn(aResponse()
                                    .withStatus(200) // Na razie na sztywno dla prostoty
                                    .withHeader("Content-Type", config.defaultRespondMediaType)
                                    .withBody('{"status":"OK_FROM_DSL"}') // Na razie na sztywno
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