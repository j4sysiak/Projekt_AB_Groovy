package com.example.sat

import spock.lang.Specification

// Klasa-kontener na wszystkie zależności naszego środowiska testowego
// to jest po prostu nasze środowisko - env

class TestEnvironment {
    // Zależności niższego poziomu
    AuthService authService
    SatelliteConnection connection
    DataRepository repository
    TelemetryModule telemetryModule
    CommandModule commandModule

    // Zależności wyższego poziomu
    TokenCache tokenCache
    JobRepository jobRepository
    ProcessingQueue processingQueue

    // Główny "robotnik"
    ISingleReprocessor singleReprocessor
}

// Wersja Ostateczna: Klasa bazowa zawiera TYLKO wspólne, statyczne dane konfiguracyjne.
abstract class SomeBaseTest extends Specification {

    protected static final Map<String, SystemConfig> SYSTEM_CONFIGS = [
            "ALPHA": new SystemConfig(
                    "sat.alpha-partner.com", 9001, "https://auth.alpha.net/token",
                    "udp://metrics.alpha.com:5555", "cmd.alpha-partner.com", 2201,
                    "ALPHA-VALID-API-KEY"
            ),
            "BETA": new SystemConfig(
                    "beta-sat.internal.net", 19001, "http://10.0.2.15/beta_auth",
                    "tcp://10.0.2.16:6000", "10.0.2.17", 2202,
                    "BETA-SECRET-KEY-99"
            )
    ]

    // METODA-FABRYKA: teraz zwraca obiekt TestEnvironment
    protected TestEnvironment setupTestEnvironment(String systemName) {
        println "--- Setting up FULL test environment for System: ${systemName} ---"
        def config = SYSTEM_CONFIGS[systemName]
        if (config == null) throw new IllegalArgumentException("No config for system: ${systemName}")

        def env = new TestEnvironment()

        // 1. Tworzymy mocki/spy dla zależności niższego rzędu
        env.authService = Spy(AuthServiceImpl, constructorArgs: [config.authServiceUrl])
        env.connection = Mock(SatelliteConnection)
        env.telemetryModule = Mock(TelemetryModule)
        env.commandModule = Mock(CommandModule)
        env.repository = Mock(DataRepository)

        // 2. Tworzymy zależności wyższego rzędu, wstrzykując im te niższego rzędu
        // Używamy SPY, aby móc testować logikę cache, ale wciąż stubbować wynik.
        env.tokenCache = Spy(TokenCacheImpl, constructorArgs: [env.authService, config.apiKey])
        env.jobRepository = Mock(JobRepository)
        env.processingQueue = Mock(ProcessingQueue)

        // 3. Tworzymy naszego "robotnika"
        env.singleReprocessor = new ReprocessingServiceImpl(
                env.connection, env.repository, env.tokenCache,
                env.telemetryModule, env.commandModule
        )

        return env
    }
}