package com.example.sat.server

import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.fakes.SatelliteSimulator
import com.example.sat.http.SimpleHttpClient // <-- POPRAWNY IMPORT
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spark.Service

//Serwer SAT
class SatelliteSimulatorServer {
    private final SatelliteSimulator simulator
    private String downlinkTargetUrl
    private Service http

    SatelliteSimulatorServer(SatelliteSimulator simulator, String downlinkTargetUrl) {
        this.simulator = simulator
        this.downlinkTargetUrl = downlinkTargetUrl
    }

    void start(int port) {
        println "SERVER (SAT): Starting Satellite Simulator Server on port ${port}..."
        http = Service.ignite() // Tworzymy nową, niezależną instancję serwera
        http.port(port)

        // Definiujemy endpointy na naszej lokalnej instancji 'http'

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

        http.post("/disconnect") { req, res ->
            res.type("application/json")
            simulator.disconnect()
            res.status(200)
            return JsonOutput.toJson([status: "OK", message: "Disconnected"])
        }

        // Endpoint do zlecania wysyłki danych do SERW1
        http.post("/downlink") { req, res ->
            res.type("application/json")
            println "SERVER (SAT): Performing health check on intermediate server..."

            def photos = simulator.downlinkQueuedPhotos()
            if (photos) {
                println "SERVER (SAT): Downlinking ${photos.size()} photos to ${downlinkTargetUrl}..."
                // === UŻYWAMY POPRAWNEGO KLIENTA ===
                def intermediateServerClient = new SimpleHttpClient(downlinkTargetUrl)
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

        // JEDEN, SPÓJNY ENDPOINT DO ODBIERANIA MANIFESTÓW
        http.post("/mission/photo-collection") { req, res ->
            res.type("application/json")
            try {
                def manifest = new PhotoCollectionManifest(new JsonSlurper().parseText(req.body()))
                simulator.storeMissionPlan(manifest)
                res.status(202) // Accepted
                return JsonOutput.toJson([status: "MANIFEST_ACCEPTED", missionId: manifest.missionId])
            } catch (Exception e) {
                res.status(500)
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
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

    // Wewnątrz klasy SatelliteSimulatorServer
    void updateDownlinkTarget(String url) {
        this.downlinkTargetUrl = url
    }
}
