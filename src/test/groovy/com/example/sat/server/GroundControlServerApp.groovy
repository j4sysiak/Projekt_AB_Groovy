package com.example.sat.server

import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoTask
import com.example.sat.fakes.FakeRelationalDB
import com.example.sat.http.ISatelliteApiClient
import com.example.sat.http.SimpleHttpClient
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spark.Service
import com.example.sat.IMissionPlanner
import com.example.sat.exceptions.MissionPlanningException
import com.example.sat.dto.MissionRequest

// Ta klasa uruchamia i zarządza serwerem kontroli naziemnej (GRUND)
class GroundControlServerApp {
    private Service http

    // Klient do komunikacji z serwerem satelity poprzez ISatelliteApiClient: SOLID dependency inversion
    // Zmieniamy typ na interfejs, zgodnie z Twoją dobrą praktyką
    private ISatelliteApiClient satClient

    // STAN: Pamięć na zaplanowane misje
    // Zamiast prostych planów, przechowujemy manifesty
    private Map<String, PhotoCollectionManifest> missionManifests = [:]

    // ZALEŻNOŚĆ: Planista Misji
    private IMissionPlanner missionPlanner

    // KOMPONENT: Własna "baza danych" dla systemu naziemnego
    private final FakeRelationalDB database = new FakeRelationalDB()

    // POLE: Flaga do symulowania awarii w testach
    private boolean forceUnhealthy = false


    // Wewnętrzny stan: przechowuje statusy zleconych zadań
    //private Map<String, Map> jobStatuses = [:]

    //private long jobIdCounter = 1


    void start(int port, String satelliteServerUrl) {
        println "SERVER (GRUND): Starting Ground Control Server on port ${port}..."
        this.satClient = new SimpleHttpClient(satelliteServerUrl)

        http = Service.ignite()
        http.port(port)

        // Definiujemy API naszego serwera GRUND serwera

        // === ENDPOINT: Health Check ===
        http.get("/health") { req, res ->
            res.type("application/json")
            if (forceUnhealthy) {
                res.status(503) // Service Unavailable
                return JsonOutput.toJson([status: "DEGRADED", reason: "Database connection pool exhausted"])
            }
            res.status(200)
            return JsonOutput.toJson([status: "UP"])
        }

        // Endpoint /data/ingest będzie odbierał paczki z SERW1
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

        // ENDPOINT: Do pobierania finalnych danych przez użytkownika
        http.get("/data/photos") { req, res ->
            res.type("application/json")
            return JsonOutput.toJson(database.getArchivedPhotos())
        }


        // NOWY ENDPOINT: Do tworzenia i wysyłania manifestu
        http.post("/missions/photo-collection") { req, res ->
            res.type("application/json")
            def params = new JsonSlurper().parseText(req.body())
            int photoCount = params.photoCount

            def tasks = (1..photoCount).collect { new PhotoTask() }
            def manifestToReturn = new PhotoCollectionManifest(tasks: tasks)
            missionManifests[manifestToReturn.missionId] = manifestToReturn

            // KLUCZOWA ZMIANA: Wysyłamy ten sam obiekt, który potem zwracamy
            def responseFromSat = satClient.sendGenericPost("/mission/photo-collection", manifestToReturn)

            if (responseFromSat.statusCode == 202) {
                res.status(202)
                return JsonOutput.toJson(manifestToReturn)
            } else {
                res.status(502)
                return JsonOutput.toJson([error: "Satellite rejected the manifest."])
            }
        }
        // =====================================



        // ZAKTUALIZOWANY ENDPOINT /missions: teraz zapisuje plan
        http.post("/missions") { req, res ->
            res.type("application/json")
            try {
                def request = new MissionRequest(new JsonSlurper().parseText(req.body()))

                // Wołamy planistę, a nie bezpośrednio klienta SAT
                def missionPlan = missionPlanner.createPlan(request)

                // W prawdziwym systemie tutaj zapisalibyśmy plan do bazy i zakolejkowali do wysłania
                // ZMIANA: Zapisujemy plan w pamięci serwera
                plannedMissions[missionPlan.missionId] = missionPlan

                res.status(202) // Accepted
                return JsonOutput.toJson(missionPlan)
            } catch (MissionPlanningException e) {
                res.status(422) // Unprocessable Entity - semantycznie poprawny błąd
                return JsonOutput.toJson([error: "Mission Rejected", reason: e.message])
            }
        }

        // NOWY ENDPOINT: Do zlecania wysyłki planu (upload)
        http.post("/missions/:missionId/upload") { req, res ->
            def missionId = req.params(":missionId")
            def plan = plannedMissions[missionId]

            if (plan == null) {
                res.status(404) // Not Found
                return JsonOutput.toJson([error: "Mission plan not found."])
            }

            // === LOGIKA Z UPLOADERA JEST TERAZ TUTAJ ===
            println "GRUND: Uploading mission plan ${plan.missionId} to satellite..."
            // Wołamy metodę z interfejsu ISatelliteApiClient
            def response = satClient.uploadMissionPlan(plan)
            if (response.statusCode != 201) {
                throw new RuntimeException("Failed to upload mission plan. Satellite responded with ${response.statusCode}.")
            }
            println "GRUND: Mission plan uploaded successfully."
            // ===========================================

            // Aktualizujemy status planu
            plan.status = "UPLOADED"

            res.status(200)
            return JsonOutput.toJson(plan)
        }

        http.awaitInitialization()
        println "SERVER (GRUND): Ground Control Server started on port ${http.port()}."
    }


    void stop() {
        if (http != null) {
            http.stop();
            http.awaitStop();
            println "SERVER (GRUND): Server stopped."
        }
    }

    int getPort() {
        return http.port()
    }

    // Publiczna metoda do aktualizacji klienta po starcie serwerów
    // Zmieniamy typ w metodzie update, żeby pasował
    void updateSatClient(SimpleHttpClient client) {
        this.satClient = client
    }

    // METODA: Do wstrzykiwania planisty w testach
    void setMissionPlanner(IMissionPlanner planner) {
        this.missionPlanner = planner
    }

    void resetState() {
        missionManifests.clear();
        database.photoTable.clear();
        println "SERVER (GRUND): State has been reset."
    }

    int getDbPhotoCount() { return database.photoTable.size() }

    // METODA: Do sterowania stanem "zdrowia" w testach
    void forceHealthStatus(boolean isUnhealthy) {
        this.forceUnhealthy = isUnhealthy
    }

}