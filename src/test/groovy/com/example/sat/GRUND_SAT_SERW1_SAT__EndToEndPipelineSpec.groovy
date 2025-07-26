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

        // 1. Uruchom wszystkie serwery i zdobądź ich adresy
        grundServer = new GroundControlServerApp(); grundServer.start(0, "")
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        intermediateServer = new IntermediateServerApp(); intermediateServer.start(0, grundServerUrl)
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        satServer = new SatelliteSimulatorServer(simulator, intermediateServerUrl); satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        // 2. Stwórz JEDNĄ instancję klienta dla każdego serwera
        grundClient = new SimpleHttpClient(grundServerUrl)
        intermediateClient = new SimpleHttpClient(intermediateServerUrl)
        satClient = new SimpleHttpClient(satServerUrl)

        // 3. Zaktualizuj serwer GRUND, aby używał właściwego klienta
        grundServer.updateSatClient(satClient)
    }

    def cleanup() {
        grundServer?.stop()
        intermediateServer?.stop()
        satServer?.stop()
    }

    def "should process a large photo collection mission in batches through the entire pipeline"() {
        given: "a mission to collect photos is requested"
        def missionRequest = [photoCount: 120]
        // Tworzymy obiekt PollingConditions, który da nam cierpliwość
        def conditions = new PollingConditions(timeout: 10, initialDelay: 0.5, factor: 1.25)

        when: "Step 1: The mission is submitted to GRUND, which uploads it to SAT"
        def missionResponse = grundClient.sendGenericPost("/missions/photo-collection", missionRequest)

        then: "The manifest is successfully stored on the satellite"
        missionResponse.statusCode == 202
        simulator.activeManifest != null
        simulator.activeManifest.tasks.size() == 120
        simulator.activeManifest.tasks.every { it.status == com.example.sat.dto.PhotoTaskStatus.PENDING }

        when: "Step 2: SAT executes the mission and downlinks data in batches"
        simulator.simulatePhotoTakingProcess()
        3.times {
            satClient.sendGenericPost("/downlink", [:])
        }

        then: "SERW1 has received all the files"
        // === ZMIANA: Używamy PollingConditions zamiast Thread.sleep ===
        // Spock będzie sprawdzał ten warunek co jakiś czas, aż do skutku (lub timeoutu)
        conditions.eventually {
            assert intermediateServer.getFileSystemFileCount() == 3
        }

        when: "Step 3: An operator commands SERW1 to process and forward the data"
        intermediateClient.sendGenericPost("/process", [:])
        def forwardResponse = intermediateClient.sendGenericPost("/forward", [:])

        then: "SERW1 confirms successful forwarding and GRUND's database contains all the photos"
        forwardResponse.statusCode == 200
        // Tutaj również używamy PollingConditions, aby poczekać na zapis w bazie GRUND
        conditions.eventually {
            assert grundServer.getDbPhotoCount() == 120
        }
    }
}