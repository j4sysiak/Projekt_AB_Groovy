// src/test/groovy/com/example/sat/hateoas/MissionLifecycleHATEOSSpec.groovy
package com.example.sat.hateoas

import com.example.sat.http.SimpleHttpClient
import groovy.json.JsonOutput
import spark.Service
import spock.lang.Shared
import spock.lang.Specification

class MissionLifecycleHATEOSSpec extends Specification {

    @Shared Service missionLifecycleServer
    @Shared SimpleHttpClient client
    @Shared String serverUrl

    // === Stan naszej misji na serwerze ===
    // W prawdziwej aplikacji to byłaby złożona encja w bazie danych.
    @Shared Map missionResource

    def setupSpec() {
        missionLifecycleServer = Service.ignite()
        missionLifecycleServer.port(0)

        // --- Definicja API naszego serwera HATEOAS ---

        def missionId = "DEMO-MISSION-007"

        // 1. Główny endpoint, który zwraca stan misji i dostępne akcje
        missionLifecycleServer.get("/missions/${missionId}") { req, res ->
            res.type("application/json")
            String baseUrl = req.scheme() + "://" + req.host()

            // === Logika HATEOAS: Serwer dynamicznie decyduje, jakie akcje udostępnić ===
            missionResource._links = [
                    self: [ href: baseUrl + "/missions/${missionId}" ]
            ]

            switch (missionResource.status) {
                case "PLANNED":
                    missionResource._links."accept-mission" = [ href: baseUrl + "/missions/${missionId}/accept", method: "POST" ]
                    break
                case "ACCEPTED":
                    missionResource._links."run-simulation" = [ href: baseUrl + "/missions/${missionId}/run", method: "POST" ]
                    break
                case "COMPLETED":
                    missionResource._links."view-results" = [ href: baseUrl + "/missions/${missionId}/results", method: "GET" ]
                    break
            }

            return JsonOutput.toJson(missionResource)
        }

        // 2. Endpointy akcji, które modyfikują stan misji
        missionLifecycleServer.post("/missions/${missionId}/accept") { req, res ->
            if (missionResource.status == "PLANNED") {
                missionResource.status = "ACCEPTED"
                return JsonOutput.toJson([message: "Mission ${missionId} accepted."])
            }
            res.status(409); return JsonOutput.toJson([error: "Mission not in PLANNED state."])
        }

        missionLifecycleServer.post("/missions/${missionId}/run") { req, res ->
            if (missionResource.status == "ACCEPTED") {
                missionResource.status = "COMPLETED"
                // Symulujemy, że po symulacji pojawiają się wyniki
                missionResource.results = [photos: 120, dataSize: "2.4GB"]
                return JsonOutput.toJson([message: "Simulation for ${missionId} completed."])
            }
            res.status(409); return JsonOutput.toJson([error: "Mission not in ACCEPTED state."])
        }

        missionLifecycleServer.get("/missions/${missionId}/results") { req, res ->
            if (missionResource.status == "COMPLETED") {
                return JsonOutput.toJson(missionResource.results)
            }
            res.status(404); return JsonOutput.toJson([error: "Results not available yet."])
        }

        missionLifecycleServer.awaitInitialization()
        serverUrl = "http://localhost:${missionLifecycleServer.port()}"
        client = new SimpleHttpClient(serverUrl)
    }

    def cleanupSpec() {
        missionLifecycleServer?.stop()
    }

    // Metoda setup() jest uruchamiana PRZED każdym testem,
    // co gwarantuje, że `missionResource` będzie miało świeży,
    // poprawny stan na początku każdego testu.
    // Resetujemy misję do stanu początkowego przed każdym testem
    def setup() {
        missionResource = [
                missionId: "DEMO-MISSION-007",
                status: "PLANNED",
                target: "Mars"
        ]
    }

    // === TEST, KTÓRY SYMULUJE "INTELIGENTNEGO" KLIENTA ===
    def "should guide the client through the entire mission lifecycle via HATEOAS links"() {
        given: "a mission ID"
        def missionUrl = "/missions/DEMO-MISSION-007"

        when: "client checks the initial state"
        println "--- ETAP 1: Misja w stanie 'PLANNED' ---"
        def response1 = client.getData(missionUrl)
        def state1 = response1.json

        then: "server offers to accept the mission"
        state1.status == "PLANNED"
        state1._links.'accept-mission' != null
        state1._links.'run-simulation' == null // Akcja "uruchom" jest jeszcze niedostępna

        when: "client follows the 'accept-mission' link"
        def acceptUrl = state1._links.'accept-mission'.href
        println "Klient 'klika' link akcji: ${acceptUrl}"
        client.sendGenericPost(acceptUrl, [:])

        and: "client checks the state again"
        println "\n--- ETAP 2: Misja w stanie 'ACCEPTED' ---"
        def response2 = client.getData(missionUrl)
        def state2 = response2.json

        then: "server now offers to run the simulation"
        state2.status == "ACCEPTED"
        state2._links.'accept-mission' == null // Link "akceptuj" zniknął
        state2._links.'run-simulation' != null

        when: "client follows the 'run-simulation' link"
        def runUrl = state2._links.'run-simulation'.href
        println "Klient 'klika' link akcji: ${runUrl}"
        client.sendGenericPost(runUrl, [:])

        and: "client checks the state for the last time"
        println "\n--- ETAP 3: Misja w stanie 'COMPLETED' ---"
        def response3 = client.getData(missionUrl)
        def state3 = response3.json

        then: "server now offers to view the results"
        state3.status == "COMPLETED"
        state3._links.'run-simulation' == null // Link "uruchom" zniknął
        state3._links.'view-results' != null

        and: "client can retrieve the results by following the final link"
        def resultsUrl = state3._links.'view-results'.href
        println "Klient 'klika' link do wyników: ${resultsUrl}"
        def resultsResponse = client.getData(resultsUrl)
        resultsResponse.statusCode == 200
        resultsResponse.json.photos == 120
    }
}