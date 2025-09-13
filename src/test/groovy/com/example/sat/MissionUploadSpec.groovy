package com.example.sat

import com.example.sat.fakes.FakeRelationalDB
import com.example.sat.fakes.HttpServerFactory
import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.SatelliteSimulatorServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MissionUploadSpec extends Specification {

    GroundControlServerApp grundServer
    SatelliteSimulatorServer satServer
    SatelliteSimulator simulator
    SimpleHttpClient grundClient

    def setup() {
        // === ZMIANA: Pełne wstrzykiwanie zależności ===

        // 1. Stwórz testowe implementacje zależności
        simulator = new SatelliteSimulator()
        def grundDb = new FakeRelationalDB()
        def grundHttp = HttpServerFactory.create()
        def satHttp = HttpServerFactory.create()

        // 2. Stwórz i uruchom SatelliteSimulatorServer
        satServer = new SatelliteSimulatorServer(simulator, satHttp)
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        // 3. Stwórz i uruchom GroundControlServerApp
        grundServer = new GroundControlServerApp(grundDb, grundHttp)
        grundServer.start(0, satServerUrl) // Przekazujemy URL do serwera satelity
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        // 4. Stwórz klienta do sterowania serwerem GRUND
        grundClient = new SimpleHttpClient(grundServerUrl)
    }

    def cleanup() {
        grundServer?.stop()
        satServer?.stop()
    }

    def "should allow client to create, accept, and upload a mission"() {
        given: "a request to collect 5 photos"
        def missionRequest = [photoCount: 5]
        def conditions = new PollingConditions(timeout: 5)

        // --- ETAP 1: Tworzenie Misji ---
        when: "the mission request is sent to the ground control server"
        def createResponse = grundClient.sendGenericPost("/missions/photo-collection", missionRequest)

        then: "the mission is created successfully in PLANNED state"
        createResponse.statusCode == 202
        def mission = createResponse.json
        def missionId = mission.missionId
        mission.status == "PLANNED"
        missionId != null

        // --- ETAP 2: Akceptacja Misji ---
        when: "the client accepts the mission"
        def acceptResponse = grundClient.sendGenericPost("/missions/${missionId}/accept", [:])

        then: "the accept operation is successful"
        acceptResponse.statusCode == 200

        // --- ETAP 3: Wysyłka Misji ---
        when: "the client requests to upload the mission"
        def uploadResponse = grundClient.sendGenericPost("/missions/${missionId}/upload", [:])

        then: "the upload is successful"
        uploadResponse.statusCode == 200
        uploadResponse.json.status == "OK"

        and: "the satellite simulator has stored the correct manifest"
        // Używamy "cierpliwej" asercji zamiast Thread.sleep
        conditions.eventually {
            assert simulator.activeManifest != null
            assert simulator.activeManifest.missionId == missionId
            assert simulator.activeManifest.status == "UPLOADED"
            assert simulator.activeManifest.tasks.size() == 5
        }
    }
}