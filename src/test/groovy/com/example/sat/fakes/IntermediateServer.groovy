package com.example.sat.fakes

import com.example.sat.dto.PhotoData
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class IntermediateServer {
    // Wewnętrzne komponenty SERW1
    FakeFileSystem fileSystem = new FakeFileSystem()
    FakeRelationalDB database = new FakeRelationalDB()

    // "Połączenie sieciowe" do serwera naziemnego
    FakeGroundControlServer groundControlServer

    // Endpoint, który odbiera dane od satelity
    void receiveDataFromSatellite(String jsonPayload) {
        println "SERW1: Receiving data package from satellite."
        // Zapisujemy cały pakiet jako jeden plik
        def fileName = "downlink_package_${System.currentTimeMillis()}.json"
        fileSystem.saveFile(fileName, jsonPayload)
    }

    // Proces, który archiwizuje pliki do bazy danych
    void archiveFilesToDatabase() {
        println "SERW1: Starting archiving process."
        def files = fileSystem.readAllFiles()
        files.each { fileName, jsonContent ->
            def photos = new JsonSlurper().parseText(jsonContent)
            photos.each { photoMap ->
                // Zapisujemy każdy obiekt "zdjęcia" jako osobny wiersz w bazie
                database.insertPhoto(photoMap)
            }
            fileSystem.deleteFile(fileName) // Usuwamy plik po zarchiwizowaniu
        }
    }

    // Proces, który wysyła dane do systemu naziemnego
    void forwardDataToGroundControl() {
        println "SERW1: Forwarding archived data to Ground Control."
        def photos = database.getArchivedPhotos()
        // W prawdziwym świecie to byłoby zapytanie HTTP POST
        groundControlServer.receiveFinalData(JsonOutput.toJson(photos))
    }
}