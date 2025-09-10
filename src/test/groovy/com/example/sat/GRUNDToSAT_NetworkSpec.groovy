package com.example.sat

import com.example.sat.fakes.FakeRelationalDB
import com.example.sat.fakes.HttpServerFactory
import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.SatelliteSimulatorServer
import spock.lang.Specification

class GRUNDToSAT_NetworkSpec extends Specification {
    GroundControlServerApp grundServer
    SatelliteSimulatorServer satServer
    SatelliteSimulator simulator
    SimpleHttpClient grundClient

    def setup() {
// 1. Stwórz testowe implementacje zależności
        simulator = new SatelliteSimulator() // Nasz "fejk" symulatora
        def satHttpServer = HttpServerFactory.create()
        def grundDb = new FakeRelationalDB()
        def grundHttpServer = HttpServerFactory.create()

        // 2. Stwórz i uruchom SatelliteSimulatorServer, wstrzykując jego zależności
        satServer = new SatelliteSimulatorServer(simulator, satHttpServer)
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        // 3. Stwórz i uruchom GroundControlServerApp, wstrzykując jego zależności
        grundServer = new GroundControlServerApp(grundDb, grundHttpServer)
        // Przekazujemy mu URL do serwera satelity, aby wiedział, z kim ma rozmawiać
        grundServer.start(0, satServerUrl)
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        // 4. Stwórz klienta do sterowania serwerem GRUND
        grundClient = new SimpleHttpClient(grundServerUrl)
    }

    def cleanup() {
        grundServer?.stop()
        satServer?.stop()
    }

    // ZMIANA: Nazwa i logika testu zostały w pełni przepisane
    def "ground server should allow a client to take a mission through its full lifecycle"() {
        given: "both servers are running"
        simulator.batteryLevel = 100.0

        when: "a user sends a mission request to the ground control server"
        def missionRequest = [photoCount: 5]
        def createResponse = grundClient.sendGenericPost("/missions/photo-collection", missionRequest)

        then: "the mission is created in PLANNED state"
        createResponse.statusCode == 202
        def mission = createResponse.json
        def missionId = mission.missionId
        mission.status == "PLANNED"
        mission.tasks.size() == 5
        missionId != null

        when: "the client accepts the mission"
        // Nie musimy używać HATEOAS, bo ten test sprawdza tylko poprawność kroków
        def acceptResponse = grundClient.sendGenericPost("/missions/${missionId}/accept", [:])

        then: "the mission status is updated to ACCEPTED"
        acceptResponse.statusCode == 200

        when: "the client requests to upload the mission"
        def uploadResponse = grundClient.sendGenericPost("/missions/${missionId}/upload", [:])

        then: "the upload is successful and the satellite receives the manifest"
        uploadResponse.statusCode == 200
        uploadResponse.json.status == "OK"

        // Czekamy chwilę, bo komunikacja jest asynchroniczna
        Thread.sleep(500)
        simulator.activeManifest.missionId == missionId
        simulator.activeManifest.status == "UPLOADED"
    }
}