package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
import com.example.sat.http.SimpleHttpClient
import com.example.sat.dto.PhotoTaskStatus
import com.example.sat.dto.PhotoTask
import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoData
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
        simulator.resetState()
        intermediateServer.resetState()

        // Tworzymy aktywny manifest z jednym zadaniem ze statusem TAKEN_AND_QUEUED
        def task = new PhotoTask(taskId: "HEALTHY-IMG", status: PhotoTaskStatus.TAKEN_AND_QUEUED)
        simulator.activeManifest = new PhotoCollectionManifest("TEST-MISSION-1", [task])

        // Umieszczamy odpowiadające zadaniu zdjęcie w storage'u
        simulator.photoStorage["HEALTHY-IMG"] = new PhotoData(photoId: "HEALTHY-IMG", quality: "HIGH", content: "Dane testowe")

        intermediateServer.forceHealthStatus(false)

        when: "the downlink is triggered"
        def response = satClient.sendGenericPost("/downlink", [:])

        then: "the operation succeeds"
        response.statusCode == 200
        intermediateServer.getFileSystemFileCount() == 1
    }

    def "should abort downlink when intermediate server is unhealthy"() {
        given: "the satellite has data but the intermediate server is unhealthy"
        simulator.resetState()
        intermediateServer.resetState()

        // Tworzymy aktywny manifest z zadaniem
        def task = new PhotoTask(taskId: "UNHEALTHY-IMG", status: PhotoTaskStatus.TAKEN_AND_QUEUED)
        simulator.activeManifest = new PhotoCollectionManifest("TEST-MISSION-2", [task])

        // Umieszczamy odpowiadające zadaniu zdjęcie
        simulator.photoStorage["UNHEALTHY-IMG"] = new PhotoData(photoId: "UNHEALTHY-IMG", quality: "HIGH", content: "Dane testowe")

        // WAŻNE: Ustawiamy serwer na unhealthy po wszystkich resetach
        intermediateServer.forceHealthStatus(true)

        when: "the downlink is triggered"
        def response = satClient.sendGenericPost("/downlink", [:])

        then: "the operation fails"
        response.statusCode == 502
        simulator.photoStorage.size() == 1
    }

}