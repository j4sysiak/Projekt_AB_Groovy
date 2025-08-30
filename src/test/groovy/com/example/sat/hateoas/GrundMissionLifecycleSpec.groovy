// src/test/groovy/com/example/sat/hateoas/GrundMissionLifecycleSpec.groovy
package com.example.sat.hateoas

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.SatelliteSimulatorServer
import spock.lang.Shared
import spock.lang.Specification

class GrundMissionLifecycleSpec extends Specification {

    @Shared GroundControlServerApp grundServer
    @Shared SatelliteSimulatorServer satServer
    @Shared SimpleHttpClient grundClient

    def setupSpec() {
        // Uruchamiamy oba serwery, ponieważ GRUND będzie się komunikował z SAT
        def simulator = new SatelliteSimulator()
        satServer = new SatelliteSimulatorServer(simulator, "")
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        grundServer = new GroundControlServerApp()
        grundServer.start(0, satServerUrl)
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        grundClient = new SimpleHttpClient(grundServerUrl)
    }

    def cleanupSpec() {
        grundServer?.stop()
        satServer?.stop()
    }

    def "should guide client through mission lifecycle from creation to upload"() {
        when: "client creates a new photo collection mission"
        def missionRequest = [photoCount: 5]
        def createResponse = grundClient.sendGenericPost("/missions/photo-collection", missionRequest)

        then: "mission is created successfully in PLANNED state"
        createResponse.statusCode == 202
        def missionId = createResponse.json.missionId
        missionId != null

        when: "client checks the state of the newly created mission"
        println "\n--- ETAP 1: Misja w stanie 'PLANNED' ---"
        def response1 = grundClient.getData("/missions/${missionId}")
        def state1 = response1.json

        then: "server offers to accept the mission"
        state1.status == "PLANNED"
        state1._links.'accept-mission' != null
        state1._links.'upload-to-satellite' == null

        when: "client follows the 'accept-mission' link"
        def acceptUrl = state1._links.'accept-mission'.href
        println "Klient 'klika' link akcji: ${acceptUrl}"
        grundClient.sendGenericPost(acceptUrl, [:])

        and: "client checks the state again"
        println "\n--- ETAP 2: Misja w stanie 'ACCEPTED' ---"
        def response2 = grundClient.getData("/missions/${missionId}")
        def state2 = response2.json

        then: "server now offers to upload the mission"
        state2.status == "ACCEPTED"
        state2._links.'accept-mission' == null
        state2._links.'upload-to-satellite' != null

        when: "client follows the 'upload-to-satellite' link"
        def uploadUrl = state2._links.'upload-to-satellite'.href
        println "Klient 'klika' link akcji: ${uploadUrl}"
        def uploadResponse = grundClient.sendGenericPost(uploadUrl, [:])

        then: "upload is successful"
        uploadResponse.statusCode == 200

        and: "mission state is finally UPLOADED"
        println "\n--- ETAP 3: Misja w stanie 'UPLOADED' ---"
        def response3 = grundClient.getData("/missions/${missionId}")
        def state3 = response3.json
        state3.status == "UPLOADED"
        state3._links.'upload-to-satellite' == null
    }
}