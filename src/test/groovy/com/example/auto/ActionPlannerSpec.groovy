package com.example.auto

import com.example.auto.domain.Coordinates
import com.example.auto.domain.AutoState
import com.example.auto.dto.ActionRequest
import com.example.auto.exceptions.ActionPlanningException
import com.example.auto.fakes.ManualMockOrbitalMechanics
import com.example.auto.fakes.ManualMockAutoStateProvider
import spock.lang.Specification

class ActionPlannerSpec extends Specification {

    def "should create a valid manifest when all conditions are met"() {
        given: "a action planner with providers indicating a healthy auto state"
        def orbitalMechanics = new ManualMockOrbitalMechanics(positionToReturn: new Coordinates(latitude: 50.0, longitude: 20.0))
        def stateProvider = new ManualMockAutoStateProvider(stateToReturn: new AutoState(batteryLevel: 80.0, dataStorageAvailable: 4096))
        def planner = new FuturePlannerImpl(orbitalMechanics, stateProvider)

        def request = new ActionRequest(
                targetCoordinates: new Coordinates(latitude: 52.0, longitude: 21.0)
        )

        when: "a plan is created for the request"
        def manifest = planner.createPlan(request)

        then: "the manifest is created successfully with a list of tasks"
        manifest.status == "PLANNED"
        manifest.tasks.size() == 5 // Zgodnie z logiką w plannerze
    }

    def "should reject a action plan if auto has insufficient power"() {
        given: "a planner with a state provider reporting low battery"
        def orbitalMechanics = Mock(IOrbitalMechanics)
        def stateProvider = new ManualMockAutoStateProvider(stateToReturn: new AutoState(batteryLevel: 15.0, dataStorageAvailable: 4096))

        def planner = new FuturePlannerImpl(orbitalMechanics, stateProvider)
        def request = new ActionRequest(targetCoordinates: new Coordinates(latitude: 52.0, longitude: 21.0))

        when: "a plan is created"
        planner.createPlan(request)

        then: "a ActionPlanningException is thrown"
        def e = thrown(ActionPlanningException)
        e.message.contains("power is too low")
    }
}