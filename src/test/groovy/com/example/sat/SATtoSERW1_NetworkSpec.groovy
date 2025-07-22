package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
import com.example.sat.http.SimpleHttpClient
import spock.lang.Specification

class SATtoSERW1_NetworkSpec extends Specification {

    IntermediateServerApp intermediateServer
    SatelliteSimulatorServer satServer
    SatelliteSimulator simulator
    SimpleHttpClient satClient

    def setup() {
        intermediateServer = new IntermediateServerApp()
        intermediateServer.start(0, "")
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        simulator = new SatelliteSimulator()
        satServer = new SatelliteSimulatorServer(simulator, intermediateServerUrl)
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        satClient = new SimpleHttpClient(satServerUrl)
    }

    def cleanup() {
        satServer?.stop()
        intermediateServer?.stop()
    }

    def "satellite should successfully downlink data to the intermediate server"() {
        given: "the satellite has photos in its queue"
        simulator.resetState()
        intermediateServer.resetState()
        simulator.photoQueue.add([photoId: "TEST_IMG_1"])

        when: "the downlink process is triggered"
        def response = satClient.sendGenericPost("/downlink", [:])

        then: "the satellite server confirms the downlink"
        response.statusCode == 200

        and: "the intermediate server received and stored the data as a file"
        Thread.sleep(500)
        intermediateServer.getFileSystemFileCount() == 1
    }
}