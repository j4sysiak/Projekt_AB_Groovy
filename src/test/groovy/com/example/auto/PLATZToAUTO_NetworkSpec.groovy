package com.example.auto

import com.example.auto.fakes.FakeRelationalDB
import com.example.auto.fakes.HttpServerFactory
import com.example.auto.fakes.AutoCheck
import com.example.auto.http.SimpleHttpClient
import com.example.auto.server.AutoControlServerApp
import com.example.auto.server.AutoCheckServer
import spock.lang.Specification

class PLATZToAUTO_NetworkSpec extends Specification {
    AutoControlServerApp platzServer
    AutoCheckServer autoServer
    AutoCheck checker
    SimpleHttpClient platzClient

    def setup() {
// 1. Stwórz testowe implementacje zależności
        checker = new AutoCheck() // Nasz "fejk"
        def autoHttpServer = HttpServerFactory.create()
        def platzDb = new FakeRelationalDB()
        def platzHttpServer = HttpServerFactory.create()

        // 2. Stwórz i uruchom AutoCheckServer, wstrzykując jego zależności
        autoServer = new AutoCheckServer(checker, autoHttpServer)
        autoServer.start(0)
        def autoServerUrl = "http://localhost:${autoServer.getPort()}"

        // 3. Stwórz i uruchom AutoControlServerApp, wstrzykując jego zależności
        platzServer = new AutoControlServerApp(platzDb, platzHttpServer)
        // Przekazujemy mu URL do serwera, aby wiedział, z kim ma rozmawiać
        platzServer.start(0, autoServerUrl)
        def platzServerUrl = "http://localhost:${platzServer.getPort()}"

        // 4. Stwórz klienta do sterowania serwerem PLATZ
        platzClient = new SimpleHttpClient(platzServerUrl)
    }

    def cleanup() {
        platzServer?.stop()
        autoServer?.stop()
    }

    // ZMIANA: Nazwa i logika testu zostały w pełni przepisane
    def "ground server should allow a client to take a action through its full lifecycle"() {
        given: "both servers are running"
        checker.batteryLevel = 100.0

        when: "a user sends a action request to the ground control server"
        def actionRequest = [photoCount: 5]
        def createResponse = platzClient.sendGenericPost("/action/photo-collection", actionRequest)

        then: "the action is created in PLANNED state"
        createResponse.statusCode == 202
        def action = createResponse.json
        def actionId = action.actionId
        action.status == "PLANNED"
        action.tasks.size() == 5
        actionId != null

        when: "the client accepts the action"
        // Nie musimy używać HATEOAS, bo ten test sprawdza tylko poprawność kroków
        def acceptResponse = platzClient.sendGenericPost("/action/${actionId}/accept", [:])

        then: "the action status is updated to ACCEPTED"
        acceptResponse.statusCode == 200

        when: "the client requests to upload the action"
        def uploadResponse = platzClient.sendGenericPost("/action/${actionId}/upload", [:])

        then: "the upload is successful and the auto receives the manifest"
        uploadResponse.statusCode == 200
        uploadResponse.json.status == "OK"

        // Czekamy chwilę, bo komunikacja jest asynchroniczna
        Thread.sleep(500)
        checker.activeManifest.actionId == actionId
        checker.activeManifest.status == "UPLOADED"
    }
}