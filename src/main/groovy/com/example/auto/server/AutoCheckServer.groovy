// ścieżka: src/main/groovy/com/example/auto/server/AutoCheckServer.groovy
package com.example.auto.server

import com.example.auto.dto.PhotoCollectionAction
import com.example.auto.dto.PhotoTask
import com.example.auto.http.SimpleHttpClient
import com.example.auto.simulation.IAutoCheck
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Serwer API, który wystawia funkcjonalności przez HTTP.
 * Działa jako fasada, tłumacząc żądania HTTP na wywołania metod.
 *
 * Zależności (symulator, serwer HTTP) są wstrzykiwane przez konstruktor.
 */
class AutoCheckServer {

    private final IAutoCheck checker
    private final IHttpServer http
    private String downlinkTargetUrl // URL serwera, na który będą wysyłane dane

    // === GŁÓWNY KONSTRUKTOR (Dependency Injection) ===
    AutoCheckServer(IAutoCheck checker, IHttpServer httpServer) {
        this.checker = checker
        this.http = httpServer
    }

    void start(int port) {
        println "SERVER (AUTO): Starting Server on port ${port}..."
        // Konfigurujemy i uruchamiamy serwer HTTP, który został wstrzyknięty
        this.http.port(port)

        defineEndpoints()

        http.awaitInitialization()

        // ZMIANA: Użyjmy portu, który już znamy, zamiast pytać serwer
        println "SERVER (PLATZ): Started on port ${port}."
    }

    // Prywatna metoda grupująca wszystkie definicje endpointów dla porządku
    private void defineEndpoints() {
        http.post("/connect") { req, res ->
            res.type("application/json")
            try {
                def authToken = req.headers("Authorization")?.replace("Bearer ", "")
                checker.connect(authToken)
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
                def responseMessage = checker.sendCommand(request.command, request.params ?: [:])
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
            checker.disconnect()
            res.status(200)
            return JsonOutput.toJson([status: "OK", message: "Disconnected"])
        }

        http.post("/downlink") { req, res ->
            res.type("application/json")
            if (!downlinkTargetUrl) {
                res.status(500)
                return JsonOutput.toJson([status: "ERROR", message: "Downlink target URL is not configured."])
            }

            def photos = checker.downlinkQueuedPhotos()
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

        http.post("/action/photo-collection") { req, res ->
            res.type("application/json")
            try {
                def payload = new JsonSlurper().parseText(req.body())
                // Tworzymy DTO na podstawie danych z sieci
                def manifest = new PhotoCollectionAction(
                        actionId: payload.actionId,
                        status: payload.status,
                        tasks: payload.tasks.collect { new PhotoTask(it) }
                )
                checker.storeActionPlan(manifest)
                res.status(202) // Accepted
                return JsonOutput.toJson([status: "MANIFEST_ACCEPTED", actionId: manifest.actionId])
            } catch (Exception e) {
                res.status(500)
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }
    }

    // === Metody Pomocnicze i Zarządzające ===

    void stop() {
        http?.stop()
        println "SERVER (AUTO): Server stopped."
    }

    int getPort() { return http.getPort() }

    void updateDownlinkTarget(String url) {
        this.downlinkTargetUrl = url
        println "SERVER (AUTO): Downlink target updated to ${url}"
    }
}