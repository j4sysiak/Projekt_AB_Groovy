package com.example.sat.fakes

import com.example.sat.dto.CommandRequest
import com.example.sat.dto.CommandResponse
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// Symuluje serwer na pokładzie satelity, który odbiera komendy   <-----------    od Ground Control Server
class FakeSatelliteServer {
    SatelliteSimulator simulator // Używa naszego istniejącego symulatora do wykonania pracy

    // Metoda-endpoint, która obsługuje przychodzące zapytania
    String handleCommandRequest(String jsonRequest) {
        println "SAT_SERVER: Received JSON request: ${jsonRequest}"

        // 1. Deserializacja zapytania JSON
        def requestMap = new JsonSlurper().parseText(jsonRequest)
        def request = new CommandRequest(requestMap)

        // 2. Wykonanie logiki - łączymy się, wysyłamy komendę, rozłączamy się
        // To jest uproszczony przepływ, który symuluje, co by się działo na satelicie
        String responseMessage
        try {
            simulator.connect(request.authToken)
            responseMessage = simulator.sendCommand(request.command)
        } catch (Exception e) {
            responseMessage = "NACK: ${e.message}"
        } finally {
            if (simulator.isConnected()) {
                simulator.disconnect()
            }
        }

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
}