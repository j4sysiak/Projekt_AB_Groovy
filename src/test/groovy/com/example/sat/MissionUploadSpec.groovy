package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.SatelliteSimulatorServer
import spock.lang.Specification

class MissionUploadSpec extends Specification {

    GroundControlServerApp grundServer
    SatelliteSimulatorServer satServer
    SatelliteSimulator simulator
    SimpleHttpClient grundClient

    def setup() {
        simulator = new SatelliteSimulator()
        satServer = new SatelliteSimulatorServer(simulator, "")
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        grundServer = new GroundControlServerApp()
        grundServer.start(0, satServerUrl)
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        grundClient = new SimpleHttpClient(grundServerUrl)
    }

    def cleanup() {
        grundServer?.stop()
        satServer?.stop()
    }

    // ZMIANA: Nazwa i logika testu zostały w pełni przepisane, aby odzwierciedlały nowy proces
    def "should allow client to create, accept, and upload a mission"() {
        given: "a request to collect 5 photos"
        def missionRequest = [photoCount: 5]

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
        // Czekamy chwilę na asynchroniczną komunikację sieciową
        Thread.sleep(1000)

        simulator.activeManifest != null
        simulator.activeManifest.missionId == missionId
        simulator.activeManifest.status == "UPLOADED" // Sprawdzamy finalny status na satelicie
        simulator.activeManifest.tasks.size() == 5
    }
}