package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
import spock.lang.Specification
import com.example.sat.dto.PhotoTaskStatus
import spock.util.concurrent.PollingConditions // Importujemy "cierpliwe" asercje

class PhotoCollectionPipelineSpec extends Specification {

    GroundControlServerApp grundServer
    IntermediateServerApp intermediateServer
    SatelliteSimulatorServer satServer
    SimpleHttpClient grundClient
    SimpleHttpClient intermediateClient
    SimpleHttpClient satClient
    SatelliteSimulator simulator

    // Metoda setup jest OK, ale poprawmy konfigurację klienta GRUND
    def setup() {
        simulator = new SatelliteSimulator()

        // Kolejność jest kluczowa. Zaczynamy od serwerów, które nie mają zależności.

        // 1. Serwer pośredniczący nie zależy od nikogo w tym setupie (bo GRUND go nie woła)
        intermediateServer = new IntermediateServerApp()
        intermediateServer.start(0, "") // Na razie bez adresu GRUND, bo go nie znamy
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        // 2. Serwer satelity zależy od serwera pośredniczącego (wie, gdzie wysyłać dane)
        satServer = new SatelliteSimulatorServer(simulator, intermediateServerUrl)
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        // 3. Serwer GRUND zależy od serwera satelity (wie, gdzie wysyłać manifesty)
        grundServer = new GroundControlServerApp()
        grundServer.start(0, satServerUrl)
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        // 4. (Opcjonalnie) Teraz, gdy GRUND działa, możemy zaktualizować SERW1, aby wiedział, gdzie jest GRUND
        intermediateServer.updateGrundControlUrl(grundServerUrl) // Musisz dodać tę metodę do IntermediateServerApp

        // 5. Stwórz klientów
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
        def conditions = new PollingConditions(timeout: 15) // Dajemy więcej czasu na cały pipeline

        // --- ETAP 1: Stworzenie, akceptacja i wysłanie misji ---
        when: "the mission is created, accepted and submitted to GRUND"
        def createResponse = grundClient.sendGenericPost("/missions/photo-collection", missionRequest)
        def missionId = createResponse.json.missionId
        grundClient.sendGenericPost("/missions/${missionId}/accept", [:])
        def uploadResponse = grundClient.sendGenericPost("/missions/${missionId}/upload", [:])

        then: "GRUND confirms upload and SAT receives the manifest"
        uploadResponse.statusCode == 200
        conditions.eventually {
            assert simulator.activeManifest != null
            assert simulator.activeManifest.tasks.size() == 120
        }

        // --- ETAP 2: Wykonanie misji i downlink ---
        when: "the satellite takes all the photos and downlinks them"
        simulator.simulatePhotoTakingProcess()

        then: "all photos are taken and queued"
        simulator.activeManifest.tasks.count { it.status == PhotoTaskStatus.TAKEN_AND_QUEUED } == 120

        when: "three downlink opportunities occur"
        3.times {
            satClient.sendGenericPost("/downlink", [:])
            Thread.sleep(500) // Krótka pauza na transfer
        }

        then: "SERW1 has received all the files"
        conditions.eventually {
            assert intermediateServer.getFileSystemFileCount() == 3 // 120 zdjęć / 40 na paczkę
        }

        // --- ETAP 3: Przetworzenie i przekazanie danych ---
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