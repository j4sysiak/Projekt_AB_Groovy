package com.example.sat

import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoData
import com.example.sat.dto.PhotoTask
import com.example.sat.dto.PhotoTaskStatus
import com.example.sat.fakes.FakeFileSystem
import com.example.sat.fakes.FakeRelationalDB
import com.example.sat.fakes.HttpServerFactory
import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class SATtoSERW1_NetworkSpec extends Specification {

    IntermediateServerApp intermediateServer
    SatelliteSimulatorServer satServer
    SatelliteSimulator simulator
    SimpleHttpClient satClient

    def setup() {
        // === ZMIANA: Pełne wstrzykiwanie zależności ===

        // 1. Stwórz testowe implementacje zależności
        simulator = new SatelliteSimulator()

        def intermediateDb = new FakeRelationalDB()
        def intermediateFs = new FakeFileSystem()

        def intermediateHttp = HttpServerFactory.create()
        def satHttp = HttpServerFactory.create()

        // 2. Stwórz i uruchom serwery, wstrzykując ich zależności

        intermediateServer = new IntermediateServerApp(intermediateHttp, intermediateFs, intermediateDb)
        intermediateServer.start(0, "")
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        satServer = new SatelliteSimulatorServer(simulator, satHttp)
        satServer.start(0)
        // Dokończ konfigurację połączenia
        satServer.updateDownlinkTarget(intermediateServerUrl)

        // 3. Stwórz klienta do komunikacji z serwerem satelity
        def satServerUrl = "http://localhost:${satServer.getPort()}"
        satClient = new SimpleHttpClient(satServerUrl)
    }

    def cleanup() {
        satServer?.stop()
        intermediateServer?.stop()
    }

    def "satellite should successfully downlink data to the intermediate server"() {
        given: "the satellite has photos in its manifest and storage"
        // Nie potrzebujemy już resetState(), bo setup() tworzy świeże instancje
        def conditions = new PollingConditions(timeout: 5)

        def taskId = "TEST_IMG_1"
        def missionId = "MISSION-NETWORK-1"

        def task = new PhotoTask(taskId: taskId, status: PhotoTaskStatus.TAKEN_AND_QUEUED)
        simulator.activeManifest = new PhotoCollectionManifest(missionId: missionId, tasks: [task])

        // Uzupełniamy dane testowe o brakujące pola
        simulator.photoStorage[taskId] = new PhotoData(
                photoId: taskId,
                missionId: missionId,
                timestamp: System.currentTimeMillis(),
                quality: 95,
                content: "Simulated image content"
        )

        when: "the downlink process is triggered"
        def response = satClient.sendGenericPost("/downlink", [:])

        then: "the satellite server confirms the downlink"
        response.statusCode == 200

        and: "the intermediate server received and stored the data as a file"
        // Używamy PollingConditions zamiast Thread.sleep
        conditions.eventually {
            assert intermediateServer.getFileSystemFileCount() == 1
        }
    }
}
