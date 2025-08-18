
ThinkingThoughts
(experimental)
Auto
Expand to view model thoughts

chevron_right
Świetny pomysł! To idealne podejście – "pobawić się" nową koncepcją w kontrolowany sposób, nie ryzykując destabilizacji działającego kodu.
Stworzymy mały, samowystarczalny przykład, który będzie żył obok Twojego istniejącego systemu. Będzie on ilustrował ideę HATEOAS na prostym przykładzie zarządzania misją. Zbudujemy go tak, aby był "przytulony" do projektu, czyli użyjemy tych samych narzędzi (Spock, Groovy, SparkJava), ale będzie on w pełni niezależny.
Projekt: "Inteligentny Kokpit Misji" (HATEOAS Demo)
Cel: Zbudujemy mini-serwer, który zarządza cyklem życia jednej misji. Klient będzie mógł "nawigować" po stanie misji, podążając za linkami, które podsuwa mu serwer.
Scenariusz, który zaimplementujemy:
Klient pyta o stan misji.
Jeśli misja jest w stanie PLANNED, serwer daje mu link do akcji "Uruchom misję".
Klient "klika" ten link (wysyła żądanie).
Stan misji zmienia się na IN_PROGRESS.
Klient znowu pyta o stan. Serwer widzi, że misja jest w toku i daje mu link do akcji "Zakończ misję".
Klient "klika" ten link.
Stan misji zmienia się na COMPLETED.
Klient znowu pyta o stan. Serwer widzi, że misja jest zakończona i nie daje już żadnych linków do akcji.
Krok 1: Stwórz nowy plik testowy
Stwórz nowy plik w src/test/groovy/com/example/sat/hateoas/HateoasMissionControlSpec.groovy. Utworzenie nowego podfolderu hateoas pomoże utrzymać porządek.
Krok 2: Kod - Mini-serwer i "Inteligentny" Klient w jednym teście
Wklej poniższy kod do nowo utworzonego pliku. Jest to kompletny, działający przykład – zawiera zarówno logikę serwera, jak i test, który go używa. Przeczytaj uważnie komentarze.
code
Groovy
// src/test/groovy/com/example/sat/hateoas/HateoasMissionControlSpec.groovy
package com.example.sat.hateoas

import com.example.sat.http.SimpleHttpClient
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spark.Service
import spock.lang.Specification

class HateoasMissionControlSpec extends Specification {

    // Nasz mini-serwer do demonstracji HATEOAS
    Service missionServer
    // Nasz klient do komunikacji
    SimpleHttpClient client
    // Adres URL serwera
    String serverUrl

    // === Stan naszej prostej misji na serwerze ===
    // W prawdziwej aplikacji byłoby to w bazie danych.
    static String missionState = "PLANNED"

    // Uruchamiamy serwer przed wszystkimi testami w tej klasie
    def setupSpec() {
        missionServer = Service.ignite()
        missionServer.port(0) // Uruchom na losowym porcie

        // --- Definicja API naszego serwera HATEOAS ---

        // 1. Główny endpoint, który zwraca stan misji i dostępne akcje
        missionServer.get("/mission") { req, res ->
            res.type("application/json")
            
            def baseUrl = req.scheme() + "://" + req.host()
            def response = [
                missionId: "DEMO-MISSION-001",
                status: missionState,
                _links: [
                    // Zawsze podajemy link do samego siebie
                    self: [ href: baseUrl + "/mission" ]
                ]
            ]

            // === Logika HATEOAS: Serwer decyduje, jakie akcje są dostępne ===
            if (missionState == "PLANNED") {
                response._links.start = [ href: baseUrl + "/mission/start", method: "POST" ]
            } else if (missionState == "IN_PROGRESS") {
                response._links.complete = [ href: baseUrl + "/mission/complete", method: "POST" ]
            }

            return JsonOutput.toJson(response)
        }

        // 2. Endpoint do uruchamiania misji (istnieje tylko, gdy misja jest PLANNED)
        missionServer.post("/mission/start") { req, res ->
            if (missionState == "PLANNED") {
                missionState = "IN_PROGRESS"
                res.status(200)
                return JsonOutput.toJson([message: "Mission started!"])
            } else {
                res.status(409) // 409 Conflict - tej akcji nie można teraz wykonać
                return JsonOutput.toJson([error: "Mission is not in PLANNED state."])
            }
        }

        // 3. Endpoint do kończenia misji (istnieje tylko, gdy misja jest IN_PROGRESS)
        missionServer.post("/mission/complete") { req, res ->
            if (missionState == "IN_PROGRESS") {
                missionState = "COMPLETED"
                res.status(200)
                return JsonOutput.toJson([message: "Mission completed!"])
            } else {
                res.status(409) // 409 Conflict
                return JsonOutput.toJson([error: "Mission is not IN_PROGRESS."])
            }
        }
        
        missionServer.awaitInitialization()
        serverUrl = "http://localhost:${missionServer.port()}"
        client = new SimpleHttpClient(serverUrl)
    }

    // Zamykamy serwer po wszystkich testach
    def cleanupSpec() {
        missionServer?.stop()
    }

    // Resetujemy stan misji przed każdym testem
    def cleanup() {
        missionState = "PLANNED"
    }


    // === TEST, KTÓRY SYMULUJE "INTELIGENTNEGO" KLIENTA ===
    def "client should navigate the mission lifecycle by following links"() {
        when: "client checks the initial state of the mission"
        println "--- ETAP 1: Sprawdzanie stanu początkowego ---"
        def initialStateResponse = client.getData("/mission")
        def initialState = initialStateResponse.json

        then: "mission is PLANNED and server provides a 'start' link"
        initialState.status == "PLANNED"
        initialState._links.start != null
        initialState._links.complete == null // Nie ma jeszcze linku do zakończenia

        when: "client follows the 'start' link provided by the server"
        println "\n--- ETAP 2: Uruchamianie misji za pomocą odkrytego linku ---"
        def startLink = initialState._links.start.href
        println "Klient 'klika' link: ${startLink}"
        def startResponse = client.sendGenericPost(startLink, [:])

        then: "the start operation is successful"
        startResponse.statusCode == 200

        when: "client checks the state again"
        println "\n--- ETAP 3: Sprawdzanie stanu po uruchomieniu ---"
        def inProgressStateResponse = client.getData("/mission")
        def inProgressState = inProgressStateResponse.json

        then: "mission is IN_PROGRESS and server now provides a 'complete' link"
        inProgressState.status == "IN_PROGRESS"
        inProgressState._links.start == null // Link do startu zniknął
        inProgressState._links.complete != null

        when: "client follows the 'complete' link"
        println "\n--- ETAP 4: Kończenie misji za pomocą odkrytego linku ---"
        def completeLink = inProgressState._links.complete.href
        println "Klient 'klika' link: ${completeLink}"
        def completeResponse = client.sendGenericPost(completeLink, [:])

        then: "the complete operation is successful"
        completeResponse.statusCode == 200

        when: "client checks the final state"
        println "\n--- ETAP 5: Sprawdzanie stanu końcowego ---"
        def finalStateResponse = client.getData("/mission")
        def finalState = finalStateResponse.json

        then: "mission is COMPLETED and there are no more action links"
        finalState.status == "COMPLETED"
        finalState._links.start == null
        finalState._links.complete == null
    }
}
Jak uruchomić i zrozumieć ten przykład?
Wklej ten kod do pliku HateoasMissionControlSpec.groovy.
Uruchom test. Powinien przejść i zaświecić się na zielono.
Spójrz na wydruki w konsoli. Zobaczysz tam, jak klient krok po kroku odkrywa, co ma dalej zrobić:
Najpierw pyta o stan i dostaje link do akcji "start".
Używa tego linku, żeby zmienić stan.
Pyta ponownie i dostaje link do akcji "complete".
Używa tego linku, żeby znowu zmienić stan.
Na końcu pyta i widzi, że nie ma już żadnych akcji do wykonania.
Jak to "przytulić" do Twojego projektu?
Ten przykład jest w pełni samowystarczalny. Możesz go analizować, modyfikować, "psuć", żeby zobaczyć, jak reaguje.
Kiedy już poczujesz się z tym komfortowo, możesz zacząć myśleć, jak zaimplementować podobną logikę w GroundControlServerApp. Zamiast prostego missionState, będziesz operować na prawdziwych manifestach, ale idea pozostanie ta sama: odpowiedź z API powinna zawierać nie tylko dane, ale także menu dostępnych akcji w formie linków.
