package com.example.sat

import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.IntermediateServerApp
import com.example.sat.utils.http.SimpleHttpClient
import spock.lang.Shared
import spock.lang.Specification

class Server1ToGrundHealthCheckSpec extends Specification {

    @Shared GroundControlServerApp grundServer
    @Shared IntermediateServerApp intermediateServer
    @Shared SimpleHttpClient intermediateClient

    def setupSpec() {
        // 1. Uruchom serwer docelowy (GRUND)
        grundServer = new GroundControlServerApp()
        grundServer.start(0, "") // URL satelity jest niepotrzebny w tym teście
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        // 2. Uruchom serwer źródłowy (SERW1), mówiąc mu, gdzie jest GRUND
        intermediateServer = new IntermediateServerApp()
        intermediateServer.start(0, grundServerUrl)
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        // 3. Stwórz klienta do sterowania serwerem SERW1
        intermediateClient = new SimpleHttpClient(intermediateServerUrl)
    }

    def cleanupSpec() {
        grundServer?.stop()
        intermediateServer?.stop()
    }

    def cleanup() {
        grundServer.forceHealthStatus(false)
        intermediateServer.resetState()
    }

    def "should successfully forward data when ground control is healthy"() {
        given: "intermediate server has data to forward and ground control is healthy"
        intermediateServer.database.insertPhoto([photoId: "HEALTHY-FWD"])
        grundServer.forceHealthStatus(false) // Upewniamy się, że jest zdrowy

        when: "the forward process is triggered"
        def response = intermediateClient.sendGenericPost("/forward", [:])

        then: "the operation succeeds"
        response.statusCode == 200
        response.json.status == "OK"
    }

    def "should abort forwarding when ground control is unhealthy"() {
        given: "intermediate server has data to forward but ground control is unhealthy"
        intermediateServer.database.insertPhoto([photoId: "UNHEALTHY-FWD"])
        grundServer.forceHealthStatus(true) // Symulujemy awarię

        when: "the forward process is triggered"
        def response = intermediateClient.sendGenericPost("/forward", [:])

        then: "the operation fails with a proper error"
        response.statusCode == 502 // Bad Gateway
        response.json.status == "ERROR"
        response.json.message.contains("unhealthy")
    }
}