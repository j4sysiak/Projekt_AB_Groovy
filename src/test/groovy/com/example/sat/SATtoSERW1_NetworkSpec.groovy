package com.example.sat

import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoData
import com.example.sat.dto.PhotoTask
import com.example.sat.dto.PhotoTaskStatus
import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
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
        given: "the satellite has photos in its manifest and storage"
        simulator.resetState()
        intermediateServer.resetState()

        def task = new PhotoTask(taskId: "TEST_IMG_1", status: PhotoTaskStatus.TAKEN_AND_QUEUED)
        simulator.activeManifest = new PhotoCollectionManifest("MISSION-NETWORK-1", [task])
        simulator.photoStorage["TEST_IMG_1"] = new PhotoData(
                photoId: "TEST_IMG_1",
                quality: 95,
                content: "Simulated image content"
        )

        when: "the downlink process is triggered"
        def response = satClient.sendGenericPost("/downlink", [:])

        then: "the satellite server confirms the downlink"
        response.statusCode == 200

        and: "the intermediate server received and stored the data as a file"
        Thread.sleep(500) // opcjonalne: do czasu aż będzie PollingConditions
        intermediateServer.getFileSystemFileCount() == 1
    }
}
