package com.example.sat

import com.example.sat.domain.Coordinates
import com.example.sat.dto.MissionRequest
import com.example.sat.fakes.ManualMockOrbitalMechanics
import com.example.sat.fakes.ManualMockSatelliteStateProvider
import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
import com.example.sat.http.SimpleHttpClient
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

        // Wstrzyknij do GRUND "fejkowego" planistę, aby test był przewidywalny
        def orbitalMechanics = new ManualMockOrbitalMechanics()
        def stateProvider = new ManualMockSatelliteStateProvider()
        grundServer.setMissionPlanner(new MissionPlannerImpl(orbitalMechanics, stateProvider))

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

        // === ETAP 1: Planowanie i wysyłka planu ===
        when: "A user commands GRUND to start a mission"
        def missionRequest = new MissionRequest(
                targetCoordinates: new Coordinates(latitude: 10.0, longitude: 10.0)
        )
        def planResponse = grundClient.sendAuthenticatedPost("/missions", missionRequest, authToken)
        def missionId = planResponse.json.missionId

        and: "the user commands GRUND to upload the mission plan to SAT"
        def uploadResponse = grundClient.sendGenericPost("/missions/${missionId}/upload", [:])

        then: "the plan is successfully stored on the satellite"
        uploadResponse.statusCode == 200
        simulator.storedMissionPlans.containsKey(missionId)

        // === ETAP 2: (Symulowane) wykonanie misji przez satelitę ===
        when: "time passes and the satellite executes the stored mission plan"
        // W prawdziwym systemie to działo by się autonomicznie.
        // W teście symulujemy to, ręcznie wołając komendy z zapisanego planu.
        def storedPlan = simulator.storedMissionPlans[missionId]

        // --- KLUCZOWA ZMIANA: Dodajemy brakujące połączenie ---
        simulator.connect(authToken) // 1. Połącz się
        storedPlan.commandSequence.each { cmd ->
            simulator.sendCommand(cmd.command, cmd.params) // 2. Wykonaj komendy
        }
        simulator.disconnect() // 3. Rozłącz się
        // -----------------------------------------------------

        then: "the satellite has generated photos and queued them for downlink"
        simulator.photoQueue.size() == 5 // Zgodnie z planem wygenerowanym przez MissionPlanner

        // === ETAP 3: Downlink danych do SERW1 ===
        when: "SAT performs a downlink to SERW1"
        def satClient = new SimpleHttpClient("http://localhost:${satServer.getPort()}")
        satClient.sendGenericPost("/downlink", [:])

        then: "SERW1 receives the data"
        Thread.sleep(500)
        intermediateServer.getFileSystemFileCount() == 1

        // === ETAP 4: Przetwarzanie na SERW1 i wysyłka do GRUND ===
        when: "An operator commands SERW1 to process and forward the data"
        intermediateClient.sendGenericPost("/process", [:])
        intermediateClient.sendGenericPost("/forward", [:])

        then: "GRUND receives and stores the final data"
        Thread.sleep(500)
        grundServer.getDbPhotoCount() == 5

        // === ETAP 5: Użytkownik końcowy pobiera dane z GRUND ===
        when: "The user retrieves the final, processed data from GRUND"
        def finalDataResponse = grundClient.getData("/data/photos")

        then: "the retrieved data is correct"
        finalDataResponse.statusCode == 200
        def finalData = finalDataResponse.json as List
        finalData.size() == 5
        finalData[0].quality == "HIGH"
    }

}