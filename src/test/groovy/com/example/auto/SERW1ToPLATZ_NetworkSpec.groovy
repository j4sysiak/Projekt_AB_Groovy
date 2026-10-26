package com.example.auto

import com.example.auto.fakes.FakeFileSystem
import com.example.auto.fakes.FakeRelationalDB
import com.example.auto.fakes.HttpServerFactory
import com.example.auto.server.IntermediateServerApp
import com.example.auto.http.SimpleHttpClient
import spock.lang.Specification

class SERW1ToPLATZ_NetworkSpec extends Specification {

    IntermediateServerApp intermediateServer
    SimpleHttpClient client
    // Potrzebujemy dostępu do "fejka", żeby wstawić plik
    FakeFileSystem fakeFileSystem

    def setup() {
        // === ZMIANA: Pełne wstrzykiwanie zależności ===

        // 1. Stwórz testowe implementacje zależności
        fakeFileSystem = new FakeFileSystem()
        def fakeDb = new FakeRelationalDB()
        def fakeHttp = HttpServerFactory.create()

        // 2. Stwórz i uruchom IntermediateServerApp, wstrzykując zależności
        intermediateServer = new IntermediateServerApp(fakeHttp, fakeFileSystem, fakeDb)
        intermediateServer.start(0) // Nie potrzebuje URL-a do PLATZ w tym teście

        // 3. Stwórz klienta do komunikacji
        def serverUrl = "http://localhost:${intermediateServer.getPort()}"
        client = new SimpleHttpClient(serverUrl)
    }

    def cleanup() {
        intermediateServer?.stop()
    }

    def "intermediate server should process an ingested file and make data available"() {
        given: "a data package has been stored as a file on the server with complete data"
        def jsonData = """
            [
                {
                    "photoId": "FINAL_IMG_001",
                    "actionId": "TEST-ACTION-NET",
                    "content": "Test content",
                    "timestamp": ${System.currentTimeMillis()},
                    "quality": 99
                }
            ]
        """
        // ZMIANA: Używamy pola `fakeFileSystem` do wstawienia pliku
        fakeFileSystem.saveFile("test_package.json", jsonData.stripIndent())

        when: "the processing endpoint is called"
        def processResponse = client.sendGenericPost("/process", [:])

        and: "the final data is requested from the data endpoint"
        def dataResponse = client.getData("/data")

        then: "all operations were successful and data was retrieved"
        processResponse.statusCode == 200
        dataResponse.statusCode == 200

        def finalData = dataResponse.json as List
        finalData.size() == 1
        finalData[0].photoId == "FINAL_IMG_001"
        finalData[0].actionId == "TEST-ACTION-NET"
    }
}