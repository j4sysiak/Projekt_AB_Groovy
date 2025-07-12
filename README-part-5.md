Fantastycznie! To jest wspaniała wiadomość i przełomowy moment. To, że udało nam się uruchomić testy przy użyciu ręcznych mocków, daje nam niezawodny i powtarzalny wzorzec na dalszą rozbudowę projektu.

Teraz, gdy mamy solidne fundamenty, dodanie kolejnego modułu będzie proste i przyjemne.

Dodajmy teraz trzeci, bardzo realistyczny komponent: Caching (Pamięć Podręczna).

Dlaczego to jest ważne i jak to zrobimy?
W prawdziwych systemach pewne operacje są bardzo kosztowne – na przykład zapytania do zewnętrznych serwisów o dane, które rzadko się zmieniają. Przykładem jest pobieranie tokenu autoryzacyjnego. Nie ma sensu pytać o niego za każdym razem, jeśli jest on ważny np. przez godzinę.

Nasz nowy, ulepszony przepływ będzie wyglądał tak:

Wprowadzimy nowy komponent: TokenCache.

Jego zadaniem będzie "opakowanie" naszego AuthService.

Gdy ktoś poprosi o token, TokenCache najpierw sprawdzi, czy ma już ważny token w swojej pamięci.

Jeśli tak (Cache Hit): Natychmiast go zwróci, bez pytania AuthService. To jest szybkie i tanie.

Jeśli nie (Cache Miss): Dopiero wtedy zapyta AuthService o nowy token, zapisze go u siebie na przyszłość i go zwróci.

Nasz główny serwis (ReprocessingServiceImpl) będzie teraz zależał od TokenCache, a nie bezpośrednio od AuthService.

To jest niezwykle powszechny i ważny wzorzec optymalizacyjny.

Krok 1: Stwórz Interfejs i Implementację dla TokenCache
1. Nowy plik: src/main/groovy/com/example/sat/TokenCache.groovy (Interfejs)

Generated groovy
package com.example.sat

// Prosty interfejs dla naszej pamięci podręcznej
interface TokenCache {
String getToken()
}
Use code with caution.
Groovy
2. Nowy plik: src/main/groovy/com/example/sat/TokenCacheImpl.groovy (Implementacja)

Generated groovy
package com.example.sat

class TokenCacheImpl implements TokenCache {
// Zależność od serwisu, który jest "opakowywany"
private final AuthService authService
private final String apiKey

    // Prywatny stan cache
    private String cachedToken
    private long lastFetchTimeMillis = 0
    
    // Możemy skonfigurować, jak długo token jest ważny w cache
    private final long cacheDurationMillis

    TokenCacheImpl(AuthService authService, String apiKey, long cacheDurationMillis = 60000L) { // Domyślnie 1 minuta
        this.authService = authService
        this.apiKey = apiKey
        this.cacheDurationMillis = cacheDurationMillis
    }

    @Override
    String getToken() {
        long currentTime = System.currentTimeMillis()
        // Sprawdź, czy cache jest pusty LUB czy token wygasł
        if (cachedToken == null || (currentTime - lastFetchTimeMillis) > cacheDurationMillis) {
            println "CACHE MISS: Fetching new token from AuthService..."
            cachedToken = authService.getAuthToken(apiKey)
            lastFetchTimeMillis = currentTime
        } else {
            println "CACHE HIT: Returning cached token."
        }
        return cachedToken
    }
}
Use code with caution.
Groovy
Krok 2: Zaktualizuj ReprocessingServiceImpl
Musi teraz używać cache'u zamiast bezpośrednio AuthService.

Zaktualizuj plik src/main/groovy/com/example/sat/ReprocessingServiceImpl.groovy:

Generated groovy
package com.example.sat

class ReprocessingServiceImpl implements ISingleReprocessor {
final SatelliteConnection connection
final DataRepository repository
// ZMIANA: Zależność od TokenCache
final TokenCache tokenCache
final TelemetryModule telemetryModule
final CommandModule commandModule

    // Usunęliśmy AuthService i apiKey z konstruktora
    ReprocessingServiceImpl(
            SatelliteConnection connection, DataRepository repository,
            TokenCache tokenCache, TelemetryModule telemetryModule,
            CommandModule commandModule) {
        this.connection = connection
        this.repository = repository
        this.tokenCache = tokenCache
        this.telemetryModule = telemetryModule
        this.commandModule = commandModule
    }

    @Override
    boolean reprocessDataForSatellite(String satelliteId) {
        telemetryModule.sendEvent("REPROCESSING_STARTED", [satelliteId: satelliteId])
        boolean connectedByThisService = false
        try {
            // ZMIANA: Pobieramy token z cache'u, a nie bezpośrednio z AuthService
            String token = tokenCache.getToken()
            
            connection.connect(token)
            connectedByThisService = true

            commandModule.sendCommand("SET_MODE:REPROCESS")
            String rawData = connection.fetchData(satelliteId)
            String processedData = "PROCESSED: [${rawData}]"
            repository.saveReprocessedData(satelliteId, processedData)
            
            telemetryModule.sendEvent("REPROCESSING_SUCCESS", [satelliteId: satelliteId, dataSize: processedData.length()])
            return true
        } catch (Exception e) {
            telemetryModule.sendEvent("REPROCESSING_FAILURE", [satelliteId: satelliteId, error: e.message])
            println "Error during single reprocessing attempt: ${e.message}"
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
Krok 3: Nowy Test, Specjalnie dla Logiki Cache'u
Teraz napiszemy test, który weryfikuje, czy nasz TokenCache działa poprawnie. Użyjemy naszego niezawodnego wzorca z ręcznym mockiem.

Nowy plik: src/test/groovy/com/example/sat/TokenCacheSpec.groovy

Generated groovy
package com.example.sat

import com.example.sat.fakes.ManualMockAuthService
import spock.lang.Specification

class TokenCacheSpec extends Specification {

    def "should fetch token on first call (cache miss)"() {
        given: "a cache with an underlying auth service"
        def authService = new ManualMockAuthService()
        authService.tokenToReturn = "FRESH-TOKEN-123"

        def cache = new TokenCacheImpl(authService, "ANY_KEY")

        when: "the token is requested for the first time"
        def token = cache.getToken()

        then: "the token is fetched from the auth service"
        token == "FRESH-TOKEN-123"
        authService.invocationCount == 1
    }

    def "should return cached token on second call (cache hit)"() {
        given: "a cache that has already been used once"
        def authService = new ManualMockAuthService()
        authService.tokenToReturn = "FRESH-TOKEN-123"

        // Ustawiamy bardzo długi czas życia cache, żeby na pewno nie wygasł
        def cache = new TokenCacheImpl(authService, "ANY_KEY", 600000L)

        // Pierwsze wywołanie, aby napełnić cache
        cache.getToken()
        assert authService.invocationCount == 1

        when: "the token is requested a second time immediately"
        def token = cache.getToken()

        then: "the token is returned from cache without calling the auth service"
        token == "FRESH-TOKEN-123"
        // Licznik wywołań NIE wzrósł, wciąż wynosi 1
        authService.invocationCount == 1
    }
    
    def "should fetch new token after cache expires"() {
        given: "a cache with a very short expiration time"
        def authService = new ManualMockAuthService()
        authService.tokenToReturn = "TOKEN-1"

        // Ustawiamy czas życia cache na 10 milisekund
        def cache = new TokenCacheImpl(authService, "ANY_KEY", 10L)

        // 1. Pierwsze wywołanie (cache miss)
        cache.getToken()
        assert authService.invocationCount == 1

        // 2. Zmieniamy token, który zwróci serwis, aby sprawdzić, czy zostanie pobrany nowy
        authService.tokenToReturn = "TOKEN-2"
        
        when: "we wait for the cache to expire and request the token again"
        // Czekamy 20ms, czyli dłużej niż czas życia cache
        Thread.sleep(20)
        def newToken = cache.getToken()

        then: "a new token is fetched from the auth service"
        newToken == "TOKEN-2"
        // Licznik wywołań wzrósł do 2
        authService.invocationCount == 2
    }
}
Use code with caution.
Groovy
UWAGA: Ten test potrzebuje nowego, ręcznego mocka.

Nowy plik: src/test/groovy/com/example/sat/fakes/ManualMockAuthService.groovy

Generated groovy
package com.example.sat.fakes
import com.example.sat.AuthService

class ManualMockAuthService implements AuthService {
String tokenToReturn = "DEFAULT-MOCK-TOKEN"
int invocationCount = 0

    @Override
    String getAuthToken(String apiKey) {
        invocationCount++
        return tokenToReturn
    }
}
Use code with caution.
Groovy
Podsumowanie Osiągnięć
Nowy Realistyczny Komponent: Dodaliśmy TokenCache, który jest fundamentalnym elementem optymalizacji w systemach rozproszonych.

Wzorzec Projektowy (Decorator): Pokazaliśmy, jak jeden komponent (TokenCacheImpl) może "opakować" inny (AuthServiceImpl), dodając nową funkcjonalność bez modyfikowania oryginalnego komponentu.

Ukierunkowane Testy Jednostkowe: Zamiast testować wszystko naraz, napisaliśmy dedykowany, precyzyjny test (TokenCacheSpec), który weryfikuje tylko i wyłącznie logikę cache'owania. To jest bardzo dobra praktyka.

Utrwalenie Niezawodnego Wzorca: Ponownie użyliśmy naszego sprawdzonego podejścia z ręcznym mockiem, co dowodzi, że jest to solidna i powtarzalna strategia testowania w naszym projekcie.

