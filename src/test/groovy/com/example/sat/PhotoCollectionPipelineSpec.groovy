package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
import com.example.sat.http.SimpleHttpClient
import spock.lang.Specification

class PhotoCollectionPipelineSpec extends Specification {

    // Deklarujemy wszystkie serwery i klientów
    GroundControlServerApp grundServer
    IntermediateServerApp intermediateServer
    SatelliteSimulatorServer satServer

    SimpleHttpClient grundClient
    SimpleHttpClient intermediateClient
    SimpleHttpClient satClient

    SatelliteSimulator simulator

    def setup() {
        simulator = new SatelliteSimulator()

        // 1. Uruchom serwer GRUND
        grundServer = new GroundControlServerApp()
        grundServer.start(0, "")
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"
        grundClient = new SimpleHttpClient(grundServerUrl)

        // 2. Uruchom serwer SERW1, podając mu adres GRUND
        intermediateServer = new IntermediateServerApp()
        intermediateServer.start(0, grundServerUrl)
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"
        intermediateClient = new SimpleHttpClient(intermediateServerUrl)

        // 3. Uruchom serwer SAT, podając mu adres SERW1
        satServer = new SatelliteSimulatorServer(simulator, intermediateServerUrl)
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"
        satClient = new SimpleHttpClient(satServerUrl)

        // 4. Zaktualizuj GRUND o poprawny adres SAT
        grundServer.updateSatClient(satClient)
    }

    def cleanup() {
        grundServer?.stop()
        intermediateServer?.stop()
        satServer?.stop()
    }

    def "should process a large photo collection mission in batches through the entire pipeline"() {
        given: "a mission to collect 120 photos is requested" // Użyjmy mniejszej liczby dla szybszego testu
        def missionRequest = [photoCount: 120]

        // === ETAP 1: GRUND zleca misję do SAT ===
        when: "the mission is submitted to GRUND"
        def missionResponse = grundClient.sendGenericPost("/missions/photo-collection", missionRequest)

        then: "GRUND accepts it and SAT receives the manifest"
        missionResponse.statusCode == 202
        simulator.activeManifest.tasks.size() == 120

        // === ETAP 2: SAT wykonuje misję (robi zdjęcia) ===
        when: "the satellite is given time to execute the mission"
        simulator.simulatePhotoTakingProcess()

        then: "all 120 photos are taken and stored on the satellite"
        simulator.photoStorage.size() == 120

        // === ETAP 3: Symulacja wielu okien komunikacyjnych (SAT -> SERW1 -> GRUND) ===
        when: "multiple downlink opportunities occur"
        int totalPhotosInGrundDb = 0
        // Symulujemy 3 okna (120 zdjęć / 50 na paczkę = 2.4 -> 3 paczki)
        3.times { round ->
            println "\n--- Communication Window #${round + 1} ---"

            // 3a. SAT wysyła paczkę danych do SERW1
            satClient.sendGenericPost("/downlink", [:])
            Thread.sleep(200) // Czekamy na request sieciowy

            // 3b. SERW1 przetwarza i wysyła dane do GRUND
            intermediateClient.sendGenericPost("/process", [:])
            intermediateClient.sendGenericPost("/forward", [:])
            Thread.sleep(200) // Czekamy na request sieciowy

            totalPhotosInGrundDb = grundServer.getDbPhotoCount()
            println "--- End of Window #${round + 1}: GRUND DB now has ${totalPhotosInGrundDb} photos ---"
        }

        then: "all 120 photos are eventually received and stored by GRUND"
        grundServer.getDbPhotoCount() == 120

        // === ETAP 4: Użytkownik końcowy weryfikuje dane w GRUND ===
        when: "the user retrieves the final data from GRUND"
        def finalDataResponse = grundClient.getData("/data/photos")

        then: "the final data is complete and correct"
        finalDataResponse.statusCode == 200
        def finalData = finalDataResponse.json as List
        finalData.size() == 120
        finalData[0].quality == "HIGH"
    }
}