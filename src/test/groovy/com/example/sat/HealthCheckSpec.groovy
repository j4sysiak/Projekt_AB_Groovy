package com.example.sat

import com.example.sat.fakes.FakeFileSystem
import com.example.sat.fakes.FakeRelationalDB
import com.example.sat.fakes.HttpServerFactory
import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
import com.example.sat.http.SimpleHttpClient
import com.example.sat.dto.PhotoTaskStatus
import com.example.sat.dto.PhotoTask
import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoData
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class HealthCheckSpec extends Specification {

    IntermediateServerApp intermediateServer
    SatelliteSimulatorServer satServer
    SatelliteSimulator simulator
    SimpleHttpClient satClient

    def setup() {
        // === ZMIANA: Pełne wstrzykiwanie zależności ===

        // 1. Stwórz wszystkie "fałszywe" komponenty
        simulator = new SatelliteSimulator()

        def intermediateDb = new FakeRelationalDB()
        def intermediateFs = new FakeFileSystem()

        def intermediateHttp = HttpServerFactory.create()
        def satHttp = HttpServerFactory.create()

        // 2. Stwórz i uruchom serwery, wstrzykując ich zależności

        // Serwer pośredniczący nie potrzebuje adresu GRUND w tym teście
        intermediateServer = new IntermediateServerApp(intermediateHttp, intermediateFs, intermediateDb)
        intermediateServer.start(0, "")
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        // Serwer satelity musi wiedzieć, gdzie jest serwer pośredniczący
        satServer = new SatelliteSimulatorServer(simulator, satHttp)
        satServer.start(0)
        satServer.updateDownlinkTarget(intermediateServerUrl) // Konfigurujemy cel po starcie

        // 3. Stwórz klienta do komunikacji z serwerem satelity
        def satServerUrl = "http://localhost:${satServer.getPort()}"
        satClient = new SimpleHttpClient(satServerUrl)
    }

    def cleanup() {
        satServer?.stop()
        intermediateServer?.stop()
    }



    def "should successfully downlink data when intermediate server is healthy"() {
        given: "the satellite has one photo ready and the intermediate server is healthy"
        intermediateServer.forceHealthStatus(false)
        def conditions = new PollingConditions(timeout: 5)

        // Przygotowujemy spójne dane w symulatorze
        def taskId = "HEALTHY-TASK-01"
        def missionId = "TEST-MISSION-1"

        def task = new PhotoTask(taskId: taskId, status: PhotoTaskStatus.TAKEN_AND_QUEUED)
        simulator.activeManifest = new PhotoCollectionManifest(missionId: missionId, tasks: [task])

        simulator.photoStorage[taskId] = new PhotoData(
                photoId: taskId, missionId: missionId,
                content: 'Healthy test data', timestamp: System.currentTimeMillis(), quality: 95
        )

        when: "the downlink is triggered"
        def response = satClient.sendGenericPost("/downlink", [:])

        then: "the operation succeeds and data is transferred"
        response.statusCode == 200
        // Używamy "cierpliwej" asercji zamiast Thread.sleep
        conditions.eventually {
            assert intermediateServer.getFileSystemFileCount() == 1
        }
    }

    def "should abort downlink when intermediate server is unhealthy"() {
        given: "the satellite has one photo ready but the intermediate server is unhealthy"
        intermediateServer.forceHealthStatus(true)

        // Przygotowujemy spójne dane w symulatorze
        def taskId = "UNHEALTHY-TASK-01"
        def missionId = "TEST-MISSION-2"

        def task = new PhotoTask(taskId: taskId, status: PhotoTaskStatus.TAKEN_AND_QUEUED)
        simulator.activeManifest = new PhotoCollectionManifest(missionId: missionId, tasks: [task])

        simulator.photoStorage[taskId] = new PhotoData(
                photoId: taskId, missionId: missionId,
                content: 'Unhealthy test data', timestamp: System.currentTimeMillis(), quality: 98
        )

        when: "the downlink is triggered"
        def response = satClient.sendGenericPost("/downlink", [:])

        then: "the operation fails and no data is transferred"
        response.statusCode == 502

        // Upewniamy się, że dane nie zostały usunięte z satelity
        simulator.photoStorage.size() == 1
        // I że nic nie trafiło do serwera pośredniczącego
        intermediateServer.getFileSystemFileCount() == 0
    }

}