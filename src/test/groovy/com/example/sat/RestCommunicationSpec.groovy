package com.example.sat

import com.example.sat.fakes.FakeGroundControlServer
import com.example.sat.fakes.FakeSatelliteServer
import com.example.sat.fakes.SatelliteSimulator
import spock.lang.Specification

class RestCommunicationSpec extends Specification {

    def "ground server should successfully send a command and receive an acknowledgement from satellite server"() {
        given: "a ground server and a satellite server connected via a 'network'"
        // 1. Tworzymy komponenty backendu satelity
        def simulator = new SatelliteSimulator()
        def satelliteServer = new FakeSatelliteServer(simulator: simulator)

        // 2. Tworzymy serwer naziemny i "łączymy" go z serwerem satelity
        def groundServer = new FakeGroundControlServer(satelliteServer: satelliteServer)

        // 3. Przygotowujemy stan symulatora
        simulator.batteryLevel = 90.0
        def validToken = "VALID-TOKEN-FOR-REST"

        when: "the ground server sends a valid command"
        def response = groundServer.sendCommand("SET_CAMERA_MODE:ACTIVE_IMAGING", validToken)

        then: "a successful response is received and deserialized correctly"
        response.status == "ACK"
        response.message == "ACK: Camera set to ACTIVE_IMAGING"

        and: "the simulator's state was changed by the command"
        simulator.cameraMode == "ACTIVE_IMAGING"
        // Sprawdzamy, czy bateria została zużyta (90.0 - 0.5 za komendę)
        response.batteryLevel < 90.0
    }

    def "ground server should handle a failure response if satellite is low on power"() {
        given: "a satellite with critically low power"
        def simulator = new SatelliteSimulator()
        simulator.batteryLevel = 5.0 // Bardzo niski poziom baterii

        def satelliteServer = new FakeSatelliteServer(simulator: simulator)
        def groundServer = new FakeGroundControlServer(satelliteServer: satelliteServer)
        def validToken = "VALID-TOKEN-FOR-REST"

        when: "the ground server sends a command"
        def response = groundServer.sendCommand("SET_CAMERA_MODE:ACTIVE_IMAGING", validToken)

        then: "a NACK response is received"
        response.status == "NACK"
        response.message.contains("Critically low power")
    }
}