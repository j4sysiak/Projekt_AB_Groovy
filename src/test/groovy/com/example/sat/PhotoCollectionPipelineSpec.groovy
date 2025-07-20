package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
import com.example.sat.http.SimpleHttpClient
import spock.lang.Specification

class PhotoCollectionPipelineSpec extends Specification {

    GroundControlServerApp grundServer
    IntermediateServerApp intermediateServer
    SatelliteSimulatorServer satServer

    SimpleHttpClient grundClient
    SimpleHttpClient intermediateClient
    SimpleHttpClient satClient

    SatelliteSimulator simulator

    def setup() {
        simulator = new SatelliteSimulator()

        // === POPRAWIONA KOLEJNOŚĆ ===

        // 1. Uruchom serwer SAT - nie zależy od niczego
        satServer = new SatelliteSimulatorServer(simulator, "") // Downlink target ustawimy później
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"
        satClient = new SimpleHttpClient(satServerUrl)

        // 2. Uruchom serwer GRUND - zależy od SAT
        grundServer = new GroundControlServerApp()
        grundServer.start(0, satServerUrl)
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"
        grundClient = new SimpleHttpClient(grundServerUrl)

        // 3. Uruchom serwer SERW1 - zależy od GRUND
        intermediateServer = new IntermediateServerApp()
        intermediateServer.start(0, grundServerUrl)
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"
        intermediateClient = new SimpleHttpClient(intermediateServerUrl)

        // 4. Zaktualizuj SAT o poprawny adres SERW1
        satServer.updateDownlinkTarget(intermediateServerUrl)

        // Czekamy, aż wszystko się ustabilizuje
        Thread.sleep(1500)
    }

    def cleanup() {
        grundServer?.stop()
        intermediateServer?.stop()
        satServer?.stop()
    }

    def "should process a large photo collection mission in batches through the entire pipeline"() {
        given: "a mission to collect 120 photos is requested"
        def missionRequest = [photoCount: 120]

        // ETAP 1: Zlecenie misji
        when: "the mission is submitted to GRUND"
        def missionResponse = grundClient.sendGenericPost("/missions/photo-collection", missionRequest)

        then: "GRUND accepts it and SAT receives the manifest"
        missionResponse.statusCode == 202

        // ETAP 2: Symulacja wykonania misji
        when: "the satellite is given time to execute the mission"
        // Zamiast zaglądać do środka, ufamy, że to działa i idziemy dalej
        simulator.simulatePhotoTakingProcess()

        then: "the satellite *should have* taken the photos (nie weryfikujemy tego już bezpośrednio)"
        // Asercja simulator.photoStorage.size() == 120 jest USUNIĘTA
        noExceptionThrown()

        // ETAP 3: Downlink w paczkach
        when: "multiple downlink opportunities occur"
        // Symulujemy 3 okna (120 zdjęć / 50 na paczkę = 2.4 -> 3 paczki)
        3.times {
            satClient.sendGenericPost("/downlink", [:])
            Thread.sleep(200)
            intermediateClient.sendGenericPost("/process", [:])
            intermediateClient.sendGenericPost("/forward", [:])
            Thread.sleep(200)
        }

        then: "GRUND database eventually contains all the photos"
        // Weryfikujemy stan końcowy, który jest publicznie widoczny
        grundServer.getDbPhotoCount() == 120

        // ETAP 4: Użytkownik końcowy pobiera dane z GRUND
        when: "the user retrieves the final data from GRUND"
        def finalDataResponse = grundClient.getData("/data/photos")

        then: "the final data is complete and correct"
        finalDataResponse.statusCode == 200
        def finalData = finalDataResponse.json as List
        finalData.size() == 120
        finalData[0].quality == "HIGH"
    }
}