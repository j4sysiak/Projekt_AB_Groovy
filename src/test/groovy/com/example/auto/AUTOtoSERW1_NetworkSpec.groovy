package com.example.auto

import com.example.auto.dto.PhotoCollectionAction
import com.example.auto.dto.PhotoData
import com.example.auto.dto.PhotoTask
import com.example.auto.dto.PhotoTaskStatus
import com.example.auto.fakes.FakeFileSystem
import com.example.auto.fakes.FakeRelationalDB
import com.example.auto.fakes.HttpServerFactory
import com.example.auto.fakes.AutoCheck
import com.example.auto.http.SimpleHttpClient
import com.example.auto.server.IntermediateServerApp
import com.example.auto.server.AutoCheckServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class AUTOtoSERW1_NetworkSpec extends Specification {

    IntermediateServerApp intermediateServer
    AutoCheckServer autoServer
    AutoCheck checker
    SimpleHttpClient autoClient

    def setup() {
        // === ZMIANA: Pełne wstrzykiwanie zależności ===

        // 1. Stwórz testowe implementacje zależności
        checker = new AutoCheck()

        def intermediateDb = new FakeRelationalDB()
        def intermediateFs = new FakeFileSystem()

        def intermediateHttp = HttpServerFactory.create()
        def autoHttp = HttpServerFactory.create()

        // 2. Stwórz i uruchom serwery, wstrzykując ich zależności

        intermediateServer = new IntermediateServerApp(intermediateHttp, intermediateFs, intermediateDb)
        intermediateServer.start(0, "")
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        autoServer = new AutoCheckServer(checker, autoHttp)
        autoServer.start(0)
        // Dokończ konfigurację połączenia
        autoServer.updateDownlinkTarget(intermediateServerUrl)

        def autoServerUrl = "http://localhost:${autoServer.getPort()}"
        autoClient = new SimpleHttpClient(autoServerUrl)
    }

    def cleanup() {
        autoServer?.stop()
        intermediateServer?.stop()
    }

    def "auto should successfully downlink data to the intermediate server"() {
        given: "the auto has photos in its manifest and storage"
        // Nie potrzebujemy już resetState(), bo setup() tworzy świeże instancje
        def conditions = new PollingConditions(timeout: 5)

        def taskId = "TEST_IMG_1"
        def actionId = "ACTION-NETWORK-1"

        def task = new PhotoTask(taskId: taskId, status: PhotoTaskStatus.TAKEN_AND_QUEUED)
        checker.activeManifest = new PhotoCollectionAction(actionId: actionId, tasks: [task])

        // Uzupełniamy dane testowe o brakujące pola
        checker.photoStorage[taskId] = new PhotoData(
                photoId: taskId,
                actionId: actionId,
                timestamp: System.currentTimeMillis(),
                quality: 95,
                content: "Simulated image content"
        )

        when: "the downlink process is triggered"
        def response = autoClient.sendGenericPost("/downlink", [:])

        then: "the auto server confirms the downlink"
        response.statusCode == 200

        and: "the intermediate server received and stored the data as a file"
        // Używamy PollingConditions zamiast Thread.sleep
        conditions.eventually {
            assert intermediateServer.getFileSystemFileCount() == 1
        }
    }
}
