package com.example.sat

import com.example.sat.fakes.ManualMockOrbitalMechanics
import spock.lang.Specification

class PassPredictorSpec extends Specification {

    def "should report window is open when satellite is high above horizon"() {
        given: "a predictor and an orbital mechanics module reporting a high satellite position"
        def orbitalMechanics = new ManualMockOrbitalMechanics()
        orbitalMechanics.positionToReturn = new Coordinates(latitude: 45.0, longitude: 15.0)

        // Nasza stacja naziemna (współrzędne nie mają znaczenia dla tej prostej logiki)
        def groundStation = new Coordinates(latitude: 52.0, longitude: 21.0)

        def predictor = new GroundStationPassPredictorImpl(orbitalMechanics, groundStation)

        when: "we check for a communication window"
        boolean isOpen = predictor.isCommunicationWindowOpen()

        then: "the window is reported as open"
        isOpen == true
    }

    def "should report window is closed when satellite is below horizon"() {
        given: "a predictor and an orbital mechanics module reporting a low satellite position"
        def orbitalMechanics = new ManualMockOrbitalMechanics()
        // Satelita jest na południowej półkuli, poza naszym zasięgiem
        orbitalMechanics.positionToReturn = new Coordinates(latitude: -30.0, longitude: 100.0)

        def groundStation = new Coordinates(latitude: 52.0, longitude: 21.0)
        def predictor = new GroundStationPassPredictorImpl(orbitalMechanics, groundStation)

        when: "we check for a communication window"
        boolean isOpen = predictor.isCommunicationWindowOpen()

        then: "the window is reported as closed"
        isOpen == false
    }
}