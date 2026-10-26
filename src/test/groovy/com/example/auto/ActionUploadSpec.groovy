package com.example.auto

import com.example.auto.fakes.FakeRelationalDB
import com.example.auto.fakes.HttpServerFactory
import com.example.auto.fakes.AutoCheck
import com.example.auto.http.SimpleHttpClient
import com.example.auto.server.AutoControlServerApp
import com.example.auto.server.AutoCheckServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ActionUploadSpec extends Specification {

    AutoControlServerApp platzServer
    AutoCheckServer autoServer
    AutoCheck checker
    SimpleHttpClient platzClient

    def setup() {
        // === ZMIANA: Pełne wstrzykiwanie zależności ===

        // 1. Stwórz testowe implementacje zależności
        checker = new AutoCheck()
        def platzDb = new FakeRelationalDB()
        def platzHttp = HttpServerFactory.create()
        def autoHttp = HttpServerFactory.create()

        // 2. Stwórz i uruchom AutoCheckServer
        autoServer = new AutoCheckServer(checker, autoHttp)
        autoServer.start(0)
        def autoServerUrl = "http://localhost:${autoServer.getPort()}"

        // 3. Stwórz i uruchom AutoControlServerApp
        platzServer = new AutoControlServerApp(platzDb, platzHttp)
        platzServer.start(0, autoServerUrl) // Przekazujemy URL do serwera
        def platzServerUrl = "http://localhost:${platzServer.getPort()}"

        // 4. Stwórz klienta do sterowania serwerem PLATZ
        platzClient = new SimpleHttpClient(platzServerUrl)
    }

    def cleanup() {
        platzServer?.stop()
        autoServer?.stop()
    }

    def "should allow client to create, accept, and upload a action"() {
        given: "a request to collect 5 photos"
        def actionRequest = [photoCount: 5]
        def conditions = new PollingConditions(timeout: 5)

        // --- ETAP 1: Tworzenie Akcji ---
        when: "the action request is sent to the ground control server"
        def createResponse = platzClient.sendGenericPost("/action/photo-collection", actionRequest)

        then: "the action is created successfully in PLANNED state"
        createResponse.statusCode == 202
        def action = createResponse.json
        def actionId = action.actionId
        action.status == "PLANNED"
        actionId != null

        // --- ETAP 2: Akceptacja Akcji ---
        when: "the client accepts the action"
        def acceptResponse = platzClient.sendGenericPost("/action/${actionId}/accept", [:])

        then: "the accept operation is successful"
        acceptResponse.statusCode == 200

        // --- ETAP 3: Wysyłka Akcji ---
        when: "the client requests to upload the action"
        def uploadResponse = platzClient.sendGenericPost("/action/${actionId}/upload", [:])

        then: "the upload is successful"
        uploadResponse.statusCode == 200
        uploadResponse.json.status == "OK"

        and: "the auto checker has stored the correct manifest"
        // Używamy "cierpliwej" asercji zamiast Thread.sleep
        conditions.eventually {
            assert checker.activeManifest != null
            assert checker.activeManifest.actionId == actionId
            assert checker.activeManifest.status == "UPLOADED"
            assert checker.activeManifest.tasks.size() == 5
        }
    }
}