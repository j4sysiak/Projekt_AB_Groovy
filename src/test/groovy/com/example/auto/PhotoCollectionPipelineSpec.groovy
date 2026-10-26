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
import com.example.auto.dto.PhotoTaskStatus
import spock.util.concurrent.PollingConditions

class PhotoCollectionPipelineSpec extends Specification {

    AutoControlServerApp platzServer
    IntermediateServerApp intermediateServer
    AutoCheckServer autoServer
    SimpleHttpClient platzClient
    SimpleHttpClient intermediateClient
    SimpleHttpClient autoClient
    AutoCheck checker

    // === ZMIANA: Pełne wstrzykiwanie zależności dla wszystkich serwerów ===
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

    def "should process a photo collection action in batches through the entire pipeline"() {
        given: "a action to collect 120 photos"
        def actionRequest = [photoCount: 120]
        def conditions = new PollingConditions(timeout: 15, delay: 0.5)

        // ETAP 1: Stworzenie, akceptacja i wysłanie akcji
        when: "the action is created, accepted and submitted to PLATZ"
        def createResponse = platzClient.sendGenericPost("/action/photo-collection", actionRequest)
        def actionId = createResponse.json.actionId
        platzClient.sendGenericPost("/action/${actionId}/accept", [:])
        def uploadResponse = platzClient.sendGenericPost("/action/${actionId}/upload", [:])

        then: "AUTO receives the manifest"
        uploadResponse.statusCode == 200
        conditions.eventually {
            assert checker.activeManifest != null
            assert checker.activeManifest.tasks.size() == 120
        }

        // ETAP 2: Wykonanie misji
        when: "the auto take"
        checker.simulatePhotoTakingProcess()

        then: "all photos are taken and queued for downlink"
        checker.activeManifest.tasks.count { it.status == PhotoTaskStatus.TAKEN_AND_QUEUED } == 120

        // ETAP 3: Downlink
        when: "three downlink opportunities occur"
        3.times {
            autoClient.sendGenericPost("/downlink", [:])
            Thread.sleep(200)
        }

        then: "SERW1 has received all the files"
        conditions.eventually {
            assert intermediateServer.getFileSystemFileCount() == 3
        }

        // ETAP 4: Przetworzenie i przekazanie
        when: "SERW1 is commanded to process and forward the data"
        intermediateClient.sendGenericPost("/process", [:])
        def forwardResponse = intermediateClient.sendGenericPost("/forward", [:])

        then: "SERW1 confirms forwarding and PLATZ's database contains all photos"
        forwardResponse.statusCode == 200
        conditions.eventually {
            assert platzServer.getDbPhotoCount() == 120
        }
    }
}