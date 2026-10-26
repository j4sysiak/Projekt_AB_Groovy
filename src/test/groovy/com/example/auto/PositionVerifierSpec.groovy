package com.example.auto

import com.example.auto.domain.Coordinates
import com.example.auto.exceptions.PositionMismatchException
import com.example.auto.fakes.ManualMockPlatzControlRadio
import com.example.auto.fakes.ManualMockOrbitalMechanics
import spock.lang.Specification

class PositionVerifierSpec extends Specification {

    def "should pass verification when predicted and measured positions are close"() {
        given: "a model and a radio reporting similar positions"
        def orbitalModel = new ManualMockOrbitalMechanics()
        orbitalModel.positionToReturn = new Coordinates(latitude: 45.0, longitude: 20.0)

        def radio = new ManualMockPlatzControlRadio()
        radio.positionToReturn = new Coordinates(latitude: 45.1, longitude: 20.1)

        def verifier = new PositionVerificationServiceImpl(orbitalModel, radio)

        when: "position is verified"
        verifier.verifyPosition("AUTO-OK")

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "should throw exception when positions mismatch significantly"() {
        given: "a model and a radio reporting distant positions"
        def orbitalModel = new ManualMockOrbitalMechanics()
        orbitalModel.positionToReturn = new Coordinates(latitude: 45.0, longitude: 20.0)

        def radio = new ManualMockPlatzControlRadio()
        radio.positionToReturn = new Coordinates(latitude: 46.0, longitude: 21.0) // Znaczna różnica

        def verifier = new PositionVerificationServiceImpl(orbitalModel, radio)

        when: "position is verified"
        verifier.verifyPosition("AUTO-DRIFT")

        then: "a PositionMismatchException is thrown"
        thrown(PositionMismatchException)
    }
}