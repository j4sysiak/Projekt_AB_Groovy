package com.example.sat

import com.example.sat.domain.Coordinates
import com.example.sat.exceptions.PositionMismatchException
import com.example.sat.fakes.ManualMockGroundControlRadar
import com.example.sat.fakes.ManualMockOrbitalMechanics
import spock.lang.Specification

class PositionVerifierSpec extends Specification {

    def "should pass verification when predicted and measured positions are close"() {
        given: "a model and a radar reporting similar positions"
        def orbitalModel = new ManualMockOrbitalMechanics()
        orbitalModel.positionToReturn = new Coordinates(latitude: 45.0, longitude: 20.0)

        def radar = new ManualMockGroundControlRadar()
        radar.positionToReturn = new Coordinates(latitude: 45.1, longitude: 20.1) // Bliska pozycja

        def verifier = new PositionVerificationServiceImpl(orbitalModel, radar)

        when: "position is verified"
        verifier.verifyPosition("SAT-OK")

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "should throw exception when positions mismatch significantly"() {
        given: "a model and a radar reporting distant positions"
        def orbitalModel = new ManualMockOrbitalMechanics()
        orbitalModel.positionToReturn = new Coordinates(latitude: 45.0, longitude: 20.0)

        def radar = new ManualMockGroundControlRadar()
        radar.positionToReturn = new Coordinates(latitude: 46.0, longitude: 21.0) // Znaczna różnica

        def verifier = new PositionVerificationServiceImpl(orbitalModel, radar)

        when: "position is verified"
        verifier.verifyPosition("SAT-DRIFT")

        then: "a PositionMismatchException is thrown"
        thrown(PositionMismatchException)
    }
}