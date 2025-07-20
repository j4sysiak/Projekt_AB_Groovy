package com.example.sat

import com.example.sat.domain.Coordinates
import com.example.sat.domain.SatelliteState
import com.example.sat.dto.MissionRequest
import com.example.sat.fakes.ManualMockOrbitalMechanics
import com.example.sat.fakes.ManualMockSatelliteStateProvider
import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.SatelliteSimulatorServer
import com.example.sat.http.SimpleHttpClient
import spock.lang.Specification

// Ten test weryfikuje komunikację i logikę planowania między GRUND i SAT
class GRUNDToSAT_NetworkSpec extends Specification {

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

    def "ground server should accept and plan a viable mission"() {
        given: "a mission planner configured for success"
        // Wstrzykujemy do serwera GRUND "fejkowe" komponenty dla planisty,
        // które gwarantują, że planowanie się powiedzie.
        def orbitalMechanics = new ManualMockOrbitalMechanics(positionToReturn: new Coordinates(latitude: 50.0, longitude: 20.0))
        def stateProvider = new ManualMockSatelliteStateProvider(stateToReturn: new SatelliteState(batteryLevel: 80.0, dataStorageAvailable: 4096))
        grundServer.setMissionPlanner(new MissionPlannerImpl(orbitalMechanics, stateProvider))

        and: "a valid mission request"
        def missionRequest = new MissionRequest(
                targetCoordinates: new Coordinates(latitude: 52.0, longitude: 21.0)
        )

        when: "a user sends the mission request to the ground control server"
        // Wołamy nowy, poprawny endpoint
        def response = grundClient.sendGenericPost("/missions", missionRequest)

        then: "the ground server accepts the mission and returns a valid plan"
        response.statusCode == 202 // Accepted
        def plan = response.json
        plan.status == "PENDING_UPLOAD"
        plan.missionId != null
    }

    def "ground server should reject a mission if satellite has insufficient power"() {
        given: "a mission planner configured to detect a low battery"
        // Konfigurujemy "fejka", aby symulował niski poziom baterii
        def orbitalMechanics = new ManualMockOrbitalMechanics()
        def stateProvider = new ManualMockSatelliteStateProvider(stateToReturn: new SatelliteState(batteryLevel: 15.0, dataStorageAvailable: 4096))
        grundServer.setMissionPlanner(new MissionPlannerImpl(orbitalMechanics, stateProvider))

        and: "a mission request"
        def missionRequest = new MissionRequest(targetCoordinates: new Coordinates(latitude: 52.0, longitude: 21.0))

        when: "a user sends the mission request"
        def response = grundClient.sendGenericPost("/missions", missionRequest)

        then: "the ground server rejects the mission with a proper explanation"
        response.statusCode == 422 // Unprocessable Entity
        def error = response.json
        error.error == "Mission Rejected"
        error.reason.contains("power is too low")
    }
}