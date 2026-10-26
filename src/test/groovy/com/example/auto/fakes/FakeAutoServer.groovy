package com.example.auto.fakes


import com.example.auto.dto.CommandResponse
import groovy.json.JsonOutput

class FakeAutoServer {
    AutoCheck checker

    void handleConnect(String authToken) {
        checker.connect(authToken)
    }

    void handleDisconnect() {
        checker.disconnect()
    }

    // Metoda-endpoint, która obsługuje przychodzące zapytania
    String handleCommandRequest(String command, String authToken) {
        println "AUTO_SERVER: Received JSON request: ${jsonRequest}"

        println "AUTO_SERVER: Received command '${command}'"
        String responseMessage = checker.sendCommand(command)

        // 3. Stworzenie obiektu odpowiedzi
        def response = new CommandResponse(
                status: responseMessage.startsWith("ACK") ? "ACK" : "NACK",
                message: responseMessage,
                batteryLevel: checker.batteryLevel
        )

        // 4. Serializacja odpowiedzi do JSON
        def jsonResponse = JsonOutput.toJson(response)
        println "AUTO_SERVER: Sending JSON response: ${jsonResponse}"
        return jsonResponse
    }

    // Endpoint do zlecania zdjęć (już nie robi connect/disconnect)
    String handleTakePhotosRequest(int numberOfPhotos, String quality) {
        println "AUTO_SERVER: Received TAKE_PHOTOS request"
        def resultMessage = checker.sendCommand("TAKE_PHOTOS", [
                numberOfPhotos: numberOfPhotos,
                quality: quality
        ])
        return JsonOutput.toJson([status: resultMessage.startsWith("ACK") ? "ACK" : "NACK", message: resultMessage])
    }

    // NOWA METODA: Symuluje wysłanie danych do SERW1
    void downlinkDataTo(IntermediateServer serw1) {
        def photos = checker.downlinkQueuedPhotos()
        if (photos) {
            // W prawdziwym świecie to byłoby zapytanie HTTP POST
            serw1.receiveDataFromAuto(JsonOutput.toJson(photos))
        }
    }
}