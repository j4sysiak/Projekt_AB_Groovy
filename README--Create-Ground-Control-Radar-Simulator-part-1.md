Oczywiście! To jest fantastyczny pomysł, który doskonale uzupełnia nasz system. Radar kontroli naziemnej (Ground Control Radar) to idealny komponent, ponieważ wprowadza kolejną, bardzo ważną koncepcję: weryfikację stanu obiektu za pomocą niezależnego sensora.

Zanim wyślemy komendę do satelity, chcemy mieć 100% pewności, że znamy jego dokładną pozycję. Nasz OrbitalMechanicsModule daje nam przewidywaną pozycję na podstawie modelu matematycznego. Radar da nam rzeczywisty, zmierzony odczyt.

Dlaczego to jest ważne i jak to zrobimy?
W prawdziwym świecie modele orbitalne mogą się z czasem rozjeżdżać z rzeczywistością. Dlatego stacje naziemne regularnie "pingują" satelity radarem, aby potwierdzić ich pozycję i skorygować modele.

Nasz nowy, ulepszony przepływ będzie wyglądał tak:

Wprowadzimy nowy komponent: GroundControlRadar.

Jego zadaniem będzie "pingowanie" satelity i zwracanie jego zmierzonej pozycji.

Dodamy nowy serwis: PositionVerificationService. Jego zadaniem będzie:

Pobranie przewidywanej pozycji z OrbitalMechanicsModule.

Pobranie zmierzonej pozycji z GroundControlRadar.

Porównanie obu pozycji. Jeśli różnica jest zbyt duża (tzw. "delta"), oznacza to, że nasz model orbitalny jest nieaktualny i operacja jest zbyt ryzykowna.

Nasz ReprocessingServiceImpl użyje PositionVerificationService jako kolejnej bramki kontrolnej, zaraz po sprawdzeniu okna komunikacyjnego.

To pokazuje bardzo dojrzały proces operacyjny: "Czy go widzę? Tak. Czy jest tam, gdzie myślę, że jest? Tak. Dopiero teraz zaczynam z nim rozmawiać."

Krok 1: Stwórz Interfejs i Implementację dla GroundControlRadar
1. Nowy plik: src/main/groovy/com/example/sat/IGroundControlRadar.groovy (Interfejs)

Generated groovy
package com.example.sat

// Interfejs dla naszego radaru
interface IGroundControlRadar {
// Pingujemy satelitę o danym ID i oczekujemy jego zmierzonych współrzędnych
Coordinates pingSatellite(String satelliteId)
}
Use code with caution.
Groovy
2. Nowy plik: src/main/groovy/com/example/sat/GroundControlRadarImpl.groovy (Prosta symulacja)

Generated groovy
package com.example.sat

class GroundControlRadarImpl implements IGroundControlRadar {
// W prawdziwym świecie to byłaby skomplikowana komunikacja z anteną radarową.
// Tutaj symulujemy, że radar ma "prawdziwą" wiedzę o pozycji satelity.
// Możemy tu zasymulować np. drobne zakłócenia atmosferyczne.

    private final IOrbitalMechanics truePositionProvider // Radar ma dostęp do "prawdy"

    GroundControlRadarImpl(IOrbitalMechanics truePositionProvider) {
        this.truePositionProvider = truePositionProvider
    }

    @Override
    Coordinates pingSatellite(String satelliteId) {
        println "RADAR: Pinging satellite ${satelliteId}..."
        Coordinates truePosition = truePositionProvider.getCurrentPosition()
        
        // Symulujemy drobny błąd pomiaru
        double measuredLat = truePosition.latitude + (Math.random() - 0.5) * 0.1
        double measuredLon = truePosition.longitude + (Math.random() - 0.5) * 0.1

        println "RADAR: Ping successful. Measured position: Lat ${String.format('%.4f', measuredLat)}, Lon ${String.format('%.4f', measuredLon)}"
        return new Coordinates(latitude: measuredLat, longitude: measuredLon)
    }
}
Use code with caution.
Groovy
Krok 2: Stwórz PositionVerificationService
To jest nowy serwis, który zawiera logikę porównywania pozycji.

1. Nowy plik: src/main/groovy/com/example/sat/IPositionVerifier.groovy (Interfejs)

Generated groovy
package com.example.sat

interface IPositionVerifier {
// Weryfikuje, czy przewidywana i zmierzona pozycja są zgodne
void verifyPosition(String satelliteId) throws PositionMismatchException
}

class PositionMismatchException extends RuntimeException {
PositionMismatchException(String message) {
super(message)
}
}
Use code with caution.
Groovy
2. Nowy plik: src/main/groovy/com/example/sat/PositionVerificationServiceImpl.groovy (Implementacja)

Generated groovy
package com.example.sat

class PositionVerificationServiceImpl implements IPositionVerifier {
private final IOrbitalMechanics orbitalModel
private final IGroundControlRadar radar
private final double maxAllowedDelta // Maksymalna dopuszczalna różnica w stopniach

    PositionVerificationServiceImpl(IOrbitalMechanics orbitalModel, IGroundControlRadar radar, double maxAllowedDelta = 0.5) {
        this.orbitalModel = orbitalModel
        this.radar = radar
        this.maxAllowedDelta = maxAllowedDelta
    }

    @Override
    void verifyPosition(String satelliteId) throws PositionMismatchException {
        println "POSITION VERIFIER: Starting position verification for ${satelliteId}..."
        
        // 1. Pobierz przewidywaną pozycję z modelu
        Coordinates predictedPosition = orbitalModel.getCurrentPosition()
        
        // 2. Pobierz zmierzoną pozycję z radaru
        Coordinates measuredPosition = radar.pingSatellite(satelliteId)

        // 3. Oblicz różnicę
        double delta = Math.sqrt(
                Math.pow(predictedPosition.latitude - measuredPosition.latitude, 2) +
                Math.pow(predictedPosition.longitude - measuredPosition.longitude, 2)
        )
        println "POSITION VERIFIER: Predicted vs Measured delta is ${String.format('%.4f', delta)}. Max allowed: ${maxAllowedDelta}"

        // 4. Jeśli różnica jest zbyt duża, rzuć wyjątkiem
        if (delta > maxAllowedDelta) {
            throw new PositionMismatchException("Position mismatch for ${satelliteId}. Delta of ${delta} exceeds threshold of ${maxAllowedDelta}.")
        }
        
        println "POSITION VERIFIER: Position verified successfully."
    }
}
Use code with caution.
Groovy
Krok 3: Zaktualizuj ReprocessingServiceImpl
Dodajemy nową bramkę kontrolną.

Zaktualizuj plik src/main/groovy/com/example/sat/ReprocessingServiceImpl.groovy:

Generated groovy
package com.example.sat

class ReprocessingServiceImpl implements ISingleReprocessor {
// ... stare zależności ...
final IPassPredictor passPredictor

    // NOWA ZALEŻNOŚĆ
    final IPositionVerifier positionVerifier

    // Aktualizujemy konstruktor
    ReprocessingServiceImpl(
            SatelliteConnection connection, DataRepository repository,
            TokenCache tokenCache, TelemetryModule telemetryModule,
            CommandModule commandModule, InputValidator validator,
            IPassPredictor passPredictor, IPositionVerifier positionVerifier) {
        // ... przypisania starych zależności ...
        this.passPredictor = passPredictor
        this.positionVerifier = positionVerifier
    }

    @Override
    boolean reprocessDataForSatellite(String satelliteId) {
        // KROK 0: Sprawdzenie okna komunikacyjnego
        if (!passPredictor.isCommunicationWindowOpen()) { /* ... */ }

        // KROK 0.5: WERYFIKACJA POZYCJI
        try {
            positionVerifier.verifyPosition(satelliteId)
        } catch (PositionMismatchException e) {
            println "Error during reprocessing: ${e.message}"
            // Wyślijmy alert, że model orbitalny wymaga korekty
            telemetryModule.sendEvent("ORBITAL_MODEL_DRIFT_DETECTED", [satelliteId: satelliteId, details: e.message])
            return false
        }
        
        try {
            validator.validateSatelliteId(satelliteId)
        } catch (ValidationException e) { /* ... */ }

        // ... reszta logiki metody pozostaje bez zmian ...
    }
}
Use code with caution.
Groovy
Krok 4: Nowy Test, Specjalnie dla PositionVerificationService
Sprawdzimy, czy nasz weryfikator poprawnie wykrywa zgodność i rozbieżność pozycji. Użyjemy do tego naszych niezawodnych, ręcznych mocków.

Nowy plik: src/test/groovy/com/example/sat/PositionVerifierSpec.groovy

Generated groovy
package com.example.sat

import com.example.sat.fakes.ManualMockGroundControlRadar
import com.example.sat.fakes.ManualMockOrbitalMechanics
import spock.lang.Specification

class PositionVerifierSpec extends Specification {

    def "should pass verification when predicted and measured positions are close"() {
        given: "a model and a radar reporting similar positions"
        def orbitalModel = new ManualMockOrbitalMechanics()
        orbitalModel.positionToReturn = new Coordinates(latitude: 45.0, longitude: 20.0)

        def radar = new ManualMockGroundControlRadar()
        radar.positionToReturn = new Coordinates(latitude: 45.1, longitude: 20.1) // Bliska pozycja

        def verifier = new PositionVerificationServiceImpl(orbitalModel, radar)

        when: "position is verified"
        verifier.verifyPosition("SAT-OK")

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "should throw exception when positions mismatch significantly"() {
        given: "a model and a radar reporting distant positions"
        def orbitalModel = new ManualMockOrbitalMechanics()
        orbitalModel.positionToReturn = new Coordinates(latitude: 45.0, longitude: 20.0)

        def radar = new ManualMockGroundControlRadar()
        radar.positionToReturn = new Coordinates(latitude: 46.0, longitude: 21.0) // Znaczna różnica

        def verifier = new PositionVerificationServiceImpl(orbitalModel, radar)

        when: "position is verified"
        verifier.verifyPosition("SAT-DRIFT")

        then: "a PositionMismatchException is thrown"
        thrown(PositionMismatchException)
    }
}
Use code with caution.
Groovy
UWAGA: Ten test potrzebuje nowego, ręcznego mocka.

Nowy plik: src/test/groovy/com/example/sat/fakes/ManualMockGroundControlRadar.groovy

Generated groovy
package com.example.sat.fakes
import com.example.sat.Coordinates
import com.example.sat.IGroundControlRadar

class ManualMockGroundControlRadar implements IGroundControlRadar {
Coordinates positionToReturn = new Coordinates(latitude: 0, longitude: 0)

    @Override
    Coordinates pingSatellite(String satelliteId) {
        return positionToReturn
    }
}
Use code with caution.
Groovy
Podsumowanie Osiągnięć
Dodaliśmy Weryfikację Sensoryczną: Nasz system nie polega już tylko na modelach. Używa niezależnego "sensora" (radaru) do potwierdzenia stanu rzeczywistego, co jest kluczowe w systemach o wysokiej niezawodności.

Nowa Warstwa Logiki Biznesowej: Wprowadziliśmy PositionVerificationService, który zawiera specyficzną, ważną logikę biznesową (porównywanie pozycji).

Bardziej Solidny Proces: Nasz główny proces (reprocessDataForSatellite) zyskał kolejną bramkę kontrolną, co czyni go jeszcze bardziej odpornym na błędy i nieoczekiwane sytuacje.

Utrwalenie Wzorca Testowania: Ponownie, użyliśmy naszego sprawdzonego podejścia: stworzyliśmy dedykowany, precyzyjny test jednostkowy (PositionVerifierSpec) dla nowej logiki, używając prostych, ręcznych mocków.

Nasz projekt stał się naprawdę zaawansowaną symulacją. Co myślisz o dodaniu teraz elementu, który będzie reagował na dane z satelity? Na przykład, jeśli na zdjęciu meteorologicznym zostanie wykryty huragan, system powinien uruchomić procedurę alarmową.

47.5s
