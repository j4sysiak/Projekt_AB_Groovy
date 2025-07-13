Oczywiście! To jest idealny moment, aby wprowadzić elementy, które są sercem operacji satelitarnych: mechanikę orbitalną i okna komunikacyjne.

W prawdziwym świecie nie można komunikować się z satelitą w dowolnym momencie. Można to robić tylko wtedy, gdy satelita przelatuje nad stacją naziemną. To tzw. "pass" lub "okno komunikacyjne". Przewidywanie tych okien jest kluczowym zadaniem.

Dodamy teraz dwa współpracujące ze sobą moduły, które to zasymulują:

OrbitalMechanicsModule: "Fizyk" naszego systemu. Będzie wiedział, gdzie na orbicie znajduje się satelita w danym momencie.

GroundStationPassPredictor: "Kontroler lotów". Będzie używał danych z modułu mechaniki orbitalnej, aby określić, czy satelita jest obecnie w zasięgu naszej stacji naziemnej.

Nasz nowy, najbardziej realistyczny przepływ będzie wyglądał tak:

Zanim ReprocessingServiceImpl spróbuje cokolwiek zrobić (nawet pobrać token!), najpierw zapyta GroundStationPassPredictor: "Czy mam teraz okno komunikacyjne z satelitą?".

Jeśli nie: Serwis natychmiast przerwie pracę z informacją, że nie ma dostępnego połączenia. To oszczędza wszystkie kolejne kroki.

Jeśli tak: Dopiero wtedy rozpocznie się cały proces walidacji, pobierania tokenu, łączenia się itd.

To wprowadza do naszego systemu fundamentalne ograniczenie czasowe, co jest niezwykle realistyczne.

Krok 1: Stwórz Interfejsy i Implementacje dla Nowych Modułów
1.1. Moduł Mechaniki Orbitalnej
Nowy plik: src/main/groovy/com/example/sat/IOrbitalMechanics.groovy (Interfejs)

Generated groovy
package com.example.sat

// Prosty obiekt do przechowywania współrzędnych
class Coordinates { double latitude; double longitude }

// Interfejs dla modułu, który oblicza pozycję satelity
interface IOrbitalMechanics {
Coordinates getCurrentPosition()
}
Use code with caution.
Groovy
Nowy plik: src/main/groovy/com/example/sat/OrbitalMechanicsImpl.groovy (Implementacja)
Użyjemy prostej funkcji sinus, aby symulować ruch satelity po orbicie.

Generated groovy
package com.example.sat

class OrbitalMechanicsImpl implements IOrbitalMechanics {
private final long orbitPeriodMinutes
private final long epoch = System.currentTimeMillis() // Moment "startu" orbity

    OrbitalMechanicsImpl(long orbitPeriodMinutes = 90L) { // Domyślnie 90-minutowa orbita
        this.orbitPeriodMinutes = orbitPeriodMinutes
    }

    @Override
    Coordinates getCurrentPosition() {
        // Symulujemy ruch satelity po orbicie za pomocą funkcji trygonometrycznej
        long millisSinceEpoch = System.currentTimeMillis() - epoch
        double orbitProgress = (millisSinceEpoch / (orbitPeriodMinutes * 60000.0)) % 1.0
        
        // Sinusoida do symulacji zmiany szerokości geograficznej
        double latitude = 90 * Math.sin(2 * Math.PI * orbitProgress)
        double longitude = (orbitProgress * 360) % 360 - 180 // Prosta symulacja długości

        println "ORBITAL MECHANICS: Current position: Lat ${String.format('%.2f', latitude)}, Lon ${String.format('%.2f', longitude)}"
        return new Coordinates(latitude: latitude, longitude: longitude)
    }
}
Use code with caution.
Groovy
1.2. Moduł Przewidywania Okien Komunikacyjnych
Nowy plik: src/main/groovy/com/example/sat/IPassPredictor.groovy (Interfejs)

Generated groovy
package com.example.sat

interface IPassPredictor {
boolean isCommunicationWindowOpen()
}
Use code with caution.
Groovy
Nowy plik: src/main/groovy/com/example/sat/GroundStationPassPredictorImpl.groovy (Implementacja)

Generated groovy
package com.example.sat

class GroundStationPassPredictorImpl implements IPassPredictor {
private final IOrbitalMechanics orbitalMechanics
private final Coordinates groundStationLocation
private final double requiredMinLatitude // Jak "wysoko" musi być satelita, żebyśmy go widzieli

    GroundStationPassPredictorImpl(IOrbitalMechanics orbitalMechanics, Coordinates groundStationLocation, double requiredMinLatitude = 10.0) {
        this.orbitalMechanics = orbitalMechanics
        this.groundStationLocation = groundStationLocation
        this.requiredMinLatitude = requiredMinLatitude
    }

    @Override
    boolean isCommunicationWindowOpen() {
        Coordinates satellitePosition = orbitalMechanics.getCurrentPosition()
        
        // Prosta logika symulacji: okno jest otwarte, jeśli satelita jest
        // na północnej półkuli (dodatnia szerokość geograficzna) i powyżej pewnego progu.
        boolean isOpen = satellitePosition.latitude > requiredMinLatitude
        
        println "PASS PREDICTOR: Satellite at lat ${String.format('%.2f', satellitePosition.latitude)}. Required > ${requiredMinLatitude}. Window open: ${isOpen}"
        return isOpen
    }
}
Use code with caution.
Groovy
Krok 2: Zaktualizuj ReprocessingServiceImpl
Musimy dodać nową zależność i użyć jej jako pierwszej bramki kontrolnej.

Zaktualizuj plik src/main/groovy/com/example/sat/ReprocessingServiceImpl.groovy:

Generated groovy
package com.example.sat

class ReprocessingServiceImpl implements ISingleReprocessor {
// ... stare zależności ...
final InputValidator validator

    // NOWA ZALEŻNOŚĆ
    final IPassPredictor passPredictor

    // Aktualizujemy konstruktor
    ReprocessingServiceImpl(
            SatelliteConnection connection, DataRepository repository,
            TokenCache tokenCache, TelemetryModule telemetryModule,
            CommandModule commandModule, InputValidator validator,
            IPassPredictor passPredictor) {
        // ... przypisania starych zależności ...
        this.validator = validator
        this.passPredictor = passPredictor
    }

    @Override
    boolean reprocessDataForSatellite(String satelliteId) {
        // KROK 0: SPRAWDZENIE OKNA KOMUNIKACYJNEGO
        if (!passPredictor.isCommunicationWindowOpen()) {
            println "Error during reprocessing: No communication window available at this time."
            // Możemy tu też wysłać specjalny event telemetryczny
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
Krok 3: Nowy Test, Specjalnie dla GroundStationPassPredictor
Napiszemy test, który sprawdza tylko logikę predyktora. Użyjemy do tego ręcznego mocka dla mechaniki orbitalnej.

Nowy plik: src/test/groovy/com/example/sat/PassPredictorSpec.groovy

Generated groovy
package com.example.sat

import com.example.sat.fakes.ManualMockOrbitalMechanics
import spock.lang.Specification

class PassPredictorSpec extends Specification {

    def "should report window is open when satellite is high above horizon"() {
        given: "a predictor and an orbital mechanics module reporting a high satellite position"
        def orbitalMechanics = new ManualMockOrbitalMechanics()
        orbitalMechanics.positionToReturn = new Coordinates(latitude: 45.0, longitude: 15.0)

        // Nasza stacja naziemna (współrzędne nie mają znaczenia dla tej prostej logiki)
        def groundStation = new Coordinates(latitude: 52.0, longitude: 21.0)
        
        def predictor = new GroundStationPassPredictorImpl(orbitalMechanics, groundStation)

        when: "we check for a communication window"
        boolean isOpen = predictor.isCommunicationWindowOpen()

        then: "the window is reported as open"
        isOpen == true
    }

    def "should report window is closed when satellite is below horizon"() {
        given: "a predictor and an orbital mechanics module reporting a low satellite position"
        def orbitalMechanics = new ManualMockOrbitalMechanics()
        // Satelita jest na południowej półkuli, poza naszym zasięgiem
        orbitalMechanics.positionToReturn = new Coordinates(latitude: -30.0, longitude: 100.0)
        
        def groundStation = new Coordinates(latitude: 52.0, longitude: 21.0)
        def predictor = new GroundStationPassPredictorImpl(orbitalMechanics, groundStation)

        when: "we check for a communication window"
        boolean isOpen = predictor.isCommunicationWindowOpen()

        then: "the window is reported as closed"
        isOpen == false
    }
}
Use code with caution.
Groovy
UWAGA: Ten test potrzebuje nowego, ręcznego mocka.

Nowy plik: src/test/groovy/com/example/sat/fakes/ManualMockOrbitalMechanics.groovy

Generated groovy
package com.example.sat.fakes
import com.example.sat.Coordinates
import com.example.sat.IOrbitalMechanics

class ManualMockOrbitalMechanics implements IOrbitalMechanics {
Coordinates positionToReturn = new Coordinates(latitude: 0, longitude: 0)

    @Override
    Coordinates getCurrentPosition() {
        return positionToReturn
    }
}
Use code with caution.
Groovy
Podsumowanie Osiągnięć
Dodaliśmy Wymiar Czasu i Pozycji: Nasz system nie jest już statyczny. Musi brać pod uwagę fizyczną pozycję satelity, co jest fundamentem prawdziwych systemów kosmicznych.

Nowa Warstwa Abstrakcji: Wprowadziliśmy moduły odpowiedzialne za "wiedzę o świecie" (OrbitalMechanics) i "podejmowanie decyzji" (PassPredictor).

Bardziej Inteligentny Serwis: Nasz ReprocessingServiceImpl stał się jeszcze mądrzejszy, podejmując decyzję o rozpoczęciu pracy na podstawie fizycznych ograniczeń.

Czyste, Ukierunkowane Testy: Ponownie, stworzyliśmy dedykowany test (PassPredictorSpec) dla nowej logiki, utrzymując porządek w zestawie testów.

Nasz projekt jest teraz bardzo bliski symulacji podstawowego, ale kompletnego systemu operacji naziemnych dla satelity.

87.7s
