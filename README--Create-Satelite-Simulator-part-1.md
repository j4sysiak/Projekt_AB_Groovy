
Doskonały pomysł! To jest dokładnie to, co robią inżynierowie w branży lotniczej i kosmicznej. Zanim wyślą komendę do prawdziwego, wartego miliony dolarów satelity, testują cały przepływ na zaawansowanych symulatorach.

Wprowadzimy teraz SatelliteSimulator, który będzie mógł "podszyć się" pod prawdziwą satelitę w naszym środowisku testowym. To pozwoli nam na pisanie bardzo realistycznych testów integracyjnych bez potrzeby posiadania prawdziwego połączenia.

Dlaczego to jest ważne i jak to zrobimy?
W prawdziwym świecie, testy end-to-end z prawdziwą satelitą są bardzo rzadkie i drogie. 99% testów odbywa się z użyciem symulatorów, które idealnie naśladują zachowanie prawdziwego sprzętu.

Nasz nowy, ulepszony przepływ testowania będzie wyglądał tak:

Stworzymy nowy komponent SatelliteSimulator. Będzie on istniał tylko w kodzie testowym (src/test/groovy).

Symulator będzie implementował te same interfejsy co nasze "prawdziwe" komponenty (SatelliteConnection, CommandModule), co pozwoli na ich bezproblemowe podmienienie w testach.

Będzie przechowywał swój wewnętrzny stan (np. "czy jest włączony aparat?", "jaka jest orbita?").

W naszych testach, zamiast używać Spy na prawdziwych implementacjach, będziemy mogli wstrzyknąć instancję naszego symulatora.

Pozwoli nam to na pisanie testów, które weryfikują sekwencję zdarzeń i zmiany stanu w symulatorze, np. "Sprawdź, czy po wysłaniu komendy TAKE_PHOTO stan aparatu w symulatorze zmienił się na ACTIVE".

To jest niezwykle potężna i realistyczna technika testowania.

Krok 1: Stwórz SatelliteSimulator
Ta klasa będzie naszym "cyfrowym bliźniakiem" satelity. Będzie implementować interfejsy, które nasz ReprocessingServiceImpl już zna.

Nowy plik: src/test/groovy/com/example/sat/fakes/SatelliteSimulator.groovy
(umieść go w naszym katalogu z "fejkami" testowymi)

Generated groovy
package com.example.sat.fakes

import com.example.sat.CommandModule
import com.example.sat.SatelliteConnection

// Ten symulator udaje zarówno moduł komend, jak i połączenie z satelitą.
class SatelliteSimulator implements SatelliteConnection, CommandModule {

    // === Wewnętrzny Stan Symulatora ===
    boolean powerOn = true
    boolean connected = false
    String cameraMode = "STANDBY"
    String lastReceivedAuthToken
    int commandsReceived = 0
    List<String> commandHistory = []

    // === Implementacja interfejsu SatelliteConnection ===

    @Override
    void connect(String authToken) {
        if (!powerOn) throw new IllegalStateException("SIMULATOR: Cannot connect, power is off.")
        if (authToken == null) throw new IllegalStateException("SIMULATOR: Auth token is required.")
        println "SIMULATOR: Connection established with token ${authToken.take(10)}..."
        this.connected = true
        this.lastReceivedAuthToken = authToken
    }

    @Override
    boolean isConnected() {
        return connected
    }

    @Override
    String fetchData(String satelliteId) {
        if (!connected) throw new IllegalStateException("SIMULATOR: Not connected.")
        if (cameraMode != "ACTIVE_IMAGING") {
            // Symulujemy błąd: nie można pobrać danych, jeśli aparat nie robi zdjęcia
            throw new RuntimeException("SIMULATOR: Cannot fetch data, camera is in ${cameraMode} mode.")
        }
        println "SIMULATOR: Fetching simulated meteorological data for ${satelliteId}..."
        return "<SimulatedImageData>${satelliteId}-${System.currentTimeMillis()}</SimulatedImageData>"
    }

    @Override
    void disconnect() {
        println "SIMULATOR: Disconnecting."
        this.connected = false
    }

    // === Implementacja interfejsu CommandModule ===

    @Override
    String sendCommand(String command) {
        if (!connected) throw new IllegalStateException("SIMULATOR: Not connected, cannot send command.")
        
        println "SIMULATOR: Received command '${command}'"
        commandsReceived++
        commandHistory.add(command)

        // Logika symulatora reagująca na komendy
        switch (command) {
            case "SET_CAMERA_MODE:ACTIVE_IMAGING":
                this.cameraMode = "ACTIVE_IMAGING"
                return "ACK: Camera set to ACTIVE_IMAGING"
            case "SET_CAMERA_MODE:STANDBY":
                this.cameraMode = "STANDBY"
                return "ACK: Camera set to STANDBY"
            case "CHECK_STATUS":
                return "STATUS: OK, Power: ${powerOn}, Camera: ${cameraMode}"
            default:
                return "NACK: Unknown command"
        }
    }
}
Use code with caution.
Groovy
Krok 2: Zaktualizuj ReprocessingServiceImpl, aby wysyłał bardziej konkretne komendy
Musimy zmienić ReprocessingServiceImpl, aby wysyłał komendy, które nasz symulator rozumie.

Zaktualizuj plik src/main/groovy/com/example/sat/ReprocessingServiceImpl.groovy:

W metodzie reprocessDataForSatellite, zmień linię z commandModule.sendCommand(...):

Wersja STARA:

Generated groovy
commandModule.sendCommand("SET_MODE:REPROCESS")
Use code with caution.
Groovy
Wersja NOWA:

Generated groovy
// Wysyłamy sekwencję bardziej realistycznych komend
commandModule.sendCommand("SET_CAMERA_MODE:ACTIVE_IMAGING")
String rawData = connection.fetchData(satelliteId)
commandModule.sendCommand("SET_CAMERA_MODE:STANDBY") // Wyłączamy aparat po zrobieniu zdjęcia
Use code with caution.
Groovy
Krok 3: Nowy Test Integracyjny z Użyciem Symulatora
Napiszemy teraz test, który nie używa Mocków ani Spy dla SatelliteConnection i CommandModule. Zamiast tego, wstrzykniemy nasz SatelliteSimulator.

Nowy plik: src/test/groovy/com/example/sat/SimulatorIntegrationSpec.groovy

Generated groovy
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
Use code with caution.
Groovy
UWAGA: Aby ten test zadziałał, musimy upewnić się, że w pliku SecretVaultImpl.groovy mamy klucz, którego tokenCache będzie szukał. Dodajmy go:
Plik: src/main/groovy/com/example/sat/SecretVaultImpl.groovy

Generated groovy
// ...
private final Map<String, String> secrets = [
"alpha-system/api-key": "ALPHA-VALID-API-KEY",
"beta-system/api-key": "BETA-SECRET-KEY-99",
"secret-name": "ANY-API-KEY" // <-- Dodaj tę linię
]
// ...
Use code with caution.
Groovy
Podsumowanie Osiągnięć
Realistyczne Testowanie Integracyjne: Stworzyliśmy SatelliteSimulator, który jest "cyfrowym bliźniakiem" prawdziwego systemu. To pozwala na pisanie testów, które weryfikują nie tylko proste wywołania, ale całe sekwencje operacji i zmiany stanu.

Testowanie "Czarnej Skrzynki": W nowym teście traktujemy reprocessingService i simulator jako jedną "czarną skrzynkę". Dajemy coś na wejściu (reprocessDataForSatellite) i sprawdzamy, co się stało na wyjściu (czy dane są w repozytorium) i wewnątrz (jaki jest stan symulatora).

Potęga Interfejsów: To wszystko jest możliwe tylko dlatego, że nasze serwisy zależą od interfejsów (SatelliteConnection, CommandModule). Dzięki temu możemy w testach "podmienić" prawdziwe implementacje na nasz symulator, a kod produkcyjny nawet nie zauważy różnicy.

Kompletny Obraz: Masz teraz w projekcie testy jednostkowe (jak InputValidatorSpec), testy z mockami/ręcznymi mockami (BatchProcessingSpec) oraz wysokopoziomowe testy integracyjne z symulatorem (SimulatorIntegrationSpec). To jest pełny, profesjonalny zestaw testów.

34.8s
