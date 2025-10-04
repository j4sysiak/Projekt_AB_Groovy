package com.example.sat

import com.example.sat.dto.PhotoData
import com.example.sat.fakes.FakeFileSystem
import com.example.sat.fakes.FakeRelationalDB
import com.example.sat.fakes.HttpServerFactory
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.IntermediateServerApp
import com.example.sat.http.SimpleHttpClient
import spock.lang.Shared
import spock.lang.Specification

class SERW1ToGRUND_HealthCheckSpec extends Specification {

    @Shared GroundControlServerApp grundServer
    @Shared IntermediateServerApp intermediateServer
    @Shared SimpleHttpClient intermediateClient

    // Musimy też współdzielić "fejki", aby mieć do nich dostęp w testach
    @Shared FakeRelationalDB intermediateDb

    def setupSpec() {
        // === ZMIANA: Pełne wstrzykiwanie zależności ===

        // 1. Stwórz testowe implementacje zależności, które będą współdzielone
        def grundDb = new FakeRelationalDB()
        intermediateDb = new FakeRelationalDB() // Używamy pola @Shared
        def intermediateFs = new FakeFileSystem()
        def grundHttp = HttpServerFactory.create()
        def intermediateHttp = HttpServerFactory.create()

        // 2. Stwórz i uruchom GroundControlServerApp
        grundServer = new GroundControlServerApp(grundDb, grundHttp)
        grundServer.start(0, "")
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        // 3. Stwórz i uruchom IntermediateServerApp
        intermediateServer = new IntermediateServerApp(intermediateHttp, intermediateFs, intermediateDb)
        intermediateServer.start(0, grundServerUrl)
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        // 4. Stwórz klienta do sterowania serwerem SERW1
        intermediateClient = new SimpleHttpClient(intermediateServerUrl)
    }

    def cleanupSpec() {
        grundServer?.stop()
        intermediateServer?.stop()
    }

    def cleanup() {
        // Resetujemy stan OBU serwerów przed każdym testem
        intermediateServer.resetState()
        grundServer.resetState()
        grundServer.forceHealthStatus(false)
    }

    def "should successfully forward data when ground control is healthy"() {
        given: "intermediate server has data to forward and ground control is healthy"
        // Używamy pola @Shared `intermediateDb` do wstawienia danych
        intermediateDb.insertPhoto(new PhotoData(
                photoId: "HEALTHY-FWD-1", missionId: "M1", content: "test",
                timestamp: 1L, quality: 90
        ))
        grundServer.forceHealthStatus(false)

        when: "the forward process is triggered"
        def response = intermediateClient.sendGenericPost("/forward", [:])

        then: "the operation succeeds and GRUND's database is updated"
        response.statusCode == 200
        response.json.status == "OK"
        grundServer.getDbPhotoCount() == 1
    }

    def "should abort forwarding when ground control is unhealthy"() {
        given: "intermediate server has data to forward but ground control is unhealthy"
        intermediateDb.insertPhoto(new PhotoData(
                photoId: "UNHEALTHY-FWD-1", missionId: "M2", content: "test",
                timestamp: 2L, quality: 90
        ))
        grundServer.forceHealthStatus(true)

        when: "the forward process is triggered"
        def response = intermediateClient.sendGenericPost("/forward", [:])

        then: "the operation fails and no data is transferred"
        response.statusCode == 502
        response.json.status == "ERROR"

        grundServer.getDbPhotoCount() == 0
        intermediateServer.getDbPhotoCount() == 1
    }
}