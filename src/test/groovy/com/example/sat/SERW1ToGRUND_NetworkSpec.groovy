package com.example.sat

import com.example.sat.server.IntermediateServerApp
import com.example.sat.http.SimpleHttpClient
import spock.lang.Specification

class SERW1ToGRUND_NetworkSpec extends Specification {

    IntermediateServerApp intermediateServer
    SimpleHttpClient client

    def setup() {
        intermediateServer = new IntermediateServerApp()
        intermediateServer.start(0)
        def serverUrl = "http://localhost:${intermediateServer.getPort()}"
        client = new SimpleHttpClient(serverUrl)
    }

    def cleanup() {
        intermediateServer?.stop()
    }

    // Usunięto cleanup(), bo setup() dla każdego testu tworzy świeżą instancję.
    // Resetowanie stanu nie jest już potrzebne i może zaciemniać obraz.

    def "intermediate server should process an ingested file and make data available"() {
        given: "a data package has been stored as a file on the server with complete data"
        // POPRAWKA: Tworzymy kompletny obiekt JSON, zgodny z nowym DTO PhotoData
        def jsonData = """
            [
                {
                    "photoId": "FINAL_IMG_001",
                    "missionId": "TEST-MISSION-NET",
                    "content": "Test content",
                    "timestamp": ${System.currentTimeMillis()},
                    "quality": 99
                }
            ]
        """
        intermediateServer.fileSystem.saveFile("test_package.json", jsonData.stripIndent())

        when: "the processing endpoint is called"
        def processResponse = client.sendGenericPost("/process", [:])

        and: "the final data is requested from the data endpoint"
        def dataResponse = client.getData("/data")

        then: "all operations were successful and data was retrieved"
        // Dobrą praktyką jest sprawdzenie statusu każdej operacji
        processResponse.statusCode == 200

        dataResponse.statusCode == 200
        def finalData = dataResponse.json as List

        // Asercje na zawartość
        finalData.size() == 1
        finalData[0].photoId == "FINAL_IMG_001"
        finalData[0].missionId == "TEST-MISSION-NET" // Sprawdźmy też nowe pole
    }
}