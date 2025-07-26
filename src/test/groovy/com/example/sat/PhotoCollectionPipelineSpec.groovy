package com.example.sat

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.IntermediateServerApp
import com.example.sat.server.SatelliteSimulatorServer
import spock.lang.Specification
import com.example.sat.dto.PhotoTaskStatus

class PhotoCollectionPipelineSpec extends Specification {

    GroundControlServerApp grundServer
    IntermediateServerApp intermediateServer
    SatelliteSimulatorServer satServer

    SimpleHttpClient grundClient
    SimpleHttpClient intermediateClient
    SimpleHttpClient satClient

    SatelliteSimulator simulator

    def setup() {
        simulator = new SatelliteSimulator()

        // 1. Uruchom serwer SAT - nie zależy od niczego
        satServer = new SatelliteSimulatorServer(simulator, "")
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"
        satClient = new SimpleHttpClient(satServerUrl)

        // 2. Uruchom serwer GRUND - zależy od SAT
        grundServer = new GroundControlServerApp()
        grundServer.start(0, satServerUrl)
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"
        grundClient = new SimpleHttpClient(grundServerUrl)
        grundServer.resetState()

        // 3. Uruchom serwer SERW1 - zależy od GRUND
        intermediateServer = new IntermediateServerApp()
        intermediateServer.start(0, grundServerUrl)
        def intermediateServerUrl = "http://localhost:${intermediateServer.getPort()}"
        intermediateClient = new SimpleHttpClient(intermediateServerUrl)

        // 4. Skonfiguruj SAT z poprawnym celem downlink
        satServer.updateDownlinkTarget(intermediateServerUrl)

        // Czekamy na stabilizację
        Thread.sleep(1500)
    }

    def cleanup() {
        grundServer?.stop()
        intermediateServer?.stop()
        satServer?.stop()
    }

    def "should process a photo collection mission in batches through the entire pipeline"() {
        given: "a mission to collect 120 photos"
        def missionRequest = [photoCount: 120]



        when: "the mission is submitted to GRUND"
        def missionResponse = grundClient.sendGenericPost("/missions/photo-collection", missionRequest)

        then: "GRUND accepts it and SAT receives the manifest"
        missionResponse.statusCode == 202
        assert simulator.activeManifest?.tasks?.size() == 120



        when: "the satellite takes all the photos"
        simulator.simulatePhotoTakingProcess()

        then: "photo storage contains all photos"
        assert simulator.photoStorage.size() == 120

        // --- DIAGNOSTYKA ---
        println "----------------- START DIAGNOSTYKI -----------------"
        def firstTask = simulator.activeManifest.tasks.first()
        println "DEBUG: Zawartość pierwszego zadania: " + firstTask

        def statusValue = firstTask.status
        println "DEBUG: Wartość w 'it.status': " + statusValue
        println "DEBUG: Typ wartości w 'it.status': " + statusValue.getClass().getName()
        println "DEBUG: Typ stałej Enum, z którą porównujemy: " + PhotoTaskStatus.TAKEN_AND_QUEUED.getClass().getName()

        // Sprawdzamy porównanie dla JEDNEGO elementu
        boolean comparisonResult = (statusValue == PhotoTaskStatus.TAKEN_AND_QUEUED)
        println "DEBUG: Wynik porównania (statusValue == PhotoTaskStatus.TAKEN_AND_QUEUED): " + comparisonResult
        println "------------------ KONIEC DIAGNOSTYKI ------------------"

        Thread.sleep(2000)

        // 1. Stwórz pustą listę na przefiltrowane wyniki
        def filteredTasks = []

        // 2. Przejdź przez wszystkie zadania z manifestu
        for (def task in simulator.activeManifest.tasks) {
            // 3. Sprawdź warunek dla każdego zadania
            if (task.status == PhotoTaskStatus.TAKEN_AND_QUEUED) {
                // 4. Jeśli warunek jest spełniony, dodaj zadanie do nowej listy
                filteredTasks.add(task)
            }
        }

        // 5. Pobierz rozmiar przefiltrowanej listy
        def numberOfTasksWithCorrectStatus = filteredTasks.size()

        // 6. Wykonaj asercję
        assert numberOfTasksWithCorrectStatus == 120




        
        when: "three downlink opportunities occur and data is processed"
        3.times {
            satClient.sendGenericPost("/downlink", [:])
            Thread.sleep(1300)
            intermediateClient.sendGenericPost("/process", [:])
            intermediateClient.sendGenericPost("/forward", [:])
            Thread.sleep(1300)
        }

        then: "GRUND's database contains exactly 120 photos at the end"
        Thread.sleep(3000)
        assert grundServer.getDbPhotoCount() == 120
    }
}
