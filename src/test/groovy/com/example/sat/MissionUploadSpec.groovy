package com.example.sat

import com.example.sat.domain.Coordinates
import com.example.sat.dto.MissionRequest
import com.example.sat.fakes.ManualMockOrbitalMechanics
import com.example.sat.fakes.ManualMockSatelliteStateProvider
import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.http.SimpleHttpClient // Używamy Twojego pakietu
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

        def orbitalMechanics = new ManualMockOrbitalMechanics()
        def stateProvider = new ManualMockSatelliteStateProvider()
        grundServer.setMissionPlanner(new MissionPlannerImpl(orbitalMechanics, stateProvider))
    }

    def cleanup() {
        grundServer?.stop()
        satServer?.stop()
    }

    def "should plan and then upload a mission to the satellite"() {
        given: "a valid mission request"
        def missionRequest = new MissionRequest(targetCoordinates: new Coordinates(latitude: 1.0, longitude: 1.0))

        when: "a planning request is sent to GRUND"
        def planResponse = grundClient.sendGenericPost("/missions", missionRequest)

        then: "the mission is planned successfully"
        planResponse.statusCode == 202
        def plan = planResponse.json
        def missionId = plan.missionId
        missionId != null

        when: "an upload request is sent for the planned mission"
        def uploadResponse = grundClient.sendGenericPost("/missions/${missionId}/upload", [:])

        then: "the upload is successful"
        uploadResponse.statusCode == 200
        uploadResponse.json.status == "UPLOADED"

        and: "the satellite simulator has stored the mission plan"
        simulator.storedMissionPlans.size() == 1
        simulator.storedMissionPlans.containsKey(missionId)
    }
}