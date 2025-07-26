package com.example.sat

// Niektóre importy nie będą już potrzebne
// import com.example.sat.domain.Coordinates
// import com.example.sat.dto.MissionRequest

import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.http.SimpleHttpClient
import com.example.sat.server.GroundControlServerApp
import com.example.sat.server.SatelliteSimulatorServer
import spock.lang.Specification

class MissionUploadSpec extends Specification {

    GroundControlServerApp grundServer
    SatelliteSimulatorServer satServer
    SatelliteSimulator simulator
    SimpleHttpClient grundClient

    // Metoda setup() jest w porządku, zostaje bez zmian
    def setup() {
        simulator = new SatelliteSimulator()
        satServer = new SatelliteSimulatorServer(simulator, "")
        satServer.start(0)
        def satServerUrl = "http://localhost:${satServer.getPort()}"

        grundServer = new GroundControlServerApp()
        grundServer.start(0, satServerUrl)
        def grundServerUrl = "http://localhost:${grundServer.getPort()}"

        grundClient = new SimpleHttpClient(grundServerUrl)

        // Usunięto setMissionPlanner, bo ta logika jest teraz prawdopodobnie
        // wewnątrz GroundControlServerApp i nie trzeba jej wstrzykiwać
    }

    // Metoda cleanup() jest w porządku
    def cleanup() {
        grundServer?.stop()
        satServer?.stop()
    }

    // ZMIENIONA NAZWA I LOGIKA TESTU
    def "ground server should successfully create and upload a photo collection mission"() {
        given: "a request to collect 5 photos"
        // ZMIANA: Tworzymy prostą mapę, której oczekuje nowy endpoint
        def missionRequest = [photoCount: 5]

        when: "the mission request is sent to the ground control server"
        // ZMIANA: Używamy poprawnego, istniejącego endpointu
        def response = grundClient.sendGenericPost("/missions/photo-collection", missionRequest)

        then: "the ground server accepts the mission and confirms upload to the satellite"
        // Sprawdzamy, czy serwer naziemny poprawnie przetworzył i przekazał misję
        response.statusCode == 202
        def responseBody = response.json
        responseBody.status == "UPLOADED"
        def missionId = responseBody.missionId
        missionId != null

        and: "the satellite simulator has stored the correct manifest"
        // Sprawdzamy, czy satelita otrzymał i zapisał misję
        // Używamy eventually/sleep, bo komunikacja jest asynchroniczna
        Thread.sleep(1000) // Dajmy chwilę na komunikację sieciową

        simulator.activeManifest != null
        simulator.activeManifest.missionId == missionId
        simulator.activeManifest.tasks.size() == 5
    }
}