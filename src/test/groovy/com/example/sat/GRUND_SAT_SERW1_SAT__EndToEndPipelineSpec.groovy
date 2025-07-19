package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
import com.example.sat.utils.http.SimpleHttpClient
import spock.lang.Specification

class GRUND_SAT_SERW1_SAT__EndToEndPipelineSpec extends Specification {

    // Deklarujemy wszystkie serwery i klientów
    GroundControlServerApp grundServer
    IntermediateServerApp intermediateServer
    SatelliteSimulatorServer satServer

    SimpleHttpClient grundClient
    SimpleHttpClient intermediateClient

    SatelliteSimulator simulator

    // Używamy setup() zamiast setupSpec(), aby zapewnić 100% izolacji
    def setup() {
        simulator = new SatelliteSimulator()

        // 1. Uruchom serwer GRUND na losowym porcie
        grundServer = new GroundControlServerApp()
        grundServer.start(0, "") // Na razie z pustym URL-em do SAT

        // 2. Uruchom serwer SERW1, podając mu adres GRUND
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"
        intermediateServer = new IntermediateServerApp()
        intermediateServer.start(0, grundServerUrl)
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        // 3. Uruchom serwer SAT, podając mu adres SERW1
        satServer = new SatelliteSimulatorServer(simulator, intermediateServerUrl)
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        // 4. Zaktualizuj GRUND o poprawny adres SAT
        grundServer.updateSatClient(new SimpleHttpClient(satServerUrl))

        // 5. Stwórz klientów do sterowania w teście
        grundClient = new SimpleHttpClient(grundServerUrl)
        intermediateClient = new SimpleHttpClient(intermediateServerUrl)
    }

    def cleanup() {
        grundServer?.stop()
        intermediateServer?.stop()
        satServer?.stop()
    }

    def "should successfully flow a mission from ground control, through all stages, and retrieve final data"() {
        given: "all systems are online and healthy"
        simulator.batteryLevel = 100.0
        def authToken = "SECRET-E2E-TOKEN"

        // === SCENARIUSZ TESTOWY ===

        when: "Step 1: A user commands GRUND to start a photo mission"
        def missionRequest = [photoCount: 2, quality: "HIGH"]
        def missionResponse = grundClient.sendAuthenticatedPost("/mission/photos", missionRequest, authToken)

        then: "GRUND accepts the mission and SAT executes it"
        missionResponse.statusCode == 202
        simulator.photoQueue.size() == 2

        when: "Step 2: SAT performs a downlink to SERW1"
        def satClient = new SimpleHttpClient("http://localhost:${satServer.getPort()}")
        satClient.sendGenericPost("/downlink", [:])

        then: "SERW1 receives the data"
        Thread.sleep(500)
        intermediateServer.getFileSystemFileCount() == 1

        when: "Step 3: An operator commands SERW1 to process and forward the data"
        intermediateClient.sendGenericPost("/process", [:]) // Archiwizacja do bazy
        intermediateClient.sendGenericPost("/forward", [:]) // Wysłanie do GRUND

        then: "GRUND receives and stores the final data"
        Thread.sleep(500)
        grundServer.getDbPhotoCount() == 2

        when: "Step 4: The user retrieves the final, processed data from GRUND"
        def finalDataResponse = grundClient.getData("/data/photos")

        then: "the retrieved data is correct"
        finalDataResponse.statusCode == 200
        def finalData = finalDataResponse.json as List
        finalData.size() == 2
        finalData[0].quality == "HIGH"
    }
}