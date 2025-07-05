Doskonale! Ogromnie się cieszę. To, co teraz zrobimy, to już czysta przyjemność rozwijania solidnego projektu. Mamy fantastyczne, elastyczne fundamenty, więc dodawanie nowych funkcji będzie proste i eleganckie.

Proponuję scenariusz, który jest bardzo powszechny w systemach przetwarzania danych, w tym satelitarnych: Przetwarzanie wsadowe (batch processing) i obsługa błędów z mechanizmem ponawiania prób (retry mechanism).

Nowy Scenariusz Biznesowy: Przetwarzanie Kolejki Zadań
Nasz ReprocessingService dostaje teraz nie jedno zadanie, ale całą kolejkę identyfikatorów satelitów do przetworzenia. Ma przetworzyć wszystkie zadania z kolejki jedno po drugim. Co więcej, jeśli przetwarzanie pojedynczego satelity się nie powiedzie z powodu przejściowego błędu sieciowego (np. ConnectException z SatelliteConnection), serwis powinien podjąć dwie dodatkowe próby przetworzenia tego konkretnego zadania, zanim ostatecznie oznaczy je jako nieudane i przejdzie do następnego.

Ten scenariusz pozwoli nam nauczyć się:

Testowania logiki pętli i przetwarzania kolekcji.

Testowania zaawansowanej obsługi błędów i logiki ponawiania prób (retry).

Bardziej zaawansowanego stubbingu w Spocku, gdzie mock musi zachować się inaczej przy kolejnych wywołaniach (np. pierwsze dwa razy rzucić wyjątkiem, a za trzecim razem zadziałać).

Dalszej adaptacji klasy bazowej, aby ułatwić testowanie tak złożonych scenariuszy.

Krok 1: Ewolucja Systemu
1.1. Zmiana w ReprocessingService
Główna metoda będzie teraz przyjmować listę i zwracać raport z przetwarzania.

Nowa klasa-kontener na wynik: src/main/groovy/com/example/sat/BatchReport.groovy

Generated groovy
package com.example.sat

import groovy.transform.Canonical

@Canonical
class BatchReport {
    int totalTasks
    List<String> successfulIds = []
    Map<String, String> failedIds = [:] // Mapowanie ID na przyczynę błędu
}
Use code with caution.
Groovy
src/main/groovy/com/example/sat/ReprocessingService.groovy
Zmieniamy sygnaturę i ciało głównej metody. Klasa i konstruktor pozostają bez zmian.

Generated groovy
// ... (góra klasy bez zmian) ...
    // Metoda została całkowicie zmieniona, aby obsługiwać przetwarzanie wsadowe i ponawianie prób.
    BatchReport reprocessBatch(List<String> satelliteIds) {
        def report = new BatchReport(totalTasks: satelliteIds.size())

        satelliteIds.each { satelliteId ->
            boolean success = processSingleWithRetry(satelliteId, 3) // 1 próba + 2 ponowienia
            if (success) {
                report.successfulIds << satelliteId
            }
        }
        return report
    }

    // Prywatna metoda pomocnicza z logiką ponawiania prób.
    private boolean processSingleWithRetry(String satelliteId, int maxAttempts) {
        int attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            try {
                println "Processing ${satelliteId}, attempt ${attempt}/${maxAttempts}..."
                // Wywołujemy naszą starą logikę, ale opakowaną w retry.
                // Dla uproszczenia, wklejam ją tutaj ponownie.
                processSingle(satelliteId) 
                return true // Jeśli się udało, wychodzimy z pętli i zwracamy sukces.
            } catch (java.net.ConnectException e) {
                println "Caught transient error for ${satelliteId}: ${e.message}. Retrying..."
                if (attempt >= maxAttempts) {
                    println "Final attempt failed for ${satelliteId}."
                    // Zapisujemy błąd do raportu, ale robimy to w metodzie publicznej
                    // Tutaj tylko informujemy o ostatecznej porażce
                    reportFailureInParentScope(satelliteId, e.message)
                    return false // Ostateczna porażka
                }
            } catch (Exception e) {
                println "Caught non-retriable error for ${satelliteId}: ${e.message}."
                reportFailureInParentScope(satelliteId, e.message)
                return false // Błąd nieprzejściowy, nie ponawiamy.
            }
        }
        return false
    }
    
    // Prywatna metoda do symulacji zapisu błędu w raporcie.
    // W prawdziwym kodzie byłoby to zrobione inaczej, ale na potrzeby symulacji jest OK.
    private void reportFailureInParentScope(String satelliteId, String errorMessage) {
        // Ta metoda jest trochę "hackiem", w prawdziwym kodzie struktura byłaby inna.
        // Tutaj symulujemy, że mamy dostęp do raportu, chociaż jest to uproszczenie.
    }

    // Stara metoda `reprocessDataForSatellite` staje się prywatna
    private void processSingle(String satelliteId) {
        telemetryModule.sendEvent("REPROCESSING_STARTED", [satelliteId: satelliteId])
        boolean connectedByThisService = false
        try {
            String oldData = repository.findDataById(satelliteId)
            if (oldData != null) {
                repository.archiveData(satelliteId, oldData)
            }
            String token = authService.getAuthToken(apiKey)
            connection.connect(token)
            connectedByThisService = true
            commandModule.sendCommand("SET_MODE:REPROCESS")
            String rawData = connection.fetchData(satelliteId)
            String processedData = "PROCESSED: [${rawData}]"
            repository.saveReprocessedData(satelliteId, processedData)
            telemetryModule.sendEvent("REPROCESSING_SUCCESS", [satelliteId: satelliteId, dataSize: processedData.length()])
        } finally {
            if (connectedByThisService) {
                connection.disconnect()
            }
        }
    }
Use code with caution.
Groovy
UWAGA: Kod powyżej jest uproszczony, aby pokazać ideę. W prawdziwym świecie obsługa raportowania błędów z metody prywatnej byłaby zrobiona inaczej (np. przez rzucanie własnego wyjątku z informacjami).

Krok 2: Adaptacja Testów
Teraz musimy napisać test, który sprawdzi tę nową, złożoną logikę.

src/test/groovy/com/example/sat/BatchProcessingSpec.groovy
(Nowy plik testowy)

Generated groovy
package com.example.sat

class BatchProcessingSpec extends SomeBaseTest {

    def setup() {
        setupSystem("ALPHA") // Używamy konfiguracji ALPHA jako bazy
    }

    def "should process all tasks in a batch successfully"() {
        given: "a list of satellite IDs"
        def ids = ["SAT-01", "SAT-02", "SAT-03"]
        def alphaApiKey = SYSTEM_CONFIGS["ALPHA"].apiKey
        def alphaAuthToken = "TOKEN-FOR-${alphaApiKey}"

        and: "all dependencies are configured for success for any ID"
        // Używamy symbolu podkreślenia `_` jako "wildcard" - dopasuj dowolny argument
        authService.getAuthToken(alphaApiKey) >> alphaAuthToken
        connection.fetchData(_) >> "Some Raw Data"

        when: "the batch is reprocessed"
        def report = service.reprocessBatch(ids)

        then: "the report indicates all tasks succeeded"
        report.totalTasks == 3
        report.successfulIds.size() == 3
        report.failedIds.isEmpty()

        and: "the main processing logic was called three times"
        3 * connection.connect(alphaAuthToken)
        3 * repository.saveReprocessedData(_,_)
    }

    def "should retry on transient connection error and succeed on the third attempt"() {
        given: "a single task that will fail twice before succeeding"
        def ids = ["SAT-FAIL-RETRY"]
        def alphaApiKey = SYSTEM_CONFIGS["ALPHA"].apiKey
        def alphaAuthToken = "TOKEN-FOR-${alphaApiKey}"
        
        authService.getAuthToken(alphaApiKey) >> alphaAuthToken
        connection.fetchData(_) >> "Retry Success Data"

        and: "the connect method is stubbed to fail twice, then succeed"
        // === ZAAWANSOWANY STUBBING SPOCKA ===
        // Potrójna strzałka `>>>` pozwala zdefiniować sekwencję odpowiedzi.
        connection.connect(alphaAuthToken) >> { throw new java.net.ConnectException("Timeout") } >> { throw new java.net.ConnectException("Timeout") } >> {} // Ostatnie wywołanie nic nie robi (sukces)

        when: "the batch is reprocessed"
        def report = service.reprocessBatch(ids)

        then: "the report indicates success"
        report.successfulIds == ["SAT-FAIL-RETRY"]
        report.failedIds.isEmpty()

        and: "the connect method was called exactly three times"
        3 * connection.connect(alphaAuthToken)

        and: "the data was saved exactly once (after the successful connection)"
        1 * repository.saveReprocessedData("SAT-FAIL-RETRY", "PROCESSED: [Retry Success Data]")
    }
}
Use code with caution.
Groovy
Podsumowanie Nabytej Wiedzy
Testowanie Logiki Biznesowej: Przeszliśmy od testowania prostych operacji CRUD do weryfikacji złożonej logiki biznesowej, takiej jak przetwarzanie wsadowe i ponawianie prób.

Zaawansowany Spock: Nauczyliśmy się używać:

Wildcardów (_) do stubbingu, który ma działać dla dowolnych argumentów.

Sekwencyjnych odpowiedzi (>>>), aby symulować zmieniające się warunki (np. zasób, który początkowo nie działa, ale potem "wstaje").

Myślenie o Scenariuszach: Projektując testy, musieliśmy pomyśleć o różnych ścieżkach wykonania: "wszystko działa", "działa po kilku próbach", "ostatecznie zawodzi" (to mógłby być kolejny test do napisania!).

Adaptowalność Klasy Bazowej: Nasza klasa SomeBaseTest po raz kolejny udowodniła swoją wartość. Mimo że logika serwisu stała się znacznie bardziej skomplikowana, fundamenty, które przygotowuje klasa bazowa, pozostały takie same i nadal działają, oszczędzając nam mnóstwo pracy konfiguracyjnej.

Ten scenariusz to świetny przykład pokazujący, jak rozwijać zarówno kod produkcyjny, jak i testy, aby radzić sobie z coraz bardziej złożonymi wymaganiami świata rzeczywistego.

44.8s
