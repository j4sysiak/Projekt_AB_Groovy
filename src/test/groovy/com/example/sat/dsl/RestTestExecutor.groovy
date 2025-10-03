package com.example.sat.dsl

import com.example.sat.http.SimpleHttpClient
import com.github.tomakehurst.wiremock.WireMockServer

import static com.github.tomakehurst.wiremock.client.WireMock.*

class RestTestExecutor {
    static Map execute(Closure serverConfigClosure, String method, String path) {

        def config = new ServerConfig()
        serverConfigClosure.delegate = config
        serverConfigClosure.call()

        def wireMockServer = new WireMockServer()
        wireMockServer.start()
        try {
            def client = new SimpleHttpClient(wireMockServer.baseUrl())

            wireMockServer.stubFor(
                    request(method.toUpperCase(), urlEqualTo(path))
                            .willReturn(aResponse()
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