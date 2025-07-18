Fantastycznie! To świetna wiadomość. Oznacza to, że nasza architektura i strategia testowania są solidne, co pozwala nam na sprawną rozbudowę.

Teraz zrobimy coś naprawdę ekscytującego – dodamy moduł, który analizuje dane pobrane z satelity i na ich podstawie podejmuje decyzje. To przeniesie nasz system z poziomu "zbierania danych" na poziom "inteligentnego reagowania".

Wprowadzimy MeteorologicalDataAnalyzer, który będzie skanował pobrane zdjęcie w poszukiwaniu anomalii pogodowych (np. huraganu).

Dlaczego to jest ważne i jak to zrobimy?
Celem zbierania danych z satelity jest ich analiza i wyciąganie wniosków. To jest serce całego systemu.

Nasz nowy, inteligentny przepływ będzie wyglądał tak:

Wprowadzimy nowy komponent: MeteorologicalDataAnalyzer. Jego zadaniem będzie analiza "surowych" danych (w naszej symulacji będzie to string) i wykrywanie w nich słów kluczowych, np. "HURRICANE".

Dodamy nowy komponent: AlertingService. Jego zadaniem będzie wysyłanie powiadomień alarmowych, np. do centrum kryzysowego.

W naszym ReprocessingServiceImpl, po pomyślnym pobraniu i zapisaniu danych, przekażemy je do MeteorologicalDataAnalyzer.

Jeśli analizator wykryje anomalię, ReprocessingServiceImpl użyje AlertingService, aby podnieść alarm.

To pokazuje kompletny cykl życia danych: od zlecenia, przez pobranie, zapis, analizę, aż po podjęcie akcji.

Krok 1: Stwórz Interfejsy i Implementacje dla Nowych Modułów
1.1. Moduł Analizy Danych
Nowy plik: src/main/groovy/com/example/sat/IDataAnalyzer.groovy (Interfejs)

Generated groovy
package com.example.sat

// Prosty obiekt do przechowywania wyniku analizy
class AnalysisResult {
boolean anomalyDetected
String anomalyType // np. "HURRICANE", "VOLCANIC_ASH", "NONE"
String details
}

// Interfejs dla modułu analizującego dane
interface IDataAnalyzer {
AnalysisResult analyze(String rawData)
}
Use code with caution.
Groovy
Nowy plik: src/main/groovy/com/example/sat/MeteorologicalDataAnalyzerImpl.groovy (Implementacja)

Generated groovy
package com.example.sat

class MeteorologicalDataAnalyzerImpl implements IDataAnalyzer {
// Prosta logika: szukamy słów kluczowych w danych tekstowych
@Override
AnalysisResult analyze(String rawData) {
println "ANALYZER: Analyzing data chunk of size ${rawData.length()}..."

        if (rawData.contains("HURRICANE")) {
            println "ANALYZER: Anomaly detected! Type: HURRICANE"
            return new AnalysisResult(
                anomalyDetected: true,
                anomalyType: "HURRICANE",
                details: "Hurricane detected in the provided satellite imagery."
            )
        }
        
        println "ANALYZER: No anomalies detected."
        return new AnalysisResult(anomalyDetected: false, anomalyType: "NONE", details: "Clear weather.")
    }
}
Use code with caution.
Groovy
1.2. Moduł Powiadomień Alarmowych
Nowy plik: src/main/groovy/com/example/sat/IAlertingService.groovy (Interfejs)

Generated groovy
package com.example.sat

interface IAlertingService {
void raiseAlert(String alertType, String message)
}
Use code with caution.
Groovy
Nowy plik: src/main/groovy/com/example/sat/AlertingServiceImpl.groovy (Implementacja)

Generated groovy
package com.example.sat

class AlertingServiceImpl implements IAlertingService {
private final String alertEndpoint

    AlertingServiceImpl(String alertEndpoint) {
        this.alertEndpoint = alertEndpoint
    }

    @Override
    void raiseAlert(String alertType, String message) {
        // W prawdziwym świecie to wysyłałoby SMS, e-mail lub komunikat do innego systemu
        println "ALERT to [${alertEndpoint}]: [${alertType}] - ${message}"
    }
}
Use code with caution.
Groovy
Krok 2: Zaktualizuj ReprocessingServiceImpl
Dodajemy nową logikę analizy i alarmowania na końcu udanego procesu.

Zaktualizuj plik src/main/groovy/com/example/sat/ReprocessingServiceImpl.groovy:

Generated groovy
package com.example.sat

class ReprocessingServiceImpl implements ISingleReprocessor {
// ... stare zależności ...
final IPositionVerifier positionVerifier

    // NOWE ZALEŻNOŚCI
    final IDataAnalyzer dataAnalyzer
    final IAlertingService alertingService

    // Aktualizujemy konstruktor
    ReprocessingServiceImpl(
            SatelliteConnection connection, DataRepository repository,
            TokenCache tokenCache, TelemetryModule telemetryModule,
            CommandModule commandModule, InputValidator validator,
            IPassPredictor passPredictor, IPositionVerifier positionVerifier,
            IDataAnalyzer dataAnalyzer, IAlertingService alertingService) {
        // ... przypisania starych zależności ...
        this.positionVerifier = positionVerifier
        this.dataAnalyzer = dataAnalyzer
        this.alertingService = alertingService
    }

    @Override
    boolean reprocessDataForSatellite(String satelliteId) {
        // ... cała logika aż do zapisu danych pozostaje bez zmian ...
        try {
            // ...
            String rawData = connection.fetchData(satelliteId)
            commandModule.sendCommand("SET_CAMERA_MODE:STANDBY")
            
            String processedData = "PROCESSED: [${rawData}]"
            repository.saveReprocessedData(satelliteId, processedData)
            
            telemetryModule.sendEvent("REPROCESSING_SUCCESS", [satelliteId: satelliteId, dataSize: processedData.length()])
            
            // === NOWA LOGIKA: ANALIZA I ALARMOWANIE ===
            println "SERVICE: Initiating post-processing data analysis..."
            AnalysisResult analysisResult = dataAnalyzer.analyze(rawData)
            if (analysisResult.anomalyDetected) {
                alertingService.raiseAlert(analysisResult.anomalyType, analysisResult.details)
            }
            // ==========================================

            return true
        } catch (Exception e) {
            // ...
            return false
        } finally {
            // ...
        }
    }
}
Use code with caution.
Groovy
Krok 3: Nowy Test, Specjalnie dla Przepływu z Analizą
Napiszemy test integracyjny, który sprawdzi, czy po pobraniu danych zawierających anomalię, zostanie poprawnie podniesiony alarm. Użyjemy do tego naszego niezawodnego wzorca z ręcznymi mockami.

Nowy plik: src/test/groovy/com/example/sat/DataAnalysisFlowSpec.groovy

Generated groovy
package com.example.sat

import com.example.sat.fakes.ManualMockAlertingService
import com.example.sat.fakes.ManualMockConnection
import spock.lang.Specification

class DataAnalysisFlowSpec extends SomeBaseTest { // Dziedziczymy, żeby mieć dostęp do `SYSTEM_CONFIGS`

    def "should raise an alert when an anomaly is detected in satellite data"() {
        given: "a service chain configured to download data containing a hurricane warning"
        def satelliteId = "SAT-STORM-01"
        def config = SYSTEM_CONFIGS["ALPHA"]

        // 1. Tworzymy ręczne mocki dla kluczowych, zmiennych elementów
        def connection = new ManualMockConnection()
        // Symulujemy, że satelita zwraca dane z ostrzeżeniem
        connection.dataToReturn = "<SatelliteData>HURRICANE CAT_5 DETECTED</SatelliteData>"
        
        def alertingService = new ManualMockAlertingService()

        // 2. Tworzymy prawdziwe implementacje dla reszty łańcucha
        def dataAnalyzer = new MeteorologicalDataAnalyzerImpl()
        // Używamy mocków Spocka dla prostszych zależności, które tylko trzeba "wyciszyć"
        def repository = Mock(DataRepository)
        def tokenCache = Mock(TokenCache)
        tokenCache.getToken() >> "VALID-TOKEN"
        def telemetryModule = Mock(TelemetryModule)
        def commandModule = Mock(CommandModule)
        def validator = Mock(InputValidator)
        def passPredictor = Mock(IPassPredictor)
        passPredictor.isCommunicationWindowOpen() >> true // Zawsze jest okno
        def positionVerifier = Mock(IPositionVerifier)

        // 3. Tworzymy nasz obiekt testowany
        def service = new ReprocessingServiceImpl(
                connection, repository, tokenCache, telemetryModule,
                commandModule, validator, passPredictor, positionVerifier,
                dataAnalyzer, alertingService
        )

        when: "the data is reprocessed"
        service.reprocessDataForSatellite(satelliteId)

        then: "a hurricane alert was raised"
        // Sprawdzamy stan naszego ręcznego mocka
        alertingService.alerts.size() == 1
        def alert = alertingService.alerts[0]
        alert.type == "HURRICANE"
        alert.message.contains("Hurricane detected")
    }
}
Use code with caution.
Groovy
UWAGA: Ten test potrzebuje dwóch nowych, ręcznych mocków i aktualizacji istniejącego.

1. Nowy plik: src/test/groovy/com/example/sat/fakes/ManualMockAlertingService.groovy

Generated groovy
package com.example.sat.fakes
import com.example.sat.IAlertingService

class AlertRecord { String type; String message }

class ManualMockAlertingService implements IAlertingService {
List<AlertRecord> alerts = []

    @Override
    void raiseAlert(String alertType, String message) {
        alerts.add(new AlertRecord(type: alertType, message: message))
    }
}
Use code with caution.
Groovy
2. Nowy plik: src/test/groovy/com/example/sat/fakes/ManualMockConnection.groovy

Generated groovy
package com.example.sat.fakes
import com.example.sat.SatelliteConnection

class ManualMockConnection implements SatelliteConnection {
String dataToReturn = "DEFAULT_DATA"
boolean throwExceptionOnFetch = false

    @Override void connect(String authToken) {}
    @Override boolean isConnected() { return true }
    @Override
    String fetchData(String satelliteId) {
        if (throwExceptionOnFetch) throw new RuntimeException("Forced fetch error")
        return dataToReturn
    }
    @Override void disconnect() {}
}
Use code with caution.
Groovy
Podsumowanie Osiągnięć
Kompletny Cykl Życia Danych: Nasz system teraz nie tylko zbiera dane, ale także je analizuje i reaguje na nie, co jest kluczowe w każdym systemie opartym na danych.

Nowa Warstwa Logiki Biznesowej: Dodaliśmy DataAnalyzer i AlertingService, które reprezentują zupełnie nową domenę odpowiedzialności w naszej aplikacji.

Mieszane Techniki Testowania: W nowym teście pokazaliśmy, jak można inteligentnie mieszać techniki – użyliśmy naszych niezawodnych, ręcznych mocków dla kluczowych, weryfikowanych komponentów (Connection, AlertingService), a dla reszty "tła" użyliśmy prostych mocków Spocka, aby je wyciszyć. To jest bardzo profesjonalne i pragmatyczne podejście.

Nasz projekt stał się naprawdę rozbudowaną i realistyczną symulacją. Gratulacje! Co dalej? Może logowanie i audyt operacji? A może jakaś prosta forma interfejsu użytkownika?

52.7s
