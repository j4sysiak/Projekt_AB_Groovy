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
import com.example.sat.dto.PhotoTaskStatus
import spock.util.concurrent.PollingConditions

class PhotoCollectionPipelineSpec extends Specification {

    GroundControlServerApp grundServer
    IntermediateServerApp intermediateServer
    SatelliteSimulatorServer satServer
    SimpleHttpClient grundClient
    SimpleHttpClient intermediateClient
    SimpleHttpClient satClient
    SatelliteSimulator simulator

    // === ZMIANA: Pełne wstrzykiwanie zależności dla wszystkich serwerów ===
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

    def "should process a photo collection mission in batches through the entire pipeline"() {
        given: "a mission to collect 120 photos"
        def missionRequest = [photoCount: 120]
        def conditions = new PollingConditions(timeout: 15, delay: 0.5)

        // ETAP 1: Stworzenie, akceptacja i wysłanie misji
        when: "the mission is created, accepted and submitted to GRUND"
        def createResponse = grundClient.sendGenericPost("/missions/photo-collection", missionRequest)
        def missionId = createResponse.json.missionId
        grundClient.sendGenericPost("/missions/${missionId}/accept", [:])
        def uploadResponse = grundClient.sendGenericPost("/missions/${missionId}/upload", [:])

        then: "SAT receives the manifest"
        uploadResponse.statusCode == 200
        conditions.eventually {
            assert simulator.activeManifest != null
            assert simulator.activeManifest.tasks.size() == 120
        }

        // ETAP 2: Wykonanie misji
        when: "the satellite takes all the photos"
        simulator.simulatePhotoTakingProcess()

        then: "all photos are taken and queued for downlink"
        simulator.activeManifest.tasks.count { it.status == PhotoTaskStatus.TAKEN_AND_QUEUED } == 120

        // ETAP 3: Downlink
        when: "three downlink opportunities occur"
        3.times {
            satClient.sendGenericPost("/downlink", [:])
            Thread.sleep(200)
        }

        then: "SERW1 has received all the files"
        conditions.eventually {
            assert intermediateServer.getFileSystemFileCount() == 3
        }

        // ETAP 4: Przetworzenie i przekazanie
        when: "SERW1 is commanded to process and forward the data"
        intermediateClient.sendGenericPost("/process", [:])
        def forwardResponse = intermediateClient.sendGenericPost("/forward", [:])

        then: "SERW1 confirms forwarding and GRUND's database contains all photos"
        forwardResponse.statusCode == 200
        conditions.eventually {
            assert grundServer.getDbPhotoCount() == 120
        }
    }
}