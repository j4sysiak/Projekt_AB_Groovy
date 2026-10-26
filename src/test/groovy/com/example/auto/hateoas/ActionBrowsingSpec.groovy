package com.example.auto.hateoas

import com.example.auto.dto.PhotoCollectionAction
import com.example.auto.dto.PhotoTask
import com.example.auto.fakes.FakeRelationalDB
import com.example.auto.fakes.HttpServerFactory
import com.example.auto.http.SimpleHttpClient
import com.example.auto.server.AutoControlServerApp
import spock.lang.Shared
import spock.lang.Specification

class ActionBrowsingSpec extends Specification {

    @Shared AutoControlServerApp platzServer
    @Shared SimpleHttpClient platzClient

    def setupSpec() {
        // === ZMIANA: Pełne wstrzykiwanie zależności ===

        // 1. Stwórz testowe implementacje zależności
        def fakeDb = new FakeRelationalDB()
        def fakeHttpServer = HttpServerFactory.create()
        def actionManifests = [:] // Tworzymy mapę, którą wstrzykniemy

        // 2. Stwórz i uruchom AutoControlServerApp, używając konstruktora testowego
        platzServer = new AutoControlServerApp(fakeDb, fakeHttpServer, actionManifests)

        // 3. Przygotuj stan początkowy, wstawiając misję do tej samej mapy
        def tasks = (1..25).collect { new PhotoTask(taskId: "T${it}") }
        def manifest = new PhotoCollectionAction(actionId: "M-PAGINATION", tasks: tasks)
        actionManifests['M-PAGINATION'] = manifest

        // 4. Uruchom serwer i stwórz klienta
        platzServer.start(0, "")
        platzClient = new SimpleHttpClient("http://localhost:${platzServer.getPort()}")
    }

    def cleanupSpec() {
        platzServer?.stop()
    }

    // Sama metoda testowa pozostaje BEZ ZMIAN!

    def "should allow client to browse action tasks using HATEOAS pagination links"() {
        when: "client requests the main action resource"
        def actionResponse = platzClient.getData("/action/M-PAGINATION")

        then: "response does not contain tasks, but a link to them"
        actionResponse.statusCode == 200
        actionResponse.json.tasks == null
        actionResponse.json._links.tasks.href != null

        when: "client follows the 'tasks' link to get the first page"
        def tasksUrl = actionResponse.json._links.tasks.href
        def page1Response = platzClient.getData(tasksUrl)

        then: "the first page of tasks is returned correctly"
        page1Response.statusCode == 200
        def page1 = page1Response.json
        page1.page.number == 1
        page1.page.totalPages == 3
        page1.page.totalElements == 25
        page1._embedded.tasks.size() == 10
        page1._links.prev == null
        page1._links.next != null

        when: "client follows the 'next' link to get the second page"
        def nextPageUrl = page1._links.next.href
        def page2Response = platzClient.getData(nextPageUrl)

        then: "the second page is returned correctly"
        page2Response.statusCode == 200
        def page2 = page2Response.json
        page2.page.number == 2
        page2._embedded.tasks.size() == 10
        page2._links.prev != null
        page2._links.next != null

        when: "client follows the 'next' link again to get the final page"
        def lastPageUrl = page2._links.next.href
        def page3Response = platzClient.getData(lastPageUrl)

        then: "the final page contains the remaining tasks"
        page3Response.statusCode == 200
        def page3 = page3Response.json
        page3.page.number == 3
        page3._embedded.tasks.size() == 5
        page3._links.prev != null
        page3._links.next == null
    }
}