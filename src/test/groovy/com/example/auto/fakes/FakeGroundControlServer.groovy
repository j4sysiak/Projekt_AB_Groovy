package com.example.auto.fakes


import com.example.auto.dto.CommandResponse
import groovy.json.JsonSlurper

class FakeGroundControlServer {
    FakeAutoServer autoServer

    FakeGroundControlServer() {
        println "Tworzymy \"prawdziwy\" system naziemny"
    }

    // NOWE POLE: przechowuje finalnie odebrane dane
    List<Map> receivedPhotos = []

    // NOWA LOGIKA: Metody do zarządzania sesją
    void connectToAuto(String authToken) {
        println "PLATZ_SERVER: Establishing connection to auto..."
        autoServer.handleConnect(authToken)
    }

    void disconnectFromAuto() {
        println "PLATZ_SERVER: Closing connection to Auto..."
        autoServer.handleDisconnect()
    }

    // Metoda-klient, która wysyła komendę przez "sieć"
    CommandResponse sendCommand(String command, String authToken) {
        println "PLATZ_SERVER: Sending command '${command}' over existing connection."
        def jsonResponse = autoServer.handleCommandRequest(command, authToken)

        // 3. Deserializacja odpowiedzi JSON (tak jak robiłby to klient HTTP)
        def responseMap = new JsonSlurper().parseText(jsonResponse)
        return new CommandResponse(responseMap)
    }

    // Metoda requestPhotos teraz zakłada, że połączenie już istnieje
    void requestPhotos(int count, String quality) {
        println "PLATZ_SERVER: Requesting ${count} photos of ${quality} quality."
        autoServer.handleTakePhotosRequest(count, quality)
    }

    // NOWY ENDPOINT: do odbierania danych z SERW1
    void receiveFinalData(String jsonPayload) {
        println "PLATZ_SERVER: Received final data package."
        this.receivedPhotos.addAll(new JsonSlurper().parseText(jsonPayload))
    }

}