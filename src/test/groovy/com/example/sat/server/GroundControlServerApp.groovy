package com.example.sat.server

import com.example.sat.fakes.FakeRelationalDB
import com.example.sat.utils.http.SimpleHttpClient
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spark.Service

// Ta klasa uruchamia i zarządza serwerem kontroli naziemnej (GRUND)
class GroundControlServerApp {
    private Service http
    // Klient do komunikacji z serwerem satelity
    private SimpleHttpClient satClient
    // Wewnętrzny stan: przechowuje statusy zleconych zadań
    private Map<String, Map> jobStatuses = [:]
    private long jobIdCounter = 1
    // NOWY KOMPONENT: Własna "baza danych" dla systemu naziemnego
    private final FakeRelationalDB database = new FakeRelationalDB()

    // NOWE POLE: Flaga do symulowania awarii w testach
    private boolean forceUnhealthy = false

    void start(int port, String satelliteServerUrl) {
        println "SERVER (GRUND): Starting Ground Control Server on port ${port}..."
        this.satClient = new SimpleHttpClient(satelliteServerUrl)

        http = Service.ignite()
        http.port(port)

        // Definiujemy API naszego serwera GRUND

        // === NOWY ENDPOINT: Health Check ===
        http.get("/health") { req, res ->
            res.type("application/json")
            if (forceUnhealthy) {
                res.status(503) // Service Unavailable
                return JsonOutput.toJson([status: "DEGRADED", reason: "Database connection pool exhausted"])
            }
            res.status(200)
            return JsonOutput.toJson([status: "UP"])
        }
        // ===================================

        // === NOWY ENDPOINT: Do odbierania danych z SERW1 ===
        // ZAKTUALIZOWANY ENDPOINT: Teraz faktycznie zapisuje dane do bazy
        http.post("/data/ingest") { req, res ->
            res.type("application/json")
            println "GRUND: Received final data package on /data/ingest endpoint."

            def photos = new JsonSlurper().parseText(req.body()) as List<Map>
            photos.each { photo ->
                database.insertPhoto(photo)
            }

            res.status(202) // Accepted
            return JsonOutput.toJson([status: "ACCEPTED", receivedPhotos: photos.size()])
        }
        // ==================================================

        // POST /mission/photos - endpoint do zlecania nowej misji fotograficznej
        http.post("/mission/photos") { req, res ->
            res.type("application/json")
            def missionParams = new JsonSlurper().parseText(req.body())
            def authToken = req.headers("Authorization")?.replace("Bearer ", "")

            println "GRUND: Received mission request: ${missionParams}"

            // 1. Nawiąż połączenie z satelitą
            def connectResponse = satClient.connect(authToken)
            if (connectResponse.statusCode != 200) {
                res.status(502) // Bad Gateway
                return JsonOutput.toJson([status: "ERROR", message: "Could not connect to satellite."])
            }

            // 2. Wyślij komendę zlecającą zrobienie zdjęć
            def commandResponse = satClient.sendCommand("TAKE_PHOTOS", [
                    numberOfPhotos: missionParams.photoCount,
                    quality: missionParams.quality
            ])

            // 3. Rozłącz się z satelitą
            satClient.disconnect()

            // 4. Zapisz status zadania i odpowiedz klientowi
            def jobId = "MISSION-${jobIdCounter++}"
            if (commandResponse.json.status == "ACK") {
                jobStatuses[jobId] = [status: "ACCEPTED", details: commandResponse.json.message]
                res.status(202) // Accepted
                return JsonOutput.toJson([jobId: jobId, status: "ACCEPTED"])
            } else {
                jobStatuses[jobId] = [status: "REJECTED", details: commandResponse.json.message]
                res.status(400) // Bad Request
                // === KLUCZOWA ZMIANA: Dodajemy brakujące pole 'details' do odpowiedzi ===
                return JsonOutput.toJson([
                        jobId: jobId,
                        status: "REJECTED",
                        details: commandResponse.json.message
                ])
            }
        }

        // NOWY ENDPOINT: Do pobierania finalnych danych przez użytkownika
        http.get("/data/photos") { req, res ->
            res.type("application/json")
            return JsonOutput.toJson(database.getArchivedPhotos())
        }

        http.awaitInitialization()
        println "SERVER (GRUND): Ground Control Server started on port ${http.port()}."
    }

    // Publiczna metoda do aktualizacji klienta po starcie serwerów
    void updateSatClient(SimpleHttpClient client) {
        this.satClient = client
    }

    void stop() {
        if (http != null) {
            http.stop(); http.awaitStop()
            println "SERVER (GRUND): Server stopped."
        }
    }


    Map getJobStatus(String jobId) { return jobStatuses[jobId] }

    // NOWA METODA: Do sterowania stanem "zdrowia" w testach
    void forceHealthStatus(boolean isUnhealthy) {
        this.forceUnhealthy = isUnhealthy
    }

    int getPort() { return http.port() }

    // Metody do weryfikacji stanu w testach
    void resetState() {
        jobStatuses.clear()
        jobIdCounter = 1
        database.photoTable.clear()
        println "SERVER (GRUND): State has been reset."
    }

    int getDbPhotoCount() { return database.photoTable.size() }
}