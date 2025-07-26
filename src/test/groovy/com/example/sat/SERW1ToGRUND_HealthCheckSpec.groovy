package com.example.sat

import com.example.sat.dto.PhotoData // <-- DODAJ IMPORT
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.IntermediateServerApp
import com.example.sat.http.SimpleHttpClient
import spock.lang.Shared
import spock.lang.Specification

class SERW1ToGRUND_HealthCheckSpec extends Specification {

    @Shared GroundControlServerApp grundServer
    @Shared IntermediateServerApp intermediateServer
    @Shared SimpleHttpClient intermediateClient

    // setupSpec jest OK, zostaje bez zmian
    def setupSpec() {
        // 1. Uruchom serwer docelowy (GRUND)
        grundServer = new GroundControlServerApp()
        grundServer.start(0, "") // URL satelity jest niepotrzebny w tym teście
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        // 2. Uruchom serwer źródłowy (SERW1), mówiąc mu, gdzie jest GRUND
        intermediateServer = new IntermediateServerApp()
        intermediateServer.start(0, grundServerUrl)
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        // 3. Stwórz klienta do sterowania serwerem SERW1
        intermediateClient = new SimpleHttpClient(intermediateServerUrl)
    }

    // cleanupSpec jest OK
    def cleanupSpec() {
        grundServer?.stop()
        intermediateServer?.stop()
    }

    def cleanup() {
        // Resetuj stan OBU serwerów, aby testy były od siebie w pełni niezależne
        intermediateServer.resetState()
        grundServer.resetState() // <-- DODAJ TĘ LINIĘ

        // Flagi najlepiej resetować po stanie
        grundServer.forceHealthStatus(false)
    }

    def "should successfully forward data when ground control is healthy"() {
        given: "intermediate server has data to forward and ground control is healthy"
        // POPRAWKA: Wstawiaj do bazy obiekt PhotoData, a nie mapę,
        // zgodnie z tym, jak działa teraz serwer.
        intermediateServer.database.insertPhoto(new PhotoData(photoId: "HEALTHY-FWD-1", content: "test"))
        grundServer.forceHealthStatus(false) // Upewniamy się, że jest zdrowy

        when: "the forward process is triggered"
        def response = intermediateClient.sendGenericPost("/forward", [:])

        then: "the operation succeeds and GRUND's database is updated"
        response.statusCode == 200
        response.json.status == "OK"
        // DODATKOWA ASERCJA: Sprawdźmy, czy dane faktycznie dotarły
        grundServer.getDbPhotoCount() == 1
    }

    def "should abort forwarding when ground control is unhealthy"() {
        given: "intermediate server has data to forward but ground control is unhealthy"
        // POPRAWKA: Wstawiaj do bazy obiekt PhotoData
        intermediateServer.database.insertPhoto(new PhotoData(photoId: "UNHEALTHY-FWD-1", content: "test"))
        grundServer.forceHealthStatus(true) // Symulujemy awarię

        when: "the forward process is triggered"
        def response = intermediateClient.sendGenericPost("/forward", [:])

        then: "the operation fails and no data is transferred"
        response.statusCode == 502 // Bad Gateway
        response.json.status == "ERROR"
        response.json.message.contains("unhealthy")
        // DODATKOWA ASERCJA: Upewnijmy się, że baza GRUND jest pusta
        grundServer.getDbPhotoCount() == 0
        // I że dane wciąż są w serwerze pośredniczącym
        intermediateServer.getDbPhotoCount() == 1
    }
}