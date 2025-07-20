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

    def "should process a photo collection mission in batches through the entire pipeline"() {
        given: "a mission to collect 120 photos"
        def missionRequest = [photoCount: 120]

        // ETAP 1: Zlecenie misji
        when: "the mission is submitted to GRUND"
        def missionResponse = grundClient.sendGenericPost("/missions/photo-collection", missionRequest)

        then: "GRUND accepts it and SAT receives the manifest"
        missionResponse.statusCode == 202
        simulator.activeManifest.tasks.size() == 120
        simulator.photoStorage.size() == 120 // Zdjęcia są zrobione od razu

        when: "multiple downlink opportunities occur, processing all batches"
        // 120 zdjęć / 50 na paczkę = 2.4 -> potrzebujemy 3 okien
        3.times {
            satClient.sendGenericPost("/downlink", [:])
            Thread.sleep(300) // Dajmy więcej czasu
            intermediateClient.sendGenericPost("/process", [:])
            intermediateClient.sendGenericPost("/forward", [:])
            Thread.sleep(300)
        }

        // ETAP KOŃCOWY: Weryfikacja
        then: "GRUND's database contains exactly 120 photos at the end"
        grundServer.getDbPhotoCount() == 120
    }
}