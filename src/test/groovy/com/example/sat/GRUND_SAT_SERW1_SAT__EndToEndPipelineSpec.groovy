package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
import com.example.sat.http.SimpleHttpClient
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class GRUND_SAT_SERW1_SAT__EndToEndPipelineSpec extends Specification {
    GroundControlServerApp grundServer
    IntermediateServerApp intermediateServer
    SatelliteSimulatorServer satServer
    SimpleHttpClient grundClient
    SimpleHttpClient satClient
    SatelliteSimulator simulator

    def setup() {
        simulator = new SatelliteSimulator()

        // Uruchamiamy serwery w logicznej kolejności zależności
        intermediateServer = new IntermediateServerApp(); intermediateServer.start(0, "")
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        satServer = new SatelliteSimulatorServer(simulator, intermediateServerUrl); satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        grundServer = new GroundControlServerApp(); grundServer.start(0, satServerUrl)
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        grundClient = new SimpleHttpClient(grundServerUrl)
        satClient = new SimpleHttpClient(satServerUrl)
    }

    def cleanup() {
        grundServer?.stop(); intermediateServer?.stop(); satServer?.stop()
    }

    def "should process a large photo collection mission in batches through the entire pipeline"() {
        given: "a mission to collect 120 photos"
        def missionRequest = [photoCount: 120]
        def conditions = new PollingConditions(timeout: 15)

        when: "Step 1: The mission is submitted to GRUND, which uploads it to SAT"
        def missionResponse = grundClient.sendGenericPost("/missions/photo-collection", missionRequest)

        then: "The manifest is successfully stored on the satellite"
        missionResponse.statusCode == 202
        simulator.activeManifest != null
        simulator.activeManifest.tasks.size() == 120
        simulator.activeManifest.tasks.every { it.status == com.example.sat.dto.PhotoTaskStatus.PENDING }

        when: "Step 2: SAT executes the mission"
        simulator.simulatePhotoTakingProcess()

        then: "All photos are taken and queued for downlink"
        simulator.photoStorage.size() == 120
        simulator.activeManifest.tasks.every { it.status == com.example.sat.dto.PhotoTaskStatus.TAKEN_AND_QUEUED }

        when: "Step 3: Multiple downlink windows occur"
        3.times {
            satClient.sendGenericPost("/downlink", [:])
        }

        then: "SERW1 eventually receives all 3 data packages"
        conditions.eventually {
            assert intermediateServer.getFileSystemFileCount() == 3
        }

        when: "Step 4: SERW1 processes and forwards the data"
        def intermediateClient = new SimpleHttpClient("http://localhost:${intermediateServer.getPort()}")
        intermediateClient.sendGenericPost("/process", [:])
        intermediateClient.sendGenericPost("/forward", [:])

        then: "GRUND's database eventually contains all the photos"
        conditions.eventually {
            assert grundServer.getDbPhotoCount() == 120
        }
    }
}