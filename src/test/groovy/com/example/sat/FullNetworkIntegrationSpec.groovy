package com.example.sat

import com.example.sat.fakes.HttpGroundControlClient
import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.SatelliteSimulatorServer
import spock.lang.Shared
import spock.lang.Specification
import spark.Spark

class FullNetworkIntegrationSpec extends Specification {

    @Shared SatelliteSimulatorServer server
    @Shared HttpGroundControlClient client
    @Shared SatelliteSimulator simulator

    def setupSpec() {
        simulator = new SatelliteSimulator()
        server = new SatelliteSimulatorServer(simulator)
        server.start(0)
        def serverUrl = "http://localhost:${Spark.port()}"
        client = new HttpGroundControlClient(serverUrl)
    }

    def cleanupSpec() {
        server.stop()
    }

    def cleanup() {
        simulator.resetState()
    }

    def "client should be able to execute a full photo mission over the network"() {
        given: "the simulator is in a healthy state"
        simulator.batteryLevel = 100.0
        def authToken = "SECRET-NETWORK-TOKEN"

        when: "the client executes a full mission sequence via HTTP"
        def connectResponse = client.connect(authToken)
        def commandResponse = client.sendCommand("TAKE_PHOTOS", [numberOfPhotos: 3, quality: "HIGH"])
        def downlinkResponse = client.downlinkData()
        def disconnectResponse = client.disconnect()

        then: "all network calls were successful"
        connectResponse.statusCode == 200
        commandResponse.statusCode == 200
        downlinkResponse.statusCode == 200
        disconnectResponse.statusCode == 200

        and: "the connection was established correctly"
        connectResponse.json.status == "OK" // <-- NOWA ASERCJA

        and: "the command was acknowledged and data was received"
        def commandJson = commandResponse.json
        commandJson.status == "ACK"
        commandJson.message.contains("3 photos taken")

        def photosJson = downlinkResponse.json as List
        photosJson.size() == 3
        photosJson[0].quality == "HIGH"
    }

    def "server should return a proper error if client tries to send command without connecting"() {
        when: "the client sends a command without connecting first"
        def commandResponse = client.sendCommand("CHECK_STATUS")

        then: "the server returns an error"
        commandResponse.statusCode == 500

        and: "the error message indicates the problem"
        commandResponse.json.message.contains("Not connected")
    }
}