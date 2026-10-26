package com.example.auto

import com.example.auto.fakes.FakeFileSystem
import com.example.auto.fakes.FakeRelationalDB
import com.example.auto.fakes.HttpServerFactory
import com.example.auto.fakes.AutoCheck
import com.example.auto.http.SimpleHttpClient
import com.example.auto.server.AutoControlServerApp
import com.example.auto.server.IntermediateServerApp
import com.example.auto.server.AutoCheckServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import com.example.auto.dto.PhotoTaskStatus

class PLATZ_AUTO_SERW1_AUTO__EndToEndPipelineSpec extends Specification {
    AutoControlServerApp platzServer
    IntermediateServerApp intermediateServer
    AutoCheckServer autoServer
    SimpleHttpClient platzClient
    SimpleHttpClient intermediateClient
    SimpleHttpClient autoClient
    AutoCheck checker

    def setup() {
        // 1. Stwórz wszystkie "fałszywe" komponenty, których będziemy potrzebować
        checker = new AutoCheck()
        def platzDb = new FakeRelationalDB()
        def intermediateDb = new FakeRelationalDB()
        def intermediateFs = new FakeFileSystem()
        def platzHttp = HttpServerFactory.create()
        def intermediateHttp = HttpServerFactory.create()
        def autoHttp = HttpServerFactory.create()

        // 2. Stwórz i uruchom serwery, wstrzykując ich zależności
        autoServer = new AutoCheckServer(checker, autoHttp)
        autoServer.start(0)
        def autoServerUrl = "http://localhost:${autoServer.getPort()}"

        platzServer = new AutoControlServerApp(platzDb, platzHttp)
        platzServer.start(0, autoServerUrl)
        def platzServerUrl = "http://localhost:${platzServer.getPort()}"

        intermediateServer = new IntermediateServerApp(intermediateHttp, intermediateFs, intermediateDb)
        intermediateServer.start(0, platzServerUrl)
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"

        // 3. Dokończ konfigurację połączeń
        autoServer.updateDownlinkTarget(intermediateServerUrl)

        // 4. Stwórz klientów do komunikacji
        platzClient = new SimpleHttpClient(platzServerUrl)
        intermediateClient = new SimpleHttpClient(intermediateServerUrl)
        autoClient = new SimpleHttpClient(autoServerUrl)
    }

    def cleanup() {
        platzServer?.stop()
        intermediateServer?.stop()
        autoServer?.stop()
    }


    def "should process a large photo collection action in batches through the entire pipeline"() {
        given: "a action to collect photos is requested"
        def actionRequest = [photoCount: 120]
        def conditions = new PollingConditions(timeout: 10)

        // === ETAP 1: Stworzenie i wysłanie akcji ===
        when: "The action is submitted to PLATZ"
        def createResponse = platzClient.sendGenericPost("/action/photo-collection", actionRequest)

        then: "The action is created in PLANNED state"
        createResponse.statusCode == 202
        def actionId = createResponse.json.actionId
        actionId != null

        when: "The action is accepted and uploaded"
        platzClient.sendGenericPost("/action/${actionId}/accept", [:])
        def uploadResponse = platzClient.sendGenericPost("/action/${actionId}/upload", [:])

        then: "The manifest is successfully stored on the auto"
        uploadResponse.statusCode == 200
        // Używamy "cierpliwej" asercji, bo upload jest asynchroniczny
        conditions.eventually {
            assert checker.activeManifest != null
            assert checker.activeManifest.tasks.size() == 120
            assert checker.activeManifest.tasks.every { it.status == PhotoTaskStatus.PENDING }
        }
        Thread.sleep(500) // Pół sekundy powinno w zupełności wystarczyć

        // === ETAP 2: Wykonanie misji i downlink ===
        when: "AUTO executes the action and downlinks data in batches"
        checker.simulatePhotoTakingProcess()
        3.times {
            autoClient.sendGenericPost("/downlink", [:])
            Thread.sleep(500) // Daj pół sekundy na pełne przetworzenie jednego downlinku
        }

        then: "SERW1 has received all the files"
        conditions.eventually {
            assert intermediateServer.getFileSystemFileCount() == 3
        }

        // === ETAP 3: Przetworzenie i przekazanie danych ===
        when: "An operator commands SERW1 to process and forward the data"
        intermediateClient.sendGenericPost("/process", [:])
        def forwardResponse = intermediateClient.sendGenericPost("/forward", [:])

        then: "SERW1 confirms successful forwarding and PLATZ's database contains all the photos"
        forwardResponse.statusCode == 200
        conditions.eventually {
            assert platzServer.getDbPhotoCount() == 120
        }
    }
}