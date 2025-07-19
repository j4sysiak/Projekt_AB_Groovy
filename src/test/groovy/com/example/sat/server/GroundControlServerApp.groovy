package com.example.sat.server

import com.example.sat.fakes.FakeRelationalDB
import com.example.sat.utils.http.SimpleHttpClient
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spark.Service

import com.example.sat.IMissionPlanner
import com.example.sat.MissionPlanningException
import com.example.sat.dto.MissionRequest

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

    // NOWA ZALEŻNOŚĆ: Planista Misji
    private IMissionPlanner missionPlanner

    // NOWA METODA: Do wstrzykiwania planisty w testach
    void setMissionPlanner(IMissionPlanner planner) {
        this.missionPlanner = planner
    }

               // Usuwamy 'satelliteServerUrl' bo nie jest już tu potrzebny
    void start(int port) {
        println "SERVER (GRUND): Starting Ground Control Server on port ${port}..."
        this.satClient = new SimpleHttpClient()

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

        // Zmieniamy endpoint /mission/photos na /missions
        http.post("/missions") { req, res ->
            res.type("application/json")
            try {
                def request = new MissionRequest(new JsonSlurper().parseText(req.body()))

                // ZMIANA: Wołamy planistę, a nie bezpośrednio klienta SAT
                def missionPlan = missionPlanner.createPlan(request)

                // W prawdziwym systemie tutaj zapisalibyśmy plan do bazy i zakolejkowali do wysłania
                res.status(202) // Accepted
                return JsonOutput.toJson(missionPlan)
            } catch (MissionPlanningException e) {
                res.status(422) // Unprocessable Entity - semantycznie poprawny błąd
                return JsonOutput.toJson([error: "Mission Rejected", reason: e.message])
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