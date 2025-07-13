Oczywiście! To jest najlepsza część – teraz, gdy mamy solidny, działający symulator, możemy go "ożywić", dodając mu wewnętrzną logikę i stan, co pozwoli nam na testowanie znacznie bardziej złożonych i realistycznych scenariuszy.

Rozbudujemy nasz SatelliteSimulator o dwa kluczowe, realistyczne systemy:

Zarządzanie Energią: Satelita będzie miał poziom naładowania baterii i panele słoneczne. Jego zdolność do wykonywania komend będzie zależała od poziomu energii. Będziemy mogli symulować cykl dnia i nocy (ładowanie/rozładowywanie).

Stan Podzespołów: Komponenty mogą ulec awarii. Dodamy system "zdrowia" dla kluczowych podzespołów (np. silników manewrowych). Nasz serwis naziemny będzie musiał najpierw sprawdzić stan satelity, zanim wyda krytyczne polecenia.

To pozwoli nam pisać testy, które odpowiadają na pytania: "Co się stanie, gdy spróbujemy zrobić zdjęcie przy niskim stanie baterii?" albo "Czy system poprawnie przerwie operację, jeśli wykryje awarię silnika?".

Krok 1: Ewolucja SatelliteSimulator – Dodajemy Życie
Przebudujemy naszego symulatora, aby był dynamicznym modelem, a nie tylko pasywnym odbiorcą komend.

Zastąp całą zawartość pliku src/test/groovy/com/example/sat/fakes/SatelliteSimulator.groovy tym kodem:

Generated groovy
package com.example.sat.fakes

import com.example.sat.CommandModule
import com.example.sat.SatelliteConnection

class SatelliteSimulator implements SatelliteConnection, CommandModule {

    // === NOWY, DYNAMICZNY STAN WEWNĘTRZNY ===
    double batteryLevel = 95.0 // w procentach
    String solarPanelStatus = "CHARGING" // CHARGING (dzień) lub IDLE (noc)
    String thrusterHealth = "NOMINAL" // NOMINAL, DEGRADED, FAILED
    private static final double POWER_CONSUMPTION_PER_COMMAND = 0.5 // % baterii
    private static final double POWER_CONSUMPTION_TAKE_PHOTO = 5.0 // % baterii

    // ... stary stan ...
    boolean powerOn = true
    boolean connected = false
    String cameraMode = "STANDBY"
    String lastReceivedAuthToken
    List<String> commandHistory = []

    // === NOWA METODA: Symulacja upływu czasu i logiki wewnętrznej ===
    // Ta metoda pozwala nam dynamicznie zmieniać stan symulatora w teście.
    void simulateTimePassage(boolean isDaylight) {
        if (isDaylight) {
            solarPanelStatus = "CHARGING"
            batteryLevel = Math.min(100.0, batteryLevel + 10.0) // Ładujemy baterię
            println "SIMULATOR: Entering daylight. Battery charging. Level: ${batteryLevel}%"
        } else {
            solarPanelStatus = "IDLE"
            batteryLevel -= 2.0 // Podstawowe zużycie energii w nocy
            println "SIMULATOR: Entering darkness. Battery discharging. Level: ${batteryLevel}%"
        }
    }

    // === Zmodyfikowane metody, które zależą od nowego stanu ===

    @Override
    String fetchData(String satelliteId) {
        if (!connected) throw new IllegalStateException("SIMULATOR: Not connected.")
        if (batteryLevel < POWER_CONSUMPTION_TAKE_PHOTO + 5.0) {
            throw new RuntimeException("SIMULATOR: Insufficient power to take and fetch photo. Battery at ${batteryLevel}%.")
        }
        if (cameraMode != "ACTIVE_IMAGING") {
            throw new RuntimeException("SIMULATOR: Cannot fetch data, camera is in ${cameraMode} mode.")
        }
        
        println "SIMULATOR: Fetching simulated meteorological data..."
        batteryLevel -= POWER_CONSUMPTION_TAKE_PHOTO
        return "<SimulatedImageData>${satelliteId}-${System.currentTimeMillis()}</SimulatedImageData>"
    }

    @Override
    String sendCommand(String command) {
        if (!connected) throw new IllegalStateException("SIMULATOR: Not connected.")
        
        println "SIMULATOR: Received command '${command}'"
        commandHistory.add(command)
        batteryLevel -= POWER_CONSUMPTION_PER_COMMAND

        // Sprawdzanie stanu przed wykonaniem komendy
        if (command.contains("THRUSTER") && thrusterHealth == "FAILED") {
            return "NACK: Command '${command}' failed. Thrusters are in FAILED state."
        }
        if (batteryLevel < 10.0) {
            return "NACK: Command '${command}' refused. Critically low power: ${batteryLevel}%."
        }

        switch (command) {
            case "SET_CAMERA_MODE:ACTIVE_IMAGING":
                this.cameraMode = "ACTIVE_IMAGING"
                return "ACK: Camera set to ACTIVE_IMAGING"
            case "SET_CAMERA_MODE:STANDBY":
                this.cameraMode = "STANDBY"
                return "ACK: Camera set to STANDBY"
            // NOWE KOMENDY do sprawdzania stanu
            case "CHECK_POWER_STATUS":
                return "STATUS: Battery: ${String.format('%.2f', batteryLevel)}%, Panels: ${solarPanelStatus}"
            case "CHECK_SYSTEM_HEALTH":
                return "STATUS: Thrusters: ${thrusterHealth}"
            default:
                return "NACK: Unknown command"
        }
    }

    // ... reszta metod (connect, isConnected, disconnect) bez zmian ...
}
Use code with caution.
Groovy
Krok 2: Ulepszenie ReprocessingServiceImpl – Dodajemy "Pre-flight Checks"
Nasz serwis naziemny musi stać się mądrzejszy. Zanim wykona główną pracę, musi sprawdzić, czy satelita jest w dobrym stanie.

Zaktualizuj plik src/main/groovy/com/example/sat/ReprocessingServiceImpl.groovy:

Generated groovy
package com.example.sat

class ReprocessingServiceImpl implements ISingleReprocessor {
// ... definicje pól bez zmian ...

    // ... konstruktor bez zmian ...
    
    // NOWA, prywatna metoda do sprawdzania stanu
    private void performPreflightChecks(CommandModule cmd) {
        println "SERVICE: Performing pre-flight checks..."
        def powerStatus = cmd.sendCommand("CHECK_POWER_STATUS")
        if (powerStatus.contains("NACK") || powerStatus.split(":")[1].trim().split("%")[0].toDouble() < 20.0) {
            throw new IllegalStateException("Pre-flight check failed: Insufficient power. Status: ${powerStatus}")
        }

        def healthStatus = cmd.sendCommand("CHECK_SYSTEM_HEALTH")
        if (healthStatus.contains("FAILED")) {
            throw new IllegalStateException("Pre-flight check failed: Critical component failure. Status: ${healthStatus}")
        }
        println "SERVICE: Pre-flight checks passed."
    }

    @Override
    boolean reprocessDataForSatellite(String satelliteId) {
        // ... walidacja i telemetria bez zmian ...
        try {
            validator.validateSatelliteId(satelliteId)
        } catch (ValidationException e) { /* ... */ }

        telemetryModule.sendEvent("REPROCESSING_STARTED", [satelliteId: satelliteId])
        boolean connectedByThisService = false
        try {
            String token = tokenCache.getToken()
            connection.connect(token)
            connectedByThisService = true

            // ZMIANA: Wykonujemy sprawdzenie stanu przed główną operacją
            performPreflightChecks(commandModule)

            commandModule.sendCommand("SET_CAMERA_MODE:ACTIVE_IMAGING")
            String rawData = connection.fetchData(satelliteId)
            commandModule.sendCommand("SET_CAMERA_MODE:STANDBY")
            
            // ... reszta logiki zapisu i telemetrii bez zmian ...
            return true
        } catch (Exception e) {
            // ...
            return false
        } finally {
            if (connectedByThisService) {
                connection.disconnect()
            }
        }
    }
}
Use code with caution.
Groovy
Krok 3: Nowe, Zaawansowane Scenariusze Testowe z Użyciem Symulatora
Stworzymy nową klasę testową, aby pokazać, jak możemy teraz testować te złożone interakcje.

Nowy plik: src/test/groovy/com/example/sat/AdvancedSimulatorScenariosSpec.groovy

Generated groovy
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
                simulator, Mock(DataRepository), tokenCache,
                Mock(TelemetryModule), simulator, new InputValidatorImpl()
        )
    }

    def "should pass pre-flight checks when simulator is in a healthy state"() {
        given: "a healthy simulator and a service"
        def simulator = new SatelliteSimulator()
        simulator.batteryLevel = 80.0
        simulator.thrusterHealth = "NOMINAL"

        def service = createService(simulator)

        when: "reprocessing is triggered"
        // Używamy `Spy`, aby przechwycić wywołanie prywatnej metody
        // To jest zaawansowana technika, ale pokazuje potęgę Spocka
        def serviceSpy = Spy(service)
        serviceSpy.reprocessDataForSatellite("SAT-HEALTHY")

        then: "pre-flight checks are performed and passed"
        1 * serviceSpy.performPreflightChecks(_)
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
Use code with caution.
Groovy
Podsumowanie Osiągnięć
Dynamiczny Symulator: Nasz SatelliteSimulator nie jest już tylko pasywnym "mockiem". Ma swój wewnętrzny, zmieniający się stan (bateria, stan podzespołów), co pozwala na symulację znacznie bardziej realistycznych warunków.

Testowanie Logiki Defensywnej: Pokazaliśmy, jak testować "inteligentne" zachowanie serwisu naziemnego, który najpierw sprawdza stan systemu zdalnego (performPreflightChecks), zanim podejmie ryzykowne działania.

Testowanie Stanu Wewnętrznego: Możemy teraz pisać testy, które nie tylko sprawdzają, czy metody zostały wywołane, ale także weryfikują, jak zmienił się wewnętrzny stan symulatora po wykonaniu operacji (np. simulator.batteryLevel).

Kompletny Obraz: Masz teraz w projekcie pełne spektrum testów: od prostych testów jednostkowych, przez testy z mockami/fejkami, aż po zaawansowane testy integracyjne z dynamicznym symulatorem. To jest niezwykle solidne i profesjonalne portfolio.

