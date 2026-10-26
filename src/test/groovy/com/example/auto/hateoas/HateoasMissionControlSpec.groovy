package com.example.auto.hateoas

import com.example.auto.http.SimpleHttpClient
import groovy.json.JsonOutput
import spark.Service
import spock.lang.Shared
import spock.lang.Specification

class HateoasActionControlSpec extends Specification {

    // Nasz mini-serwer do demonstracji HATEOAS
    @Shared Service actionServer  // inteligentny robot, servuje linki na żądanie klienta
    // Nasz klient do komunikacji
    @Shared SimpleHttpClient client
    // Adres URL serwera
    @Shared String serverUrl

    // === Stan naszej prostej misji na serwerze ===
    // W prawdziwej aplikacji byłoby to w bazie danych.
    static String actionState = "PLANNED"   //  stan misji

    // Uruchamiamy serwer przed wszystkimi testami w tej klasie
    def setupSpec() {
        actionServer = Service.ignite()
        actionServer.port(0)  // uruchom na losowym porcie

        // --- Definicja API naszego serwera HATEOAS ---

        // 1. Główny endpoint, który zwraca stan misji i dostępne akcje
        actionServer.get("/action") { req, res ->
            res.type("application/json")
            println "Główny Endpoint, który zwraca stan misji i dostępne akcje"

            def baseUrl = req.scheme() + "://" + req.host()
            def response = [
                    actionId: "DEMO-ACTION-001",
                    status: ActionState,
                    _links: [
                            // Zawsze podajemy link do samego siebie
                            self: [ href: baseUrl + "/action" ]
                    ]
            ]

            // === Logika HATEOAS: Serwer decyduje, jakie akcje są dostępne ===
            if (actionState == "PLANNED") {
                response._links.start = [ href: baseUrl + "/action/start", method: "POST" ]
            } else if (actionState == "IN_PROGRESS") {
                response._links.complete = [ href: baseUrl + "/action/complete", method: "POST" ]
            }

            return JsonOutput.toJson(response)
        }

        // 2. Endpoint do uruchamiania misji (istnieje tylko, gdy misja jest PLANNED)
        actionServer.post("/action/start") { req, res ->
            println "Endpoint do uruchamiania misji (istnieje tylko, gdy misja jest PLANNED)"
            if (actionState == "PLANNED") {
                actionState = "IN_PROGRESS"
                res.status(200)
                return JsonOutput.toJson([message: "Action started!"])
            } else {
                res.status(409) // 409 Conflict - tej akcji nie można teraz wykonać
                return JsonOutput.toJson([error: "Action is not in PLANNED state."])
            }
        }

        // 3. Endpoint do kończenia misji (istnieje tylko, gdy misja jest IN_PROGRESS)
        actionServer.post("/action/complete") { req, res ->
            println "Endpoint do kończenia misji (istnieje tylko, gdy misja jest IN_PROGRESS)"
            if (actionState == "IN_PROGRESS") {
                actionState = "COMPLETED"
                res.status(200)
                return JsonOutput.toJson([message: "Action completed!"])
            } else {
                res.status(409) // 409 Conflict
                return JsonOutput.toJson([error: "Action is not IN_PROGRESS."])
            }
        }

        actionServer.awaitInitialization()
        serverUrl = "http://localhost:${actionServer.port()}"
        client = new SimpleHttpClient(serverUrl)
    }

    // Zamykamy serwer po wszystkich testach
    def cleanupSpec() {
        actionServer?.stop()
    }

    // Resetujemy stan misji przed każdym testem
    def cleanup() {
        actionState = "PLANNED"
    }


    // === TEST, KTÓRY SYMULUJE "INTELIGENTNEGO" KLIENTA ===
    def "client should navigate the action lifecycle by following links"() {
        when: "client checks the initial state of the action"
        println "--- ETAP 1: Sprawdzanie stanu początkowego ---"
        def initialStateResponse = client.getData("/action")
        def initialState = initialStateResponse.json

        then: "action is PLANNED and server provides a 'start' link"
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
        def inProgressStateResponse = client.getData("/action")
        def inProgressState = inProgressStateResponse.json

        then: "action is IN_PROGRESS and server now provides a 'complete' link"
        inProgressState.status == "IN_PROGRESS"
        inProgressState._links.start == null // Link do startu zniknął
        inProgressState._links.complete != null

        when: "client follows the 'complete' link"
        println "\n--- ETAP 4: Kończenie akcji za pomocą odkrytego linku ---"
        def completeLink = inProgressState._links.complete.href
        println "Klient 'klika' link: ${completeLink}"
        def completeResponse = client.sendGenericPost(completeLink, [:])

        then: "the complete operation is successful"
        completeResponse.statusCode == 200

        when: "client checks the final state"
        println "\n--- ETAP 5: Sprawdzanie stanu końcowego ---"
        def finalStateResponse = client.getData("/action")
        def finalState = finalStateResponse.json

        then: "action is COMPLETED and there are no more action links"
        finalState.status == "COMPLETED"
        finalState._links.start == null
        finalState._links.complete == null
    }
}