package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
import com.example.sat.utils.http.SimpleHttpClient
import spock.lang.Specification

class HealthCheckSpec extends Specification {

    // Zamiast @Shared, serwery będą tworzone na nowo dla każdego testu
    IntermediateServerApp intermediateServer
    SatelliteSimulatorServer satServer
    SatelliteSimulator simulator
    SimpleHttpClient satClient

    // Metoda setup jest wołana przed KAŻDYM testem
    def setup() {
        intermediateServer = new IntermediateServerApp()
        intermediateServer.start(0, "") // Adres GRUND nie jest potrzebny
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        simulator = new SatelliteSimulator()
        satServer = new SatelliteSimulatorServer(simulator, intermediateServerUrl)
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        satClient = new SimpleHttpClient(satServerUrl)
    }

    // Metoda cleanup jest wołana po KAŻDYM teście
    def cleanup() {
        satServer?.stop()
        intermediateServer?.stop()
    }

    def "should successfully downlink data when intermediate server is healthy"() {
        given: "the satellite has data and the intermediate server is healthy"
        simulator.photoQueue.add([photoId: "HEALTHY-IMG"])
        intermediateServer.forceHealthStatus(false)

        when: "the downlink is triggered"
        def response = satClient.sendGenericPost("/downlink", [:])

        then: "the operation succeeds"
        response.statusCode == 200
        intermediateServer.getFileSystemFileCount() == 1
    }

    def "should abort downlink when intermediate server is unhealthy"() {
        given: "the satellite has data but the intermediate server is unhealthy"
        simulator.photoQueue.add([photoId: "UNHEALTHY-IMG"])
        intermediateServer.forceHealthStatus(true)

        when: "the downlink is triggered"
        def response = satClient.sendGenericPost("/downlink", [:])

        then: "the operation fails"
        response.statusCode == 502
        simulator.photoQueue.size() == 1
    }
}