package com.example.sat

import com.example.sat.fakes.InMemoryDbRepository
import com.example.sat.fakes.InMemoryFileRepository
import spock.lang.Specification

// Wersja Ostateczna: Używamy Spy na obu niestabilnych zależnościach.
abstract class SomeBaseTest extends Specification {

    // === CENTRALNY REJESTR KONFIGURACJI ===
    protected static final Map<String, SystemConfig> SYSTEM_CONFIGS = [
            // Używamy prostego, POZYCYJNEGO konstruktora. To jest kluczowa zmiana.
            // Argumenty muszą być w tej samej kolejności co pola w klasie SystemConfig.
            "ALPHA": new SystemConfig(
                    "sat.alpha-partner.com",    // satelliteHost
                    9001,                       // satellitePort
                    "https://auth.alpha.net/token",// authServiceUrl
                    "udp://metrics.alpha.com:5555", // telemetryEndpoint
                    "cmd.alpha-partner.com",    // commandHost
                    2201,                       // commandPort
                    "ALPHA-VALID-API-KEY"       // apiKey
            ),
            "BETA": new SystemConfig(
                    "beta-sat.internal.net",    // satelliteHost
                    19001,                      // satellitePort
                    "http://10.0.2.15/beta_auth",// authServiceUrl
                    "tcp://10.0.2.16:6000",     // telemetryEndpoint
                    "10.0.2.17",                // commandHost
                    2202,                       // commandPort
                    "BETA-SECRET-KEY-99"        // apiKey
            )
    ]

    // === Pola na nasze zależności testowe ===
    // Będą one wypełniane przez metodę setupSystem()
    protected AuthService authService
    protected SatelliteConnection connection
    protected DataRepository repository
    protected TelemetryModule telemetryModule
    protected CommandModule commandModule

    protected ReprocessingService service

    // === METODA-FABRYKA: serce naszego rozwiązania ===
    // Ta metoda buduje kompletne, gotowe do testów środowisko dla wybranego systemu.
    protected void setupSystem(String systemName) {
        println "--- Setting up test environment for System: ${systemName} ---"

        // 1. Pobierz konfigurację dla danego systemu
        def config = SYSTEM_CONFIGS[systemName]
        if (config == null) {
            throw new IllegalArgumentException("No configuration found for system: ${systemName}")
        }

        // 2. Stwórz zależności testowe (Spy/Mock) na podstawie tej konfiguracji.
        // Używamy Spy, bo ten wzorzec okazał się dla nas stabilny.
        authService = Spy(AuthServiceImpl, constructorArgs: [config.authServiceUrl])
        connection = Spy(SatelliteConnectionImpl, constructorArgs: [config.satelliteHost, config.satellitePort])
        telemetryModule = Spy(TelemetryModuleImpl, constructorArgs: [config.telemetryEndpoint])
        commandModule = Spy(CommandModuleImpl, constructorArgs: [config.commandHost, config.commandPort])


        // === KLUCZOWA, OSTATECZNA ZMIANA ===
        // Zamiast tworzyć zwykły obiekt, owijamy go w Spy().
        // To pozwala jednocześnie sprawdzać stan I weryfikować interakcje.
        if (systemName == "ALPHA") {
            repository = Spy(InMemoryFileRepository)
        } else if (systemName == "BETA") {
            repository = Spy(InMemoryDbRepository)
        } else {
            repository = Mock(DataRepository)
        }

        // 3. Stwórz główny obiekt testowany z tymi zależnościami i konfiguracją.
        service = new ReprocessingService(connection, repository, authService, telemetryModule, commandModule, config)
    }
}