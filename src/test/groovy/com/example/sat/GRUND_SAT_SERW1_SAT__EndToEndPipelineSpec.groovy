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
    SimpleHttpClient intermediateClient
    SimpleHttpClient satClient
    SatelliteSimulator simulator

    def setup() {
        simulator = new SatelliteSimulator()

        // === POPRAWIONA KONFIGURACJA SERWERÓW ===
        // Kolejność jest ważna, bo serwery zależą od siebie nawzajem.

        // 1. Uruchom serwer docelowy (GRUND), ale na razie bez wiedzy o SAT
        grundServer = new GroundControlServerApp(); grundServer.start(0, "")
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        // 2. Uruchom serwer pośredniczący, który wie gdzie jest GRUND
        intermediateServer = new IntermediateServerApp(); intermediateServer.start(0, grundServerUrl)
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        // 3. Uruchom serwer satelity, który wie gdzie jest SERW1
        satServer = new SatelliteSimulatorServer(simulator, intermediateServerUrl); satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        // 4. TERAZ zaktualizuj GRUND, aby wiedział, gdzie jest SAT
        // To jest kluczowe, bo dopiero teraz znamy satServerUrl
        grundServer.updateSatClientUrl(satServerUrl)

        // 5. Stwórz klientów do komunikacji z serwerami
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

        // === ETAP 2: Wykonanie misji i downlink ===
        when: "SAT executes the mission and downlinks data in batches"
        simulator.simulatePhotoTakingProcess()
        3.times {
            satClient.sendGenericPost("/downlink", [:])
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