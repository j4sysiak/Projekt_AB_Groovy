package com.example.sat

import com.example.sat.fakes.*

// Importujemy wszystkie nasze niezawodne fejki

class DataPipelineSpec extends SomeBaseTest { // Dziedziczymy, aby mieć dostęp do SYSTEM_CONFIGS

    //DataPipelineSpec() {}

    def "should successfully flow data from satellite, through intermediate server SERW1, to ground control"() {
        given: "a fully interconnected system of simulated servers and fakes"
        def satelliteId = "SAT-PIPELINE-01"
       // def config = SYSTEM_CONFIGS["ALPHA"]

        // === ETAP 1: Budowa Komponentów ===

        // 1. Tworzymy "prawdziwy" system naziemny i serwer pośredniczący
        def groundServer = new FakeGroundControlServer()
        def intermediateServer = new IntermediateServer(groundControlServer: groundServer)  // SERW1

        // 2. Tworzymy pełny, działający ReprocessingService, używając naszych fejków i mocków
        //    To jest serce, które będzie działało "na pokładzie" satelity.
        def singleReprocessor = new ReprocessingServiceImpl(
                new ManualMockConnection(dataToReturn: "PIPELINE_DATA"),
                new ManualMockDataRepository(),
                new ManualMockTokenCache(tokenToReturn: "VALID-PIPELINE-TOKEN"),
                new ManualMockTelemetryModule(),
                new ManualMockCommandModule(),
                new InputValidatorImpl(), // Używamy prawdziwego walidatora
                new ManualMockPassPredictor(isWindowOpen: true),
                new ManualMockPositionVerifier(isPositionOk: true),
                new MeteorologicalDataAnalyzerImpl(), // Prawdziwy analizator
                new ManualMockAlertingService()
        )

        // 3. Tworzymy serwis do ponawiania prób, który opakowuje naszego "robotnika"
        def batchReprocessor = new BatchReprocessingService(singleReprocessor)

        // 4. Tworzymy Listenera, który będzie nasłuchiwał na "kolejce"
        //    Używamy ręcznych mocków dla jego zależności, aby mieć pełną kontrolę
        def queue = new ManualMockProcessingQueue()
        def jobRepository = new ManualMockJobRepository()
        def listener = new QueueListener(queue, batchReprocessor, jobRepository)


        when: "the full data pipeline is triggered"
        // Symulujemy sekwencję zdarzeń w naszym rozproszonym systemie

        // Etap 1: Wiadomość z zadaniem pojawia się na kolejce (np. umieszczona przez JobCoordinator)
        println "\nPIPELINE STEP 1: Job appears on the queue..."
        queue.jobsToConsume = [satelliteId]

        // Etap 2: Listener pobiera wiadomość i uruchamia przetwarzanie
        println "\nPIPELINE STEP 2: Listener processes the job..."
        listener.processNextMessage()

        // Etap 3: Po jakimś czasie, 'prawdziwe' repozytorium jest aktualizowane
        // (W naszym teście dzieje się to natychmiast, bo ManualMockDataRepository jest synchroniczny)
        // W tym momencie dane są "zapisane" po stronie satelity.

        // Etap 4: Teraz symulujemy, że te zapisane dane są wysyłane do serwera pośredniczącego
        // W prawdziwym systemie to byłby osobny proces, tutaj symulujemy go ręcznie.
        println "\nPIPELINE STEP 3: Downlinking data to intermediate server..."
        def dataPackage = [[photoId: "IMG_PIPE_1", content: "PIPELINE_DATA"]]
        intermediateServer.receiveDataFromSatellite(groovy.json.JsonOutput.toJson(dataPackage))

        // Etap 5: SERW1 przetwarza i archiwizuje otrzymane pliki
        println "\nPIPELINE STEP 4: Archiving data..."
        intermediateServer.archiveFilesToDatabase()

        // Etap 6: SERW1 wysyła zarchiwizowane dane do systemu naziemnego
        println "\nPIPELINE STEP 5: Forwarding data..."
        intermediateServer.forwardDataToGroundControl()


        then: "the data has correctly passed through all stages"
        // Weryfikujemy stan końcowy każdego komponentu

        // Listener poprawnie zaktualizował status zadania
        jobRepository.successJobs.contains(satelliteId)

        // Serwer pośredniczący przetworzył pliki i jego magazyn jest pusty, a baza danych pełna
        intermediateServer.fileSystem.files.isEmpty()
        intermediateServer.database.photoTable.size() == 1

        // System naziemny otrzymał finalne dane
        groundServer.receivedPhotos.size() == 1
        groundServer.receivedPhotos[0].content == "PIPELINE_DATA"
    }
}