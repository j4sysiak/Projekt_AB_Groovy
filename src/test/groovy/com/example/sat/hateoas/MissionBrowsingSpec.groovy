package com.example.sat.hateoas

import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoTask
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.GroundControlServerApp
import spock.lang.Shared
import spock.lang.Specification

class MissionBrowsingSpec extends Specification {

    @Shared GroundControlServerApp grundServer
    @Shared SimpleHttpClient grundClient

    def setupSpec() {
        grundServer = new GroundControlServerApp()
        // Tworzymy misję z 25 zadaniami, aby mieć co paginować
        def tasks = (1..25).collect { new PhotoTask(taskId: "T${it}") }
        def manifest = new PhotoCollectionManifest(missionId: "M-PAGINATION", tasks: tasks)
        grundServer.missionManifests['M-PAGINATION'] = manifest

        grundServer.start(0, "")
        grundClient = new SimpleHttpClient("http://localhost:${grundServer.getPort()}")
    }

    def cleanupSpec() {
        grundServer?.stop()
    }

    def "should allow client to browse mission tasks using HATEOAS pagination links"() {
        when: "client requests the main mission resource"
        def missionResponse = grundClient.getData("/missions/M-PAGINATION")

        then: "response does not contain tasks, but a link to them"
        missionResponse.statusCode == 200
        missionResponse.json.tasks == null // Lista zadań jest pusta!
        missionResponse.json._links.tasks.href != null

        when: "client follows the 'tasks' link to get the first page"
        def tasksUrl = missionResponse.json._links.tasks.href
        println "Odkryty URL do zadań: ${tasksUrl}"
        def page1Response = grundClient.getData(tasksUrl)

        then: "the first page of tasks is returned correctly"
        page1Response.statusCode == 200
        def page1 = page1Response.json
        page1.page.number == 1
        page1.page.totalPages == 3 // 25 zadań / 10 na stronę = 3 strony
        page1.page.totalElements == 25
        page1._embedded.tasks.size() == 10 // Domyślny rozmiar strony to 10
        page1._links.prev == null // Nie ma poprzedniej strony
        page1._links.next != null // Jest następna strona

        when: "client follows the 'next' link to get the second page"
        def nextPageUrl = page1._links.next.href
        println "Odkryty URL do następnej strony: ${nextPageUrl}"
        def page2Response = grundClient.getData(nextPageUrl)

        then: "the second page is returned correctly"
        page2Response.statusCode == 200
        def page2 = page2Response.json
        page2.page.number == 2
        page2._embedded.tasks.size() == 10
        page2._links.prev != null
        page2._links.next != null

        when: "client follows the 'next' link again to get the final page"
        def lastPageUrl = page2._links.next.href
        println "Odkryty URL do ostatniej strony: ${lastPageUrl}"
        def page3Response = grundClient.getData(lastPageUrl)

        then: "the final page contains the remaining tasks"
        page3Response.statusCode == 200
        def page3 = page3Response.json
        page3.page.number == 3
        page3._embedded.tasks.size() == 5 // Ostatnie 5 z 25 zadań
        page3._links.prev != null
        page3._links.next == null // Nie ma już następnej strony
    }
}