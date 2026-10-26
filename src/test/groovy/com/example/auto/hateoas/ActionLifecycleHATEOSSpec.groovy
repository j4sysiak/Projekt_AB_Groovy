package com.example.auto.hateoas

import com.example.auto.http.SimpleHttpClient
import groovy.json.JsonOutput
import spark.Service
import spock.lang.Shared
import spock.lang.Specification

class ActionLifecycleHATEOSSpec extends Specification {

    @Shared Service actionLifecycleServer
    @Shared SimpleHttpClient client
    @Shared String serverUrl

    // === Stan naszej misji na serwerze ===
    // W prawdziwej aplikacji to byłaby złożona encja w bazie danych.
    @Shared Map actionResource

    def setupSpec() {
        actionLifecycleServer = Service.ignite()
        actionLifecycleServer.port(0)

        // --- Definicja API naszego serwera HATEOAS ---

        def actionId = "DEMO-ACTION-007"

        // 1. Główny endpoint, który zwraca stan misji i dostępne akcje
        actionLifecycleServer.get("/actions/${actionId}") { req, res ->
            res.type("application/json")
            String baseUrl = req.scheme() + "://" + req.host()

            // === Logika HATEOAS: Serwer dynamicznie decyduje, jakie akcje udostępnić ===
            actionResource._links = [
                    self: [ href: baseUrl + "/actions/${actionId}" ]
            ]

            switch (actionResource.status) {
                case "PLANNED":
                    actionResource._links."accept-action" = [ href: baseUrl + "/actions/${actionId}/accept", method: "POST" ]
                    break
                case "ACCEPTED":
                    actionResource._links."run-action" = [ href: baseUrl + "/actions/${actionId}/run", method: "POST" ]
                    break
                case "COMPLETED":
                    actionResource._links."view-results" = [ href: baseUrl + "/actions/${actionId}/results", method: "GET" ]
                    break
            }

            return JsonOutput.toJson(actionResource)
        }

        // 2. Endpointy akcji, które modyfikują stan misji
        actionLifecycleServer.post("/actions/${actionId}/accept") { req, res ->
            if (actionResource.status == "PLANNED") {
                actionResource.status = "ACCEPTED"
                return JsonOutput.toJson([message: "Action ${actionId} accepted."])
            }
            res.status(409); return JsonOutput.toJson([error: "Action not in PLANNED state."])
        }

        actionLifecycleServer.post("/actions/${actionId}/run") { req, res ->
            if (actionResource.status == "ACCEPTED") {
                actionResource.status = "COMPLETED"
                actionResource.results = [photos: 120, dataSize: "2.4GB"]
                return JsonOutput.toJson([message: "Simulation for ${actionId} completed."])
            }
            res.status(409); return JsonOutput.toJson([error: "Action not in ACCEPTED state."])
        }

        actionLifecycleServer.get("/actions/${actionId}/results") { req, res ->
            if (actionResource.status == "COMPLETED") {
                return JsonOutput.toJson(actionResource.results)
            }
            res.status(404); return JsonOutput.toJson([error: "Results not available yet."])
        }

        actionLifecycleServer.awaitInitialization()
        serverUrl = "http://localhost:${actionLifecycleServer.port()}"
        client = new SimpleHttpClient(serverUrl)
    }

    def cleanupSpec() {
        actionLifecycleServer?.stop()
    }

    def setup() {
        actionResource = [
                actionId: "DEMO-ACTION-007",
                status: "PLANNED",
                target: "Mars"
        ]
    }

    // === TEST, KTÓRY SYMULUJE "INTELIGENTNEGO" KLIENTA ===
    def "should guide the client through the entire action lifecycle via HATEOAS links"() {
        given: "an action ID"
        def actionUrl = "/actions/DEMO-ACTION-007"

        when: "client checks the initial state"
        println "--- ETAP 1: Akcja w stanie 'PLANNED' ---"
        def response1 = client.getData(actionUrl)
        def state1 = response1.json

        then: "server offers to accept the action"
        state1.status == "PLANNED"
        state1._links.'accept-action' != null
        state1._links.'run-action' == null // Akcja "uruchom" jest jeszcze niedostępna

        when: "client follows the 'accept-action' link"
        def acceptUrl = state1._links.'accept-action'.href
        println "Klient 'klika' link akcji: ${acceptUrl}"
        client.sendGenericPost(acceptUrl, [:])

        and: "client checks the state again"
        println "\n--- ETAP 2: Akcja w stanie 'ACCEPTED' ---"
        def response2 = client.getData(actionUrl)
        def state2 = response2.json

        then: "server now offers to run the simulation"
        state2.status == "ACCEPTED"
        state2._links.'accept-action' == null // Link "akceptuj" zniknął
        state2._links.'run-action' != null

        when: "client follows the 'run-action' link"
        def runUrl = state2._links.'run-action'.href
        println "Klient 'klika' link akcji: ${runUrl}"
        client.sendGenericPost(runUrl, [:])

        and: "client checks the state for the last time"
        println "\n--- ETAP 3: Misja w stanie 'COMPLETED' ---"
        def response3 = client.getData(actionUrl)
        def state3 = response3.json

        then: "server now offers to view the results"
        state3.status == "COMPLETED"
        state3._links.'run-action' == null // Link "uruchom" zniknął
        state3._links.'view-results' != null

        and: "client can retrieve the results by following the final link"
        def resultsUrl = state3._links.'view-results'.href
        println "Klient 'klika' link do wyników: ${resultsUrl}"
        def resultsResponse = client.getData(resultsUrl)
        resultsResponse.statusCode == 200
        resultsResponse.json.photos == 120
    }
}