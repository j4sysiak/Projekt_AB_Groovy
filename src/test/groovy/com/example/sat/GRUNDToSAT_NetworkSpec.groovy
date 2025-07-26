package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.SatelliteSimulatorServer
import com.example.sat.http.SimpleHttpClient
import spock.lang.Specification

class GRUNDToSAT_NetworkSpec extends Specification {
    GroundControlServerApp grundServer
    SatelliteSimulatorServer satServer
    SatelliteSimulator simulator
    SimpleHttpClient grundClient

    def setup() {
        simulator = new SatelliteSimulator()
        satServer = new SatelliteSimulatorServer(simulator, "")
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        grundServer = new GroundControlServerApp()
        grundServer.start(0, satServerUrl)
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        grundClient = new SimpleHttpClient(grundServerUrl)
    }

    def cleanup() {
        grundServer?.stop()
        satServer?.stop()
    }

    def "ground server should successfully request and upload a photo collection mission"() {
        given: "both servers are running"
        simulator.batteryLevel = 100.0

        when: "a user sends a mission request to the ground control server"
        def missionRequest = [photoCount: 5]
        def response = grundClient.sendGenericPost("/missions/photo-collection", missionRequest)

        then: "the ground server accepts the mission and confirms upload to satellite"
        response.statusCode == 202
        def manifest = response.json
        manifest.status == "UPLOADED"
        manifest.tasks.size() == 5

        and: "the satellite simulator has stored the manifest"
        simulator.activeManifest.missionId == manifest.missionId
    }
}