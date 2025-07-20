package com.example.sat.server

import com.example.sat.http.SimpleHttpClient
import com.example.sat.fakes.SatelliteSimulator
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spark.Service
import com.example.sat.dto.MissionPlan

class SatelliteSimulatorServer {
    private final SatelliteSimulator simulator
    private final String downlinkTargetUrl
    private Service http // Własna, prywatna instancja serwera Spark

    SatelliteSimulatorServer(SatelliteSimulator simulator, String downlinkTargetUrl) {
        this.simulator = simulator
        this.downlinkTargetUrl = downlinkTargetUrl
    }

    void start(int port) {
        println "SERVER (SAT): Starting Satellite Simulator Server on port ${port}..."
        http = Service.ignite() // Tworzymy nową, niezależną instancję serwera
        http.port(port)

        // Definiujemy endpointy na naszej lokalnej instancji 'http'

        // === ENDPOINT: Do przyjmowania planów misji ===
        http.post("/mission-plan") { req, res ->
            res.type("application/json")
            try {
                // Deserializujemy cały plan z ciała zapytania
                def plan = new MissionPlan(new JsonSlurper().parseText(req.body()))
                simulator.storeMissionPlan(plan)
                res.status(201) // 201 Created
                return JsonOutput.toJson([status: "STORED", missionId: plan.missionId])
            } catch (Exception e) {
                res.status(500)
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }
        // =================================================

        http.post("/connect") { req, res ->
            res.type("application/json")
            def authToken = req.headers("Authorization")?.replace("Bearer ", "")
            try {
                simulator.connect(authToken)
                res.status(200)
                return JsonOutput.toJson([status: "OK", message: "Connected"])
            } catch (Exception e) {
                res.status(400)
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }

        http.post("/command") { req, res ->
            res.type("application/json")
            try {
                def request = new JsonSlurper().parseText(req.body())
                def responseMessage = simulator.sendCommand(request.command, request.params ?: [:])

                // === KLUCZOWA ZMIANA: Bardziej inteligentne określanie statusu ===
                // Uznajemy za sukces, jeśli odpowiedź zaczyna się od "ACK" LUB "STATUS"
                boolean isSuccess = responseMessage.startsWith("ACK") || responseMessage.startsWith("STATUS")

                def response = [
                        status: isSuccess ? "ACK" : "NACK", // Zwracamy "ACK" dla obu typów sukcesu
                        message: responseMessage,
                        batteryLevel: simulator.batteryLevel
                ]
                return JsonOutput.toJson(response)
            } catch (Exception e) {
                res.status(500)
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }

        // Endpoint do zlecania wysyłki danych do SERW1
        http.post("/downlink") { req, res ->
            res.type("application/json")

            // LOGIKA: Health Check ===
            println "SERVER (SAT): Performing health check on intermediate server..."
            def intermediateServerClient = new SimpleHttpClient(downlinkTargetUrl)
            def healthResponse = intermediateServerClient.getData("/health") // getData to GET

            if (healthResponse.statusCode != 200 || healthResponse.json.status != "UP") {
                println "SERVER (SAT): Health check FAILED for ${downlinkTargetUrl}. Aborting downlink."
                res.status(502) // Bad Gateway
                return JsonOutput.toJson([
                        status: "ERROR",
                        message: "Intermediate server is unhealthy. Downlink aborted.",
                        healthCheckResponse: healthResponse.json
                ])
            }
            println "SERVER (SAT): Health check PASSED."

            def photos = simulator.downlinkQueuedPhotos()
            if (photos) {
                println "SERVER (SAT): Downlinking ${photos.size()} photos to ${downlinkTargetUrl}..."
                def response = intermediateServerClient.sendGenericPost("/ingest", photos)

                if (response.statusCode == 202) {
                    return JsonOutput.toJson([status: "OK", message: "Downlink successful."])
                } else {
                    res.status(502)
                    return JsonOutput.toJson([status: "ERROR", message: "Failed to downlink data to intermediate server."])
                }
            }

            return JsonOutput.toJson([status: "OK", message: "No data to downlink."])
        }

        http.post("/disconnect") { req, res ->
            res.type("application/json")
            simulator.disconnect()
            res.status(200)
            return JsonOutput.toJson([status: "OK", message: "Disconnected"])
        }

        http.awaitInitialization()
        println "SERVER (SAT): Satellite Simulator Server started on port ${http.port()}."
    }

    void stop() {
        if (http != null) {
            println "SERVER (SAT): Stopping Satellite Simulator Server..."
            http.stop()
            http.awaitStop()
            println "SERVER (SAT): Server stopped."
        }
    }

    // Publiczna metoda do pobierania portu, na którym serwer faktycznie wystartował
    int getPort() { return http.port() }
}
