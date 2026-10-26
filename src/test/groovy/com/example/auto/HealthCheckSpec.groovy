package com.example.auto

import com.example.auto.fakes.FakeFileSystem
import com.example.auto.fakes.FakeRelationalDB
import com.example.auto.fakes.HttpServerFactory
import com.example.auto.fakes.AutoCheck
import com.example.auto.server.IntermediateServerApp
import com.example.auto.server.AutoCheckServer
import com.example.auto.http.SimpleHttpClient
import com.example.auto.dto.PhotoTaskStatus
import com.example.auto.dto.PhotoTask
import com.example.auto.dto.PhotoCollectionAction
import com.example.auto.dto.PhotoData
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class HealthCheckSpec extends Specification {

    IntermediateServerApp intermediateServer
    AutoCheckServer autoServer
    AutoCheck checker
    SimpleHttpClient autoClient

    def setup() {
        // === ZMIANA: Pełne wstrzykiwanie zależności ===

        // 1. Stwórz wszystkie "fałszywe" komponenty
        checker = new AutoCheck()

        def intermediateDb = new FakeRelationalDB()
        def intermediateFs = new FakeFileSystem()

        def intermediateHttp = HttpServerFactory.create()
        def autoHttp = HttpServerFactory.create()

        // 2. Stwórz i uruchom serwery, wstrzykując ich zależności

        // Serwer pośredniczący nie potrzebuje adresu PLATZ w tym teście
        intermediateServer = new IntermediateServerApp(intermediateHttp, intermediateFs, intermediateDb)
        intermediateServer.start(0, "")
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        // Serwer musi wiedzieć, gdzie jest serwer pośredniczący
        autoServer = new AutoCheckServer(checker, autoHttp)
        autoServer.start(0)
        autoServer.updateDownlinkTarget(intermediateServerUrl) // Konfigurujemy cel po starcie

        // 3. Stwórz klienta do komunikacji z serwerem
        def autoServerUrl = "http://localhost:${autoServer.getPort()}"
        autoClient = new SimpleHttpClient(autoServerUrl)
    }

    def cleanup() {
        autoServer?.stop()
        intermediateServer?.stop()
    }



    def "should successfully downlink data when intermediate server is healthy"() {
        given: "the auto has one photo ready and the intermediate server is healthy"
        intermediateServer.forceHealthStatus(false)
        def conditions = new PollingConditions(timeout: 5)

        def taskId = "HEALTHY-TASK-01"
        def actionId = "TEST-ACTION-1"

        def task = new PhotoTask(taskId: taskId, status: PhotoTaskStatus.TAKEN_AND_QUEUED)
        checker.activeManifest = new PhotoCollectionAction(actionId: actionId, tasks: [task])

        checker.photoStorage[taskId] = new PhotoData(
                photoId: taskId, actionId: actionId,
                content: 'Healthy test data', timestamp: System.currentTimeMillis(), quality: 95
        )

        when: "the downlink is triggered"
        def response = autoClient.sendGenericPost("/downlink", [:])

        then: "the operation succeeds and data is transferred"
        response.statusCode == 200
        // Używamy "cierpliwej" asercji zamiast Thread.sleep
        conditions.eventually {
            assert intermediateServer.getFileSystemFileCount() == 1
        }
    }

    def "should abort downlink when intermediate server is unhealthy"() {
        given: "the auto has one photo ready but the intermediate server is unhealthy"
        intermediateServer.forceHealthStatus(true)

        def taskId = "UNHEALTHY-TASK-01"
        def actionId = "TEST-ACTION-2"

        def task = new PhotoTask(taskId: taskId, status: PhotoTaskStatus.TAKEN_AND_QUEUED)
        checker.activeManifest = new PhotoCollectionAction(actionId: actionId, tasks: [task])

        checker.photoStorage[taskId] = new PhotoData(
                photoId: taskId, actionId: actionId,
                content: 'Unhealthy test data', timestamp: System.currentTimeMillis(), quality: 98
        )

        when: "the downlink is triggered"
        def response = autoClient.sendGenericPost("/downlink", [:])

        then: "the operation fails and no data is transferred"
        response.statusCode == 502

        // Upewniamy się, że dane nie zostały usunięte
        checker.photoStorage.size() == 1
        // I że nic nie trafiło do serwera pośredniczącego
        intermediateServer.getFileSystemFileCount() == 0
    }

}