package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import spock.lang.Specification

class AdvancedSimulatorScenariosSpec extends Specification {

    // Helper do tworzenia serwisu, aby nie powielać kodu w każdym teście
    private ReprocessingServiceImpl createService(SatelliteSimulator simulator) {
        // Używamy "fejków" i mocków dla zależności, które nie są symulowane
        def tokenCache = Mock(TokenCache)
        tokenCache.getToken() >> "VALID-SIM-TOKEN"

        return new ReprocessingServiceImpl(
                simulator,
                Mock(DataRepository),
                tokenCache,
                Mock(TelemetryModule),
                simulator,
                new InputValidatorImpl()
        )
    }

    def "should pass pre-flight checks when simulator is in a healthy state"() {
        given: "a healthy simulator and a service"
        def simulator = new SatelliteSimulator()
        simulator.batteryLevel = 80.0
        simulator.thrusterHealth = "NOMINAL"

        // ZMIANA: Tworzymy 'service' i od razu tworzymy jego 'Spy'
        // 'service' jest teraz widoczne w całym teście.
        def service = Spy(createService(simulator))

        when: "reprocessing is triggered"
        // Używamy teraz 'service', który jest już "szpiegiem"
        service.reprocessDataForSatellite("SAT-HEALTHY")

        then: "pre-flight checks are performed and passed"
        // Teraz asercja zadziała, bo 'service' jest widoczne i jest szpiegiem
        1 * service.performPreflightChecks(_)
    }

    def "should abort mission if simulator reports critically low power"() {
        given: "a simulator with low battery"
        def simulator = new SatelliteSimulator()
        simulator.batteryLevel = 15.0 // Poniżej progu 20%

        def service = createService(simulator)

        when: "reprocessing is triggered"
        boolean result = service.reprocessDataForSatellite("SAT-LOW-POWER")

        then: "the operation fails immediately after pre-flight checks"
        result == false

        and: "no attempt was made to take a photo"
        // Sprawdzamy, że komenda do włączenia aparatu nigdy nie została wysłana
        simulator.commandHistory.contains("SET_CAMERA_MODE:ACTIVE_IMAGING") == false
    }

    def "should correctly simulate the day-night power cycle"() {
        given: "a simulator with medium battery level in darkness"
        def simulator = new SatelliteSimulator()
        simulator.batteryLevel = 50.0
        simulator.simulateTimePassage(false) // Jest noc, bateria się rozładowuje

        expect: "battery level has decreased"
        simulator.batteryLevel == 48.0
        simulator.solarPanelStatus == "IDLE"

        when: "time passes and the simulator enters daylight"
        simulator.simulateTimePassage(true) // Nadchodzi dzień, panele zaczynają ładować

        then: "battery level increases and panel status changes"
        simulator.batteryLevel == 58.0
        simulator.solarPanelStatus == "CHARGING"
    }
}