package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.SatelliteSimulatorServer
import com.example.sat.http.SimpleHttpClient
import spock.lang.Shared
import spock.lang.Specification

// Ten test weryfikuje tylko i wyłącznie publiczne API serwera SatelliteSimulatorServer
class SAT_ApiSpec extends Specification {

    @Shared
    SatelliteSimulatorServer server
    @Shared
    SimpleHttpClient client
    @Shared
    SatelliteSimulator simulator

    def setupSpec() {
        simulator = new SatelliteSimulator()
        // Nie potrzebujemy downlinku do innego serwera, więc URL jest pusty
        server = new SatelliteSimulatorServer(simulator, "")
        server.start(0) // Uruchom na losowym wolnym porcie

        def serverUrl = "http://localhost:${server.getPort()}"
        client = new SimpleHttpClient(serverUrl)
    }

    def cleanupSpec() {
        server?.stop()
    }

    // Resetujemy stan symulatora przed każdym testem
    def cleanup() {
        simulator.resetState()
    }

    def "should successfully connect, execute command, and disconnect"() {
        given: "the satellite is ready"
        simulator.batteryLevel = 100.0
        def authToken = "SECRET-API-TOKEN"

        when: "a client executes a standard command sequence"
        def connectResponse = client.connect(authToken)
        def commandResponse = client.sendCommand("CHECK_SYSTEM_HEALTH")
        def disconnectResponse = client.disconnect()

        then: "all HTTP calls are successful"
        connectResponse.statusCode == 200
        commandResponse.statusCode == 200
        disconnectResponse.statusCode == 200

        and: "the responses contain correct data"
        connectResponse.json.status == "OK"
        commandResponse.json.status == "ACK"
        commandResponse.json.message == "STATUS: Thrusters: NOMINAL"
        disconnectResponse.json.status == "OK"

        and: "the simulator state reflects the interactions"
        // Sprawdzamy, czy serwer poprawnie zarządzał stanem połączenia symulatora
        simulator.isConnected() == false // Powinien być rozłączony na końcu
        simulator.commandHistory == ["CHECK_SYSTEM_HEALTH"]
    }

    def "should reject commands if the client is not connected"() {
        when: "a client sends a command without connecting first"
        def commandResponse = client.sendCommand("CHECK_STATUS")

        then: "the server returns a proper error"
        commandResponse.statusCode == 500 // Internal Server Error (bo poleciał wyjątek)

        and: "the error message indicates the problem"
        commandResponse.json.message.contains("Not connected")

        and: "the simulator was not affected"
        simulator.commandHistory.isEmpty()
    }
}