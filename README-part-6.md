Fantastycznie! Bardzo się cieszę. To, że udało nam się zbudować solidny, działający i testowalny fundament, pozwala nam teraz na szybką i przyjemną rozbudowę.

Dodajmy teraz kolejny bardzo realistyczny komponent: moduł do walidacji danych wejściowych (InputValidator).

Dlaczego to jest ważne i jak to zrobimy?
W prawdziwym świecie nigdy nie ufamy danym, które przychodzą z zewnątrz. Zanim zaczniemy kosztowne operacje (jak łączenie się z satelitą), musimy najpierw sprawdzić, czy dane wejściowe są w ogóle poprawne. Na przykład, czy ID satelity ma prawidłowy format?

Nasz nowy, ulepszony przepływ będzie wyglądał tak:

Wprowadzimy nowy komponent: InputValidator.

Jego zadaniem będzie sprawdzenie, czy satelliteId ma poprawny format (np. "SAT-", po którym następują cyfry).

Nasz główny serwis (ReprocessingServiceImpl) będzie używał InputValidator na samym początku swojej pracy.

Jeśli walidacja się nie powiedzie, serwis natychmiast przerwie działanie i zwróci błąd, oszczędzając czas i zasoby, które zostałyby zmarnowane na próby autoryzacji, połączenia itd. z niepoprawnymi danymi.

To jest fundamentalna zasada programowania defensywnego: "Fail Fast" (zawieś działanie tak wcześnie, jak to możliwe).

Krok 1: Stwórz Interfejs i Implementację dla InputValidator
1. Nowy plik: src/main/groovy/com/example/sat/InputValidator.groovy (Interfejs)

Generated groovy
package com.example.sat

// Prosty interfejs dla naszego walidatora
interface InputValidator {
// Sprawdza, czy ID satelity jest poprawne.
// Rzuca wyjątkiem, jeśli nie jest.
void validateSatelliteId(String satelliteId) throws ValidationException
}

// Definiujemy nasz własny, dedykowany typ wyjątku dla błędów walidacji
class ValidationException extends IllegalArgumentException {
ValidationException(String message) {
super(message)
}
}
Use code with caution.
Groovy
2. Nowy plik: src/main/groovy/com/example/sat/InputValidatorImpl.groovy (Implementacja)

Generated groovy
package com.example.sat

class InputValidatorImpl implements InputValidator {
// Prosta reguła: ID musi zaczynać się od "SAT-" i mieć łącznie co najmniej 5 znaków.
private static final String ID_PREFIX = "SAT-"
private static final int MIN_LENGTH = 5

    @Override
    void validateSatelliteId(String satelliteId) throws ValidationException {
        println "VALIDATOR: Validating ID: ${satelliteId}"
        if (satelliteId == null) {
            throw new ValidationException("Satellite ID cannot be null.")
        }
        if (!satelliteId.startsWith(ID_PREFIX)) {
            throw new ValidationException("Invalid format: Satellite ID must start with '${ID_PREFIX}'.")
        }
        if (satelliteId.length() < MIN_LENGTH) {
            throw new ValidationException("Invalid format: Satellite ID is too short.")
        }
        println "VALIDATOR: ID '${satelliteId}' is valid."
    }
}
Use code with caution.
Groovy
Krok 2: Zaktualizuj ReprocessingServiceImpl
Musimy dodać nową zależność i użyć jej na początku metody.

Zaktualizuj plik src/main/groovy/com/example/sat/ReprocessingServiceImpl.groovy:

Generated groovy
package com.example.sat

class ReprocessingServiceImpl implements ISingleReprocessor {
// ... stare zależności ...
final SatelliteConnection connection
final DataRepository repository
final TokenCache tokenCache
final TelemetryModule telemetryModule
final CommandModule commandModule

    // NOWA ZALEŻNOŚĆ
    final InputValidator validator

    // Aktualizujemy konstruktor
    ReprocessingServiceImpl(
            SatelliteConnection connection, DataRepository repository,
            TokenCache tokenCache, TelemetryModule telemetryModule,
            CommandModule commandModule, InputValidator validator) {
        this.connection = connection
        this.repository = repository
        this.tokenCache = tokenCache
        this.telemetryModule = telemetryModule
        this.commandModule = commandModule
        this.validator = validator
    }

    @Override
    boolean reprocessDataForSatellite(String satelliteId) {
        // ZMIANA: Walidacja jako PIERWSZY KROK
        try {
            validator.validateSatelliteId(satelliteId)
        } catch (ValidationException e) {
            println "Error during reprocessing: ${e.message}"
            // Nie wysyłamy nawet telemetrii, bo to błąd "śmieciowych" danych wejściowych
            return false // Zawieś działanie natychmiast
        }

        telemetryModule.sendEvent("REPROCESSING_STARTED", [satelliteId: satelliteId])
        boolean connectedByThisService = false
        try {
            // ... reszta logiki pozostaje bez zmian ...
            String token = tokenCache.getToken()
            connection.connect(token)
            // ... itd. ...
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
Krok 3: Nowy, Precyzyjny Test dla InputValidator
Napiszemy test, który sprawdza tylko i wyłącznie logikę walidatora. Nie potrzebujemy do tego żadnych mocków ani klasy bazowej.

Nowy plik: src/test/groovy/com/example/sat/InputValidatorSpec.groovy

Generated groovy
package com.example.sat

import spock.lang.Specification
import spock.lang.Unroll

class InputValidatorSpec extends Specification {

    // Nasz obiekt testowany
    def validator = new InputValidatorImpl()

    def "should pass validation for a correct satellite ID"() {
        when: "a valid ID is validated"
        validator.validateSatelliteId("SAT-12345")

        then: "no exception is thrown"
        noExceptionThrown()
    }

    // Używamy @Unroll, aby Spock wygenerował osobne raporty dla każdego przypadku testowego
    @Unroll
    def "should throw ValidationException for invalid ID: '#invalidId'"() {
        when: "an invalid ID is validated"
        validator.validateSatelliteId(invalidId)

        then: "a ValidationException is thrown"
        thrown(ValidationException)

        // Tabela danych wejściowych dla tego testu
        where:
        invalidId | _
        null      | _
        "INVALID" | _
        "SAT-"    | _
        "SAT-1"   | _ // Zbyt krótkie
    }
}
Use code with caution.
Groovy
Podsumowanie Osiągnięć
Nowy Realistyczny Komponent: Dodaliśmy InputValidator, który jest absolutnie niezbędny w każdym systemie przetwarzającym dane z zewnątrz.

Zasada Programowania "Fail Fast": Nasz główny serwis stał się bardziej "inteligentny" i odporny na błędy. Nie marnuje zasobów na przetwarzanie niepoprawnych danych.

Ukierunkowane Testy Jednostkowe: Zamiast komplikować istniejące testy, stworzyliśmy nowy, bardzo prosty i czytelny test (InputValidatorSpec), który weryfikuje tylko logikę walidacji. To pokazuje, jak budować zestaw testów, w którym każdy test ma jasno określoną odpowiedzialność.

Wykorzystanie Funkcji Spocka (@Unroll): Pokazaliśmy, jak używać testów opartych na danych (Data-Driven Tests) do efektywnego sprawdzania wielu niepoprawnych przypadków w jednym, zwięzłym teście.

Projekt staje się coraz bardziej kompletny i realistyczny. Jak Ci się podoba ten kierunek?

