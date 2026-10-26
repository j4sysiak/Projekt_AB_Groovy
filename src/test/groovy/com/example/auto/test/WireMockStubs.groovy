package com.example.auto.test

import com.github.tomakehurst.wiremock.WireMockServer

import static com.github.tomakehurst.wiremock.client.WireMock.*

/**
 * Klasa pomocnicza zawierająca predefiniowane scenariusze (stuby) dla WireMocka.
 * Utrzymuje porządek i pozwala na ponowne użycie tych samych definicji w wielu testach.
 */
class WireMockStubs {

    private WireMockServer wireMockServer

    WireMockStubs(WireMockServer wireMockServer) {
        this.wireMockServer = wireMockServer
    }

    void setupSuccessfulHealthCheck() {
        wireMockServer.stubFor(get(urlEqualTo("/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('{"status": "UP"}')
                )
        )
    }

    void setupActionAcceptance(String actionId) {
        wireMockServer.stubFor(post(urlEqualTo("/action/${actionId}/accept"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody('{"message": "Action accepted."}')
                )
        )
    }

    void setupNotFoundFor(String path) {
        wireMockServer.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse().withStatus(404))
        )
    }

    /**
     * Programuje serwer, aby dla danej akcji zwrócił listę zadań.
     * @param actionId ID akcji
     * @param taskCount Ile zadań ma zostać zwróconych w odpowiedzi
     */
    void setupActionTasksResponse(String actionId, int taskCount) {
        // Tworzymy dynamicznie listę zadań w formacie JSON
        def tasks = (1..taskCount).collect {
            [taskId: "TASK-${it}", status: "PENDING"]
        }

        // Używamy JsonOutput, aby poprawnie zamienić listę Groovy na string JSON
        def responseBody = new groovy.json.JsonOutput().toJson(tasks)

        wireMockServer.stubFor(get(urlEqualTo("/action/${actionId}/tasks"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)
                )
        )
    }
}