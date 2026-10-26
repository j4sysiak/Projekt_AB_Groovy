package com.example.auto

import com.example.auto.dto.PhotoData
import com.example.auto.fakes.FakeFileSystem
import com.example.auto.fakes.FakeRelationalDB
import com.example.auto.fakes.HttpServerFactory
import com.example.auto.server.AutoControlServerApp
import com.example.auto.server.IntermediateServerApp
import com.example.auto.http.SimpleHttpClient
import spock.lang.Shared
import spock.lang.Specification

class SERW1ToPLATZ_HealthCheckSpec extends Specification {

    @Shared AutoControlServerApp platzServer
    @Shared IntermediateServerApp intermediateServer
    @Shared SimpleHttpClient intermediateClient

    // Musimy też współdzielić "fejki", aby mieć do nich dostęp w testach
    @Shared FakeRelationalDB intermediateDb

    def setupSpec() {
        // === ZMIANA: Pełne wstrzykiwanie zależności ===

        // 1. Stwórz testowe implementacje zależności, które będą współdzielone
        def platzDb = new FakeRelationalDB()
        intermediateDb = new FakeRelationalDB() // Używamy pola @Shared
        def intermediateFs = new FakeFileSystem()
        def platzHttp = HttpServerFactory.create()
        def intermediateHttp = HttpServerFactory.create()

        // 2. Stwórz i uruchom AutoControlServerApp
        platzServer = new AutoControlServerApp(platzDb, platzHttp)
        platzServer.start(0, "")
        def platzServerUrl = "http://localhost:${platzServer.getPort()}"

        // 3. Stwórz i uruchom IntermediateServerApp
        intermediateServer = new IntermediateServerApp(intermediateHttp, intermediateFs, intermediateDb)
        intermediateServer.start(0, platzServerUrl)
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        // 4. Stwórz klienta do sterowania serwerem SERW1
        intermediateClient = new SimpleHttpClient(intermediateServerUrl)
    }

    def cleanupSpec() {
        platzServer?.stop()
        intermediateServer?.stop()
    }

    def cleanup() {
        // Resetujemy stan OBU serwerów przed każdym testem
        intermediateServer.resetState()
        platzServer.resetState()
        platzServer.forceHealthStatus(false)
    }

    def "should successfully forward data when ground control is healthy"() {
        given: "intermediate server has data to forward and ground control is healthy"
        // Używamy pola @Shared `intermediateDb` do wstawienia danych
        intermediateDb.insertPhoto(new PhotoData(
                photoId: "HEALTHY-FWD-1", actionId: "M1", content: "test",
                timestamp: 1L, quality: 90
        ))
        platzServer.forceHealthStatus(false)

        when: "the forward process is triggered"
        def response = intermediateClient.sendGenericPost("/forward", [:])

        then: "the operation succeeds and PLATZ's database is updated"
        response.statusCode == 200
        response.json.status == "OK"
        platzServer.getDbPhotoCount() == 1
    }

    def "should abort forwarding when ground control is unhealthy"() {
        given: "intermediate server has data to forward but ground control is unhealthy"
        intermediateDb.insertPhoto(new PhotoData(
                photoId: "UNHEALTHY-FWD-1", actionId: "M2", content: "test",
                timestamp: 2L, quality: 90
        ))
        platzServer.forceHealthStatus(true)

        when: "the forward process is triggered"
        def response = intermediateClient.sendGenericPost("/forward", [:])

        then: "the operation fails and no data is transferred"
        response.statusCode == 502
        response.json.status == "ERROR"

        platzServer.getDbPhotoCount() == 0
        intermediateServer.getDbPhotoCount() == 1
    }
}