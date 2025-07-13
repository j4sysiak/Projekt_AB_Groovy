package com.example.sat

import com.example.sat.fakes.ManualMockDataRepository
import com.example.sat.fakes.ManualMockTelemetryModule
import com.example.sat.fakes.ManualMockTokenCache
import com.example.sat.fakes.SatelliteSimulator
import spock.lang.Specification

// Ta klasa nie dziedziczy po SomeBaseTest, bo wszystko budujemy ręcznie.
class AdvancedSimulatorScenariosSpec extends Specification {

    // Helper do tworzenia serwisu z naszymi ręcznymi fejkami
    private ReprocessingServiceImpl createService(SatelliteSimulator simulator) {
        return new ReprocessingServiceImpl(
                simulator,
                new ManualMockDataRepository(),
                new ManualMockTokenCache(),
                new ManualMockTelemetryModule(),
                simulator, new InputValidatorImpl()
        )
    }

    def "should pass pre-flight checks when simulator is in a healthy state"() {
        given: "a healthy simulator and a service built with fakes"
        def simulator = new SatelliteSimulator()
        simulator.batteryLevel = 80.0

        def service = createService(simulator)

        when: "reprocessing is triggered"
        // Symulujemy, że fetchData zawiedzie, aby zatrzymać test w odpowiednim momencie
        simulator.forceFetchDataToFail = true
        service.reprocessDataForSatellite("SAT-HEALTHY")

        then: "the pre-flight check commands were sent and the correct history is recorded"
        // Weryfikujemy stan naszego symulatora, a nie "magiczne" interakcje Spocka
        simulator.commandHistory.contains("CHECK_POWER_STATUS")
        simulator.commandHistory.contains("CHECK_SYSTEM_HEALTH")
    }

    def "should abort mission if simulator reports critically low power"() {
        given: "a simulator with low battery"
        def simulator = new SatelliteSimulator()
        simulator.batteryLevel = 15.0 // Poniżej progu 20%

        def service = createService(simulator)

        when: "reprocessing is triggered"
        boolean result = service.reprocessDataForSatellite("SAT-LOW-POWER")

        then: "the operation fails as expected"
        result == false

        and: "the camera activation command was never sent"
        !simulator.commandHistory.contains("SET_CAMERA_MODE:ACTIVE_IMAGING")
    }

    def "should correctly simulate the day-night power cycle"() {
        given: "a simulator with medium battery level in darkness"
        def simulator = new SatelliteSimulator()
        simulator.batteryLevel = 50.0
        simulator.simulateTimePassage(false)

        expect: "battery level has decreased"
        simulator.batteryLevel == 48.0
        simulator.solarPanelStatus == "IDLE"

        when: "time passes and the simulator enters daylight"
        simulator.simulateTimePassage(true)

        then: "battery level increases and panel status changes"
        simulator.batteryLevel == 58.0
        simulator.solarPanelStatus == "CHARGING"
    }
}