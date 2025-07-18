package com.example.sat.fakes

import com.example.sat.dto.CommandRequest
import com.example.sat.dto.CommandResponse
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.example.sat.dto.TakePhotosRequest

// Symuluje system naziemny, który wysyła komendy  ------>   to Satellite
class FakeGroundControlServer {
    FakeSatelliteServer satelliteServer // "Połączenie sieciowe" do serwera satelity

    // NOWE POLE: przechowuje finalnie odebrane dane
    List<Map> receivedPhotos = []

    // NOWA LOGIKA: Metody do zarządzania sesją
    void connectToSatellite(String authToken) {
        println "GRUND_SERVER: Establishing connection to satellite..."
        satelliteServer.handleConnect(authToken)
    }

    void disconnectFromSatellite() {
        println "GRUND_SERVER: Closing connection to satellite..."
        satelliteServer.handleDisconnect()
    }

    // Metoda-klient, która wysyła komendę przez "sieć"
    CommandResponse sendCommand(String command, String authToken) {
        println "GRUND_SERVER: Sending command '${command}' over existing connection."
        def jsonResponse = satelliteServer.handleCommandRequest(command, authToken)

        // 3. Deserializacja odpowiedzi JSON (tak jak robiłby to klient HTTP)
        def responseMap = new JsonSlurper().parseText(jsonResponse)
        return new CommandResponse(responseMap)
    }

    // Metoda requestPhotos teraz zakłada, że połączenie już istnieje
    void requestPhotos(int count, String quality) {
        println "GRUND_SERVER: Requesting ${count} photos of ${quality} quality."
        satelliteServer.handleTakePhotosRequest(count, quality)
    }

    // NOWY ENDPOINT: do odbierania danych z SERW1
    void receiveFinalData(String jsonPayload) {
        println "GRUND_SERVER: Received final data package."
        this.receivedPhotos.addAll(new JsonSlurper().parseText(jsonPayload))
    }

}