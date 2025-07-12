package com.example.sat

import com.example.sat.fakes.InMemoryFileRepository
import com.example.sat.fakes.SatelliteSimulator
import spock.lang.Specification

// Ten test nie dziedziczy po SomeBaseTest, bo budujemy środowisko ręcznie,
// podmieniając prawdziwe komponenty na nasz symulator.
class SimulatorIntegrationSpec extends Specification {

    def "should correctly command the satellite simulator to take a photo and download data"() {
        given: "a complete processing chain with a satellite simulator"
        def satelliteId = "SAT-SIM-007"

        // 1. Tworzymy nasz symulator. To jest "czarna skrzynka", którą będziemy testować.
        def simulator = new SatelliteSimulator()

        // 2. Tworzymy inne, prawdziwe zależności
        def config = new SystemConfig("host", 1, "url", "ep", "ch", 1, "secret-name")
        // Używamy prawdziwych implementacji, aby przetestować ich integrację
        def authService = new AuthServiceImpl("auth-url")
        def secretVault = new SecretVaultImpl("vault-url") // Zakładając, że ten plik istnieje
        def tokenCache = new TokenCacheImpl(authService, secretVault, config.apiKeySecretName)
        def repository = new InMemoryFileRepository()
        def telemetryModule = new TelemetryModuleImpl(config.telemetryEndpoint)
        def validator = new InputValidatorImpl()

        // 3. Tworzymy nasz główny serwis, wstrzykując mu nasz SYMULATOR zamiast
        // prawdziwych implementacji Connection i Command. To jest możliwe, bo implementują te same interfejsy.
        def reprocessingService = new ReprocessingServiceImpl(
                simulator, // <-- wstrzykujemy symulator jako Connection
                repository,
                tokenCache,
                telemetryModule,
                simulator, // <-- wstrzykujemy symulator jako CommandModule
                validator
        )

        when: "the reprocessing service is asked to process the satellite data"
        boolean result = reprocessingService.reprocessDataForSatellite(satelliteId)

        then: "the operation succeeds"
        result == true

        and: "the simulator state has changed as expected"
        // Sprawdzamy, czy interakcja z symulatorem przebiegła poprawnie
        simulator.isConnected() == false // Serwis powinien się na końcu rozłączyć
        simulator.commandsReceived == 2
        simulator.commandHistory == ["SET_CAMERA_MODE:ACTIVE_IMAGING", "SET_CAMERA_MODE:STANDBY"]
        simulator.cameraMode == "STANDBY" // Sprawdzamy, czy aparat został wyłączony

        and: "the data was saved in the repository"
        repository.wasDataSaved(satelliteId) == true
    }
}