package com.example.sat.http

import com.example.sat.test.WireMockStubs // <-- Importujemy naszą fabrykę
import com.github.tomakehurst.wiremock.WireMockServer
import spock.lang.Shared
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.client.WireMock.*

class SimpleHttpClientWithStubsSpec extends Specification {

    @Shared WireMockServer wireMockServer
    @Shared SimpleHttpClient client
    @Shared WireMockStubs stubs // Nasza fabryka stubów

    def setupSpec() {
        // Uruchamiamy serwer i klienta - standardowa procedura
        wireMockServer = new WireMockServer()
        wireMockServer.start()
        client = new SimpleHttpClient(wireMockServer.baseUrl())

        // Inicjalizujemy naszą fabrykę, przekazując jej serwer, którym ma zarządzać
        stubs = new WireMockStubs(wireMockServer)
    }

    def cleanupSpec() {
        wireMockServer?.stop()
    }

    // Przed każdym testem resetujemy wszystkie reguły na serwerze
    def setup() {
        wireMockServer.resetAll()
    }

    // ===========================================
    // ===          TEST DLA METODY GET        ===
    // ===========================================
    def "should correctly handle a GET request configured by a stub factory"() {
        given: "the server is prepared for a successful health check"
        // JEDNA linia, która ukrywa całą złożoność konfiguracji WireMocka!
        stubs.setupSuccessfulHealthCheck()

        when: "the client requests the health status"
        def response = client.getData("/health")

        then: "the client correctly parses the successful response"
        response.statusCode == 200
        response.json.status == "UP"

        and: "we can still verify the interaction on the server"
        // Możemy sprawdzić, czy żądanie faktycznie dotarło
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/health")))
    }


    // ===========================================
    // ===         TEST DLA METODY POST          ===
    // ===========================================
    def "should correctly handle a POST request configured by a stub factory"() {
        given: "the server is prepared to accept a mission"
        def missionId = "M-123"
        // Ponownie, jedna linia do przygotowania serwera na przyjęcie POST-a
        stubs.setupMissionAcceptance(missionId)

        when: "the client sends a request to accept the mission"
        // Wykonujemy akcję POST na naszym kliencie
        def response = client.sendGenericPost("/missions/${missionId}/accept", [:])

        then: "the client correctly parses the response from the POST request"
        response.statusCode == 200
        response.json.message == "Mission accepted."

        and: "we can verify that the POST request was received"
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/missions/${missionId}/accept")))
    }

    // =======================================================
    // === TEST ZLICZAJĄCY ELEMENTY W ODPOWIEDZI Z SERWERA ===
    // =======================================================
    def "should correctly parse a response containing a list of items and allow counting them"() {
        given: "a server that is programmed to return a list of 5 tasks for a mission"
        def missionId = "M-456"
        def expectedTaskCount = 5

        // Używamy naszej nowej, sparametryzowanej metody ze stuba
        stubs.setupMissionTasksResponse(missionId, expectedTaskCount)

        when: "the client requests the list of tasks for that mission"
        def response = client.getData("/missions/${missionId}/tasks")

        then: "the client receives a successful response"
        response.statusCode == 200

        and: "the response body can be correctly parsed as a list"
        // Odpowiedź JSON jest automatycznie parsowana do listy map przez JsonSlurper w kliencie
        def tasks = response.json
        tasks instanceof List // Upewniamy się, że to faktycznie lista

        and: "we can correctly count the number of tasks in the response"
        // Używamy metody .size() do zliczenia elementów w liście
        tasks.size() == expectedTaskCount

        and: "we can even inspect individual elements"
        tasks[0].taskId == "TASK-1" // Sprawdźmy pierwszy element dla pewności
        tasks[4].status == "PENDING" // I ostatni
    }
}