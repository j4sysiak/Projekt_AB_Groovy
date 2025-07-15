package com.example.sat.fakes

import com.example.sat.dto.CommandRequest
import com.example.sat.dto.CommandResponse
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// Symuluje system naziemny, który wysyła komendy  ------>   to Satellite
class FakeGroundControlServer {
    FakeSatelliteServer satelliteServer // "Połączenie sieciowe" do serwera satelity

    // Metoda-klient, która wysyła komendę przez "sieć"
    CommandResponse sendCommand(String command, String authToken) {
        println "GRUND_SERVER: Creating command request for '${command}'"
        def request = new CommandRequest(command: command, authToken: authToken)

        // 1. Serializacja do JSON (tak jak robiłby to klient HTTP)
        def jsonRequest = JsonOutput.toJson(request)
        println "GRUND_SERVER: Sending JSON request: ${jsonRequest}"

        // 2. "Wysłanie" przez sieć do serwera satelity
        def jsonResponse = satelliteServer.handleCommandRequest(jsonRequest)
        println "GRUND_SERVER: Received JSON response: ${jsonResponse}"

        // 3. Deserializacja odpowiedzi JSON (tak jak robiłby to klient HTTP)
        def responseMap = new JsonSlurper().parseText(jsonResponse)
        return new CommandResponse(responseMap)
    }
}