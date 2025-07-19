package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.SatelliteSimulatorServer
import com.example.sat.utils.http.SimpleHttpClient
import spock.lang.Shared
import spock.lang.Specification

// Weryfikuje on bezpośrednią komunikację GRUND <-> SAT.
class GrundToSatNetworkSpec extends Specification {

    @Shared GroundControlServerApp grundServer
    @Shared SatelliteSimulatorServer satServer
    @Shared SatelliteSimulator simulator
    // Klient, który będzie udawał "użytkownika" klikającego w interfejsie GRUND
    @Shared SimpleHttpClient grundClient

    def setup() {
        // 1. Uruchom serwer satelity (SAT)
        simulator = new SatelliteSimulator()
        // Nie potrzebujemy downlinku w tym teście, więc URL może być pusty
        satServer = new SatelliteSimulatorServer(simulator, "") // Downlink nie jest potrzebny
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        // 2. Uruchom serwer kontroli naziemnej (GRUND), mówiąc mu, gdzie jest SAT
        grundServer = new GroundControlServerApp()
        grundServer.start(0, satServerUrl)
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        // 3. Stwórz klienta do komunikacji z serwerem GRUND
        grundClient = new SimpleHttpClient(grundServerUrl)
    }

    def cleanupSpec() {
        grundServer?.stop()
        satServer?.stop()
    }

    def cleanup() {
        simulator.resetState()
    }

    def "ground server should successfully send a command and receive an acknowledgement"() {
        given: "the satellite is ready to receive commands"
        simulator.batteryLevel = 90.0
        def authToken = "VALID-TOKEN-FOR-REST"

        when: "a user sends a mission request to the ground control server"
        // Używamy endpointu /mission/photos, który wewnątrz wysyła komendę TAKE_PHOTOS
        def missionRequest = [photoCount: 1, quality: "HIGH"]
        def response = grundClient.sendAuthenticatedPost("/mission/photos", missionRequest, authToken)

        then: "the ground server accepts the mission"
        response.statusCode == 202 // Accepted
        response.json.status == "ACCEPTED"

        and: "the simulator's state was changed by the command passed from GRUND to SAT"
        // Sprawdzamy, czy komenda dotarła do symulatora i została wykonana
        simulator.commandHistory.contains("TAKE_PHOTOS")
        simulator.photoQueue.size() == 1
        // Sprawdzamy, czy bateria została zużyta (90.0 - 0.5 za komendę)
        simulator.batteryLevel < 90.0
    }

    def "ground server should handle a failure response if satellite is low on power"() {
        given: "a satellite with critically low power"
        simulator.batteryLevel = 5.0 // Bardzo niski poziom baterii
        def authToken = "VALID-TOKEN-FOR-REST"

        when: "a user sends a mission request to the ground control server"
        def missionRequest = [photoCount: 1, quality: "HIGH"]

        // === POPRAWIONA LITERÓWKA ===
        def response = grundClient.sendAuthenticatedPost("/mission/photos", missionRequest, authToken)

        then: "the ground server rejects the mission based on the satellite's response"
        response.statusCode == 400 // Bad Request
        def jobInfo = response.json
        jobInfo.status == "REJECTED"
        jobInfo.details.contains("Critically low power")

        and: "the satellite's photo queue remains empty"
        simulator.photoQueue.isEmpty()
    }
}