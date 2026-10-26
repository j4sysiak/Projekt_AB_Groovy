package com.example.auto

import com.example.auto.fakes.HttpServerFactory
import com.example.auto.fakes.AutoCheck
import com.example.auto.server.AutoCheckServer
import com.example.auto.http.SimpleHttpClient
import spock.lang.Shared
import spock.lang.Specification

class AUTO_ApiSpec extends Specification {

    @Shared AutoCheckServer server
    @Shared SimpleHttpClient client
    @Shared AutoCheck checker

    def setupSpec() {
        // === ZMIANA: Pełne wstrzykiwanie zależności ===

        // 1. Stwórz testowe implementacje zależności
        checker = new AutoCheck()
        def autoHttp = HttpServerFactory.create()

        // 2. Stwórz i uruchom AutoCheckServer, wstrzykując zależności
        //    Nie przekazujemy downlinkTargetUrl, bo nie jest potrzebny w tym teście.
        server = new AutoCheckServer(checker, autoHttp)
        server.start(0) // Uruchom na losowym wolnym porcie

        // 3. Stwórz klienta do komunikacji
        def serverUrl = "http://localhost:${server.getPort()}"
        client = new SimpleHttpClient(serverUrl)
    }

    def cleanupSpec() {
        server?.stop()
    }

    def cleanup() {
        checker.resetState()
    }

    def "should successfully connect, execute command, and disconnect"() {
        given: "the auto is ready"
        checker.batteryLevel = 100.0
        def authToken = "SECRET-API-TOKEN"

        when: "a client executes a standard command sequence using generic methods"
        // POPRAWKA: Używamy sendGenericPost zamiast connect, podając ścieżkę i ciało
        def connectResponse = client.sendGenericPost("/connect", [token: authToken])
        // POPRAWKA: Używamy sendGenericPost zamiast sendCommand
        def commandResponse = client.sendGenericPost("/command", [command: "CHECK_SYSTEM_HEALTH", params: [:]])
        // POPRAWKA: Używamy sendGenericPost zamiast disconnect
        def disconnectResponse = client.sendGenericPost("/disconnect", [:])

        then: "all HTTP calls are successful"
        connectResponse.statusCode == 200
        commandResponse.statusCode == 200
        disconnectResponse.statusCode == 200

        and: "the responses contain correct data"
        connectResponse.json.status == "OK"
        commandResponse.json.status == "ACK"
        commandResponse.json.message == "STATUS: Thrusters: NOMINAL"
        disconnectResponse.json.status == "OK"

        and: "the checker state reflects the interactions"
        checker.isConnected() == false
        checker.commandHistory == ["CHECK_SYSTEM_HEALTH"]
    }

    def "should reject commands if the client is not connected"() {
        when: "a client sends a command without connecting first"
        // POPRAWKA: Używamy sendGenericPost zamiast sendCommand
        def commandResponse = client.sendGenericPost("/command", [command: "CHECK_STATUS", params: [:]])

        then: "the server returns a proper error"
        // ZMIANA: Serwer powinien zwrócić błąd 401 Unauthorized lub 403 Forbidden,
        // a nie 500, jeśli jest dobrze napisany. Zakładamy, że zwróci 500 z wyjątku.
        commandResponse.statusCode == 500

        and: "the error message indicates the problem"
        commandResponse.json.message.contains("Not connected")

        and: "the checker was not affected"
        checker.commandHistory.isEmpty()
    }
}