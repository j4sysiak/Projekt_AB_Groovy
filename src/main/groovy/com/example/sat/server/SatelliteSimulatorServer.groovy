// ścieżka: src/main/groovy/com/example/sat/server/SatelliteSimulatorServer.groovy
package com.example.sat.server

import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoTask
import com.example.sat.http.SimpleHttpClient
import com.example.sat.simulation.ISatelliteSimulator
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Serwer API, który wystawia funkcjonalności symulatora satelity przez HTTP.
 * Działa jako fasada, tłumacząc żądania HTTP na wywołania metod w symulatorze.
 *
 * Zależności (symulator, serwer HTTP) są wstrzykiwane przez konstruktor.
 */
class SatelliteSimulatorServer {

    private final ISatelliteSimulator simulator
    private final IHttpServer http
    private String downlinkTargetUrl // URL serwera, na który będą wysyłane dane

    // === GŁÓWNY KONSTRUKTOR (Dependency Injection) ===
    SatelliteSimulatorServer(ISatelliteSimulator simulator, IHttpServer httpServer) {
        this.simulator = simulator
        this.http = httpServer
    }

    void start(int port) {
        println "SERVER (SAT): Starting Satellite Simulator Server on port ${port}..."
        // Konfigurujemy i uruchamiamy serwer HTTP, który został wstrzyknięty
        this.http.port(port)

        defineEndpoints()

        http.awaitInitialization()

        // ZMIANA: Użyjmy portu, który już znamy, zamiast pytać serwer
        println "SERVER (GRUND): Started on port ${port}."
    }

    // Prywatna metoda grupująca wszystkie definicje endpointów dla porządku
    private void defineEndpoints() {
        http.post("/connect") { req, res ->
            res.type("application/json")
            try {
                def authToken = req.headers("Authorization")?.replace("Bearer ", "")
                simulator.connect(authToken)
                res.status(200)
                return JsonOutput.toJson([status: "OK", message: "Connected"])
            } catch (Exception e) {
                res.status(401) // Unauthorized/Bad Request
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }

        http.post("/command") { req, res ->
            res.type("application/json")
            try {
                def request = new JsonSlurper().parseText(req.body())
                def responseMessage = simulator.sendCommand(request.command, request.params ?: [:])
                boolean isSuccess = responseMessage.startsWith("ACK") || responseMessage.startsWith("STATUS")
                res.status(200)
                return JsonOutput.toJson([status: isSuccess ? "ACK" : "NACK", message: responseMessage])
            } catch (Exception e) {
                res.status(500) // Internal Server Error for unexpected failures
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }

        http.post("/disconnect") { req, res ->
            res.type("application/json")
            simulator.disconnect()
            res.status(200)
            return JsonOutput.toJson([status: "OK", message: "Disconnected"])
        }

        http.post("/downlink") { req, res ->
            res.type("application/json")
            if (!downlinkTargetUrl) {
                res.status(500)
                return JsonOutput.toJson([status: "ERROR", message: "Downlink target URL is not configured."])
            }

            def photos = simulator.downlinkQueuedPhotos()
            if (photos) {
                def client = new SimpleHttpClient(downlinkTargetUrl)
                def response = client.sendGenericPost("/ingest", photos)
                if (response.statusCode == 202) {
                    res.status(200)
                    return JsonOutput.toJson([status: "OK", message: "Downlink successful."])
                } else {
                    res.status(502) // Bad Gateway
                    return JsonOutput.toJson([status: "ERROR", message: "Downlink to intermediate server failed."])
                }
            }
            res.status(200)
            return JsonOutput.toJson([status: "OK", message: "No data to downlink."])
        }

        http.post("/mission/photo-collection") { req, res ->
            res.type("application/json")
            try {
                def payload = new JsonSlurper().parseText(req.body())
                // Tworzymy DTO na podstawie danych z sieci
                def manifest = new PhotoCollectionManifest(
                        missionId: payload.missionId,
                        status: payload.status,
                        tasks: payload.tasks.collect { new PhotoTask(it) }
                )
                // Przekazujemy gotowy obiekt do symulatora
                simulator.storeMissionPlan(manifest)
                res.status(202) // Accepted
                return JsonOutput.toJson([status: "MANIFEST_ACCEPTED", missionId: manifest.missionId])
            } catch (Exception e) {
                res.status(500)
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }
    }

    // === Metody Pomocnicze i Zarządzające ===

    void stop() {
        http?.stop()
        println "SERVER (SAT): Server stopped."
    }

    int getPort() { return http.getPort() }

    void updateDownlinkTarget(String url) {
        this.downlinkTargetUrl = url
        println "SERVER (SAT): Downlink target updated to ${url}"
    }
}