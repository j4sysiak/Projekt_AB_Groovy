package com.example.sat

import com.example.sat.fakes.FakeFileSystem
import com.example.sat.fakes.FakeRelationalDB
import com.example.sat.fakes.HttpServerFactory
import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class GRUND_SAT_SERW1_SAT__EndToEndPipelineSpec extends Specification {
    GroundControlServerApp grundServer
    IntermediateServerApp intermediateServer
    SatelliteSimulatorServer satServer
    SimpleHttpClient grundClient
    SimpleHttpClient intermediateClient
    SimpleHttpClient satClient
    SatelliteSimulator simulator

    def setup() {
        // 1. Stwórz wszystkie "fałszywe" komponenty, których będziemy potrzebować
        simulator = new SatelliteSimulator()
        def grundDb = new FakeRelationalDB()
        def intermediateDb = new FakeRelationalDB()
        def intermediateFs = new FakeFileSystem()
        def grundHttp = HttpServerFactory.create()
        def intermediateHttp = HttpServerFactory.create()
        def satHttp = HttpServerFactory.create()

        // 2. Stwórz i uruchom serwery, wstrzykując ich zależności
        satServer = new SatelliteSimulatorServer(simulator, satHttp)
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        grundServer = new GroundControlServerApp(grundDb, grundHttp)
        grundServer.start(0, satServerUrl)
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        intermediateServer = new IntermediateServerApp(intermediateHttp, intermediateFs, intermediateDb)
        intermediateServer.start(0, grundServerUrl)
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        // 3. Dokończ konfigurację połączeń
        satServer.updateDownlinkTarget(intermediateServerUrl)

        // 4. Stwórz klientów do komunikacji
        grundClient = new SimpleHttpClient(grundServerUrl)
        intermediateClient = new SimpleHttpClient(intermediateServerUrl)
        satClient = new SimpleHttpClient(satServerUrl)
    }

    def cleanup() {
        grundServer?.stop()
        intermediateServer?.stop()
        satServer?.stop()
    }


    def "should process a large photo collection mission in batches through the entire pipeline"() {
        given: "a mission to collect photos is requested"
        def missionRequest = [photoCount: 120]
        def conditions = new PollingConditions(timeout: 10)

        // === ETAP 1: Stworzenie i wysłanie misji ===
        when: "The mission is submitted to GRUND"
        def createResponse = grundClient.sendGenericPost("/missions/photo-collection", missionRequest)

        then: "The mission is created in PLANNED state"
        createResponse.statusCode == 202
        def missionId = createResponse.json.missionId
        missionId != null

        when: "The mission is accepted and uploaded"
        grundClient.sendGenericPost("/missions/${missionId}/accept", [:])
        def uploadResponse = grundClient.sendGenericPost("/missions/${missionId}/upload", [:])

        then: "The manifest is successfully stored on the satellite"
        uploadResponse.statusCode == 200
        // Używamy "cierpliwej" asercji, bo upload jest asynchroniczny
        conditions.eventually {
            assert simulator.activeManifest != null
            assert simulator.activeManifest.tasks.size() == 120
            assert simulator.activeManifest.tasks.every { it.status == com.example.sat.dto.PhotoTaskStatus.PENDING }
        }
        Thread.sleep(500) // Pół sekundy powinno w zupełności wystarczyć

        // === ETAP 2: Wykonanie misji i downlink ===
        when: "SAT executes the mission and downlinks data in batches"
        simulator.simulatePhotoTakingProcess()
        3.times {
            satClient.sendGenericPost("/downlink", [:])
            Thread.sleep(500) // Daj pół sekundy na pełne przetworzenie jednego downlinku
        }

        then: "SERW1 has received all the files"
        conditions.eventually {
            assert intermediateServer.getFileSystemFileCount() == 3
        }

        // === ETAP 3: Przetworzenie i przekazanie danych ===
        when: "An operator commands SERW1 to process and forward the data"
        intermediateClient.sendGenericPost("/process", [:])
        def forwardResponse = intermediateClient.sendGenericPost("/forward", [:])

        then: "SERW1 confirms successful forwarding and GRUND's database contains all the photos"
        forwardResponse.statusCode == 200
        conditions.eventually {
            assert grundServer.getDbPhotoCount() == 120
        }
    }
}