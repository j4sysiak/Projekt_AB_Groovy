package com.example.auto.hateoas

import com.example.auto.fakes.FakeRelationalDB
import com.example.auto.fakes.HttpServerFactory
import com.example.auto.fakes.AutoCheck
import com.example.auto.http.SimpleHttpClient
import com.example.auto.server.AutoControlServerApp
import com.example.auto.server.AutoCheckServer
import spock.lang.Shared
import spock.lang.Specification

class PlatzActionLifecycleSpec extends Specification {

    @Shared AutoControlServerApp platzServer
    @Shared AutoCheckServer autoServer
    @Shared SimpleHttpClient platzClient
    @Shared AutoCheck checker

    def setupSpec() {
        // === ZMIANA: Pełne wstrzykiwanie zależności ===

        // 1. Stwórz testowe implementacje zależności
        checker = new AutoCheck() // Używamy pola @Shared
        def autoHttp = HttpServerFactory.create()
        def platzDb = new FakeRelationalDB()
        def platzHttp = HttpServerFactory.create()

        // 2. Stwórz i uruchom AutoCheckServer
        autoServer = new AutoCheckServer(checker, autoHttp)
        autoServer.start(0)
        def autoServerUrl = "http://localhost:${autoServer.getPort()}"

        // 3. Stwórz i uruchom AutoControlServerApp
        platzServer = new AutoControlServerApp(platzDb, platzHttp)
        platzServer.start(0, autoServerUrl)
        def platzServerUrl = "http://localhost:${platzServer.getPort()}"

        // 4. Stwórz klienta do komunikacji
        platzClient = new SimpleHttpClient(platzServerUrl)
    }

    def cleanupSpec() {
        platzServer?.stop()
        autoServer?.stop()
    }

    // Dodajemy cleanup(), żeby resetować stan przed każdym testem
    def cleanup() {
        platzServer.resetState()
        checker.resetState()
    }

    def "should guide client through action lifecycle from creation to upload"() {
        when: "client creates a new photo collection action"
        def actionRequest = [photoCount: 5]
        def createResponse = platzClient.sendGenericPost("/action/photo-collection", actionRequest)

        then: "action is created successfully in PLANNED state"
        createResponse.statusCode == 202
        def actionId = createResponse.json.actionId
        actionId != null

        when: "client checks the state of the newly created action"
        println "\n--- ETAP 1: Akcja w stanie 'PLANNED' ---"
        def response1 = platzClient.getData("/action/${actionId}")
        def state1 = response1.json

        then: "server offers to accept the action"
        state1.status == "PLANNED"
        state1._links.'accept-action' != null
        state1._links.'upload-to-auto' == null

        when: "client follows the 'accept-action' link"
        def acceptUrl = state1._links.'accept-action'.href
        println "Klient 'klika' link akcji: ${acceptUrl}"
        platzClient.sendGenericPost(acceptUrl, [:])

        and: "client checks the state again"
        println "\n--- ETAP 2: Akcja w stanie 'ACCEPTED' ---"
        def response2 = platzClient.getData("/action/${actionId}")
        def state2 = response2.json

        then: "server now offers to upload the action"
        state2.status == "ACCEPTED"
        state2._links.'accept-action' == null
        state2._links.'upload-to-auto' != null

        when: "client follows the 'upload-to-auto' link"
        def uploadUrl = state2._links.'upload-to-auto'.href
        println "Klient 'klika' link akcji: ${uploadUrl}"
        def uploadResponse = platzClient.sendGenericPost(uploadUrl, [:])

        then: "upload is successful"
        uploadResponse.statusCode == 200

        and: "Action state is finally UPLOADED"
        println "\n--- ETAP 3: Akcja w stanie 'UPLOADED' ---"
        def response3 = platzClient.getData("/action/${actionId}")
        def state3 = response3.json
        state3.status == "UPLOADED"
        state3._links.'upload-to-auto' == null
    }
}