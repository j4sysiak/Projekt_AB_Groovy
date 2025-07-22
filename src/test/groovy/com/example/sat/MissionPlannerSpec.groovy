package com.example.sat

import com.example.sat.domain.Coordinates
import com.example.sat.domain.SatelliteState
import com.example.sat.dto.MissionRequest
import com.example.sat.exceptions.MissionPlanningException
import com.example.sat.fakes.ManualMockOrbitalMechanics
import com.example.sat.fakes.ManualMockSatelliteStateProvider
import spock.lang.Specification

class MissionPlannerSpec extends Specification {

    def "should create a valid manifest when all conditions are met"() {
        given: "a mission planner with providers indicating a healthy satellite"
        def orbitalMechanics = new ManualMockOrbitalMechanics(positionToReturn: new Coordinates(latitude: 50.0, longitude: 20.0))
        def stateProvider = new ManualMockSatelliteStateProvider(stateToReturn: new SatelliteState(batteryLevel: 80.0, dataStorageAvailable: 4096))
        def planner = new MissionPlannerImpl(orbitalMechanics, stateProvider)

        def request = new MissionRequest(
                targetCoordinates: new Coordinates(latitude: 52.0, longitude: 21.0)
        )

        when: "a plan is created for the request"
        def manifest = planner.createPlan(request)

        then: "the manifest is created successfully with a list of tasks"
        manifest.status == "PLANNED"
        manifest.tasks.size() == 5 // Zgodnie z logiką w plannerze
    }

    def "should reject a mission plan if satellite has insufficient power"() {
        given: "a planner with a state provider reporting low battery"
        def orbitalMechanics = Mock(IOrbitalMechanics)
        def stateProvider = new ManualMockSatelliteStateProvider(stateToReturn: new SatelliteState(batteryLevel: 15.0, dataStorageAvailable: 4096))

        def planner = new MissionPlannerImpl(orbitalMechanics, stateProvider)
        def request = new MissionRequest(targetCoordinates: new Coordinates(latitude: 52.0, longitude: 21.0))

        when: "a plan is created"
        planner.createPlan(request)

        then: "a MissionPlanningException is thrown"
        def e = thrown(MissionPlanningException)
        e.message.contains("power is too low")
    }
}