package com.example.sat.dsl


import spock.lang.Specification

@RestDSL
class MyRestDslSpec extends Specification {

    def "should execute a GET request using the injected DSL method"() {
        when: "a GET request is defined and executed in a single block"

        /*
        Czytamy host = "http://example.com" z Twojego closure i zapisujemy go w config.host.
        Następnie całkowicie ignorujemy tę wartość.
        Zamiast tego, tworzymy nowy, tymczasowy WireMockServer, który startuje na losowym porcie (np. localhost:54321).

        Nasz SimpleHttpClient jest konfigurowany, aby "rozmawiał" właśnie z tym tymczasowym serwerem.
        Wniosek:
        Pole host w tej implementacji jest tylko opisowym "rekwizytem" w teście.
        Nie ma żadnego wpływu na to, gdzie faktycznie wysyłane jest żądanie.
        Czy mogłoby mieć? Oczywiście! Moglibyśmy zmodyfikować RestTestExecutor,
               aby zamiast startować WireMockServer, używał new SimpleHttpClient(config.host) i wysyłał żądanie do prawdziwego,
                     działającego serwera.
                     Ale wtedy nasz test przestałby być testem jednostkowym klienta, a stałby się testem integracyjnym.
        */

        Map res = get("/health") {
            host = "http://example.com"  // ten url nie ma znaczenia
            defaultRespondMediaType = "application/json"
        }

        then: "the response is successful"
        res.statusCode == 200
        res.json.status == "OK_FROM_DSL"
    }
}


/*
OPIS:


Model
Dokładnie tak! Twoja obserwacja jest w 100% trafna.
W całym tym skomplikowanym mechanizmie DSL i transformacji AST, ostatecznym celem jest wykonanie bardzo prostej, konkretnej akcji:
Wysłanie żądania GET na ścieżkę /health do serwera, który uruchomiliśmy w locie.
Cała ta "magia" służy tylko i wyłącznie temu, aby sposób, w jaki opisujemy tę akcję w naszym teście, był jak najbardziej elegancki, czytelny i reużywalny.
Śledząc Przepływ od DSL do Endpointu
Prześledźmy całą drogę, którą pokonuje informacja "/health":
Test (MyRestDslSpec.groovy):
Ty piszesz: get("/health") { ... }
Podajesz "/health" jako pierwszy argument do (wstrzykniętej) metody get.
Transformacja AST (RestDSLTransformation.groovy):
Transformacja w trakcie kompilacji zamieniła Twoje get(...) na:
RestTestExecutor.execute({ ... }, "get", "/health")
"/health" zostało przekazane jako trzeci argument (path) do metody execute.
Logika Wykonawcza (RestTestExecutor.groovy):
Metoda static Map execute(Closure ..., String method, String path) otrzymuje path o wartości "/health".
Wewnątrz tej metody dzieją się dwie kluczowe rzeczy:
Programowanie WireMocka:
code
Groovy
wireMockServer.stubFor(
    // Używamy `path` do zdefiniowania reguły
    request(method.toUpperCase(), urlEqualTo(path))
    // ...
)
Mówimy WireMockowi: "Oczekuj żądania na ścieżce "/health"".
Wywołanie Klienta:
code
Groovy
return client.getData(path)
Mówimy naszemu SimpleHttpClient, aby faktycznie wysłał żądanie na ścieżkę "/health".
Klient HTTP (SimpleHttpClient.groovy):
Metoda getData("/health") jest wywoływana.
SimpleHttpClient wie, że jego serverUrl to adres naszego tymczasowego WireMocka (np. http://localhost:12345).
Skleja te dwie części, tworząc pełny adres: http://localhost:12345/health.
Wysyła żądanie GET na ten adres.
Serwer (WireMockServer):
Odbiera przychodzące żądanie GET http://localhost:12345/health.
Przegląda swoje zaprogramowane "reguły" (stuby).
Znajduje regułę, która pasuje do GET na /health.
Zwraca odpowiedź, którą zdefiniowaliśmy (status 200, ciało {"status":"OK_FROM_DSL"}).
W skrócie:
Tak, na samym końcu tego całego łańcucha, faktycznie wysyłamy żądanie na endpoint /health. Cały ten skomplikowany mechanizm DSL służy tylko temu, aby ten proces był opisany w teście w jednej, pięknej linijce, a cała brudna robota (startowanie serwera, programowanie go, wywoływanie klienta, zatrzymywanie serwera) była przed Tobą ukryta.
User
a ten zapis , dokładnie host, czy nie powinien właśnie wskazywać na serwer np. GroundControlServerApp gdzie implementowany jest endpoint healt
dlaczego host = "http://example.com"  i co to w ogóle jest defaultRespondMediaType = "application/json" ?
code
Code
Map res = get("/health") {
            host = "http://example.com"
            defaultRespondMediaType = "application/json"
        }
warning
Model
To są genialne pytania. Trafiasz w samo sedno tego, jak ten DSL działa i co w nim jest ważne, a co jest tylko "ozdobnikiem".
Odpowiedzmy na nie po kolei.
Pytanie 1: Dlaczego host = "http://example.com" i czy nie powinien wskazywać na GroundControlServerApp?
Krótka odpowiedź: Masz rację, intuicyjnie powinien. Ale w tej konkretnej implementacji naszego DSL-a, to pole jest całkowicie ignorowane. Służy ono tylko jako dokumentacja lub opis wewnątrz testu.
Dłuższe wyjaśnienie:
Spójrzmy na logikę w RestTestExecutor.groovy:
code
Groovy
// w RestTestExecutor.groovy
static Map execute(Closure serverConfigClosure, String method, String path) {
    // ...
    def config = new ServerConfig()
    serverConfigClosure.delegate = config
    serverConfigClosure.call() // <-- Tutaj pola `host` i `defaultRespondMediaType` są ustawiane w obiekcie `config`

    // Uruchamiamy NOWY, PUSTY serwer WireMock w locie
    def wireMockServer = new WireMockServer()
    wireMockServer.start() // <-- Startuje na LOSOWYM, wolnym porcie
    try {
        // Tworzymy klienta, który wskazuje na adres TEGO TYMCZASOWEGO SERWERA
        def client = new SimpleHttpClient(wireMockServer.baseUrl())

        // ... programujemy WireMocka i wykonujemy żądanie ...
    } finally {
        wireMockServer.stop()
    }
}
Zauważ, co się dzieje:
Czytamy host = "http://example.com" z Twojego closure i zapisujemy go w config.host.
Następnie całkowicie ignorujemy tę wartość.
Zamiast tego, tworzymy nowy, tymczasowy WireMockServer, który startuje na losowym porcie (np. localhost:54321).
Nasz SimpleHttpClient jest konfigurowany, aby "rozmawiał" właśnie z tym tymczasowym serwerem.
Wniosek: Pole host w tej implementacji jest tylko opisowym "rekwizytem" w teście. Nie ma żadnego wpływu na to, gdzie faktycznie wysyłane jest żądanie.
Czy mogłoby mieć? Oczywiście! Moglibyśmy zmodyfikować RestTestExecutor, aby zamiast startować WireMockServer, używał new SimpleHttpClient(config.host) i wysyłał żądanie do prawdziwego, działającego serwera. Ale wtedy nasz test przestałby być testem jednostkowym klienta, a stałby się testem integracyjnym.
Pytanie 2: Co to jest defaultRespondMediaType = "application/json"?
To pole jest używane i jest bardzo ważne.
defaultRespondMediaType: Wymyśliliśmy tę nazwę sami. Oznacza ona "domyślny typ mediów (MIME type) w odpowiedzi".
"application/json": To jest standardowy sposób, w jaki serwer HTTP informuje przeglądarkę lub klienta: "Dane, które Ci wysyłam w ciele (body) odpowiedzi, są w formacie JSON".
Spójrzmy, gdzie go używamy w RestTestExecutor.groovy:
code
Groovy
// w RestTestExecutor.groovy
// ...
wireMockServer.stubFor(
    request(...)
        .willReturn(aResponse()
            .withStatus(200)
            // Używamy wartości z `config` do ustawienia nagłówka odpowiedzi
            .withHeader("Content-Type", config.defaultRespondMediaType)
            .withBody('{"status":"OK_FROM_DSL"}')
        )
)
// ...
Co się dzieje:
Wartość "application/json" z Twojego closure trafia do config.defaultRespondMediaType.
Następnie używamy jej, aby zaprogramować WireMockServer.
Mówimy mu: "Gdy będziesz odpowiadał na to żądanie, dodaj do swojej odpowiedzi nagłówek Content-Type o wartości application/json".
Dlaczego to jest ważne?
Chociaż w naszych prostych testach nie ma to krytycznego znaczenia, w prawdziwym świecie wiele klientów i frameworków patrzy na ten nagłówek, aby zdecydować, jak zinterpretować (sparsować) ciało odpowiedzi. Jeśli serwer wysyła JSON, ale nie ustawi tego nagłówka, niektóre klienty mogą potraktować go jako zwykły tekst i nie uruchomić parsera JSON.
W skrócie:
host: W tej implementacji to tylko komentarz/dekoracja.
defaultRespondMediaType: To jest rzeczywista konfiguracja, która wpływa na to, jak nasz tymczasowy serwer WireMock będzie się zachowywał.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.

 */