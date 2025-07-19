package com.example.sat

import com.example.sat.server.IntermediateServerApp
import com.example.sat.utils.http.SimpleHttpClient
import spock.lang.Specification

class Server1ToGroundNetworkSpec extends Specification {

    IntermediateServerApp intermediateServer
    SimpleHttpClient client

    def setup() {
        intermediateServer = new IntermediateServerApp()
        // Wołamy teraz metodę start z JEDNYM argumentem. Drugi będzie domyślnie null.
        intermediateServer.start(0)
        def serverUrl = "http://localhost:${intermediateServer.getPort()}"
        client = new SimpleHttpClient(serverUrl)
    }

    def cleanup() {
        intermediateServer?.stop()
        intermediateServer?.resetState()
    }

    def "intermediate server should process an ingested file and make data available"() {
        given: "a data package has been stored as a file on the server"
        def jsonData = '[{"photoId":"FINAL_IMG_001", "quality":"HIGH"}]'
        intermediateServer.fileSystem.saveFile("test_package.json", jsonData)

        when: "the processing endpoint is called"
        client.sendGenericPost("/process", [:])

        and: "the final data is requested from the data endpoint"
        def dataResponse = client.getData("/data")

        then: "the data was correctly processed and retrieved"
        dataResponse.statusCode == 200
        def finalData = dataResponse.json as List
        finalData[0].photoId == "FINAL_IMG_001"
    }
}