package com.example.sat.fakes

import com.example.sat.dto.CommandRequest
import com.example.sat.dto.CommandResponse
import com.example.sat.dto.TakePhotosRequest
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// Symuluje serwer na pokładzie satelity, który odbiera komendy   <-----------    od Ground Control Server
class FakeSatelliteServer {
    SatelliteSimulator simulator // Używa naszego istniejącego symulatora do wykonania pracy

    // NOWE ENDPOINTY DO ZARZĄDZANIA SESJĄ
    void handleConnect(String authToken) {
        simulator.connect(authToken)
    }

    void handleDisconnect() {
        simulator.disconnect()
    }

    // Metoda-endpoint, która obsługuje przychodzące zapytania
    String handleCommandRequest(String command, String authToken) {
        println "SAT_SERVER: Received JSON request: ${jsonRequest}"

        println "SAT_SERVER: Received command '${command}'"
        String responseMessage = simulator.sendCommand(command)

        // 3. Stworzenie obiektu odpowiedzi
        def response = new CommandResponse(
                status: responseMessage.startsWith("ACK") ? "ACK" : "NACK",
                message: responseMessage,
                batteryLevel: simulator.batteryLevel // Dołączamy dodatkowe dane telemetryczne
        )

        // 4. Serializacja odpowiedzi do JSON
        def jsonResponse = JsonOutput.toJson(response)
        println "SAT_SERVER: Sending JSON response: ${jsonResponse}"
        return jsonResponse
    }

    // Endpoint do zlecania zdjęć (już nie robi connect/disconnect)
    String handleTakePhotosRequest(int numberOfPhotos, String quality) {
        println "SAT_SERVER: Received TAKE_PHOTOS request"
        def resultMessage = simulator.sendCommand("TAKE_PHOTOS", [
                numberOfPhotos: numberOfPhotos,
                quality: quality
        ])
        return JsonOutput.toJson([status: resultMessage.startsWith("ACK") ? "ACK" : "NACK", message: resultMessage])
    }

    // NOWA METODA: Symuluje wysłanie danych do SERW1
    void downlinkDataTo(IntermediateServer serw1) {
        def photos = simulator.downlinkQueuedPhotos()
        if (photos) {
            // W prawdziwym świecie to byłoby zapytanie HTTP POST
            serw1.receiveDataFromSatellite(JsonOutput.toJson(photos))
        }
    }
}