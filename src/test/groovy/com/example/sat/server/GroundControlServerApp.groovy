package com.example.sat.server

import com.example.sat.dto.PhotoData
import com.example.sat.IMissionPlanner
import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoTask
import com.example.sat.dto.SubscriptionRequest
import com.example.sat.fakes.FakeRelationalDB
import com.example.sat.http.INotificationClient
import com.example.sat.http.ISatelliteApiClient
import com.example.sat.http.SimpleHttpClient
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spark.Service
import com.example.sat.services.NotificationService // <-- NOWY IMPORT
import java.util.concurrent.Executors

class GroundControlServerApp {
    private Service http
    private ISatelliteApiClient satClient
    private IMissionPlanner missionPlanner
    private Map<String, PhotoCollectionManifest> missionManifests = [:]
    private final FakeRelationalDB database = new FakeRelationalDB()
    private boolean forceUnhealthy = false
    private NotificationService notificationService //POLE: INSTANCJA SERWISU POWIADOMIEŃ

    void start(int port, String satelliteServerUrl) {
        def notificationExecutor = Executors.newCachedThreadPool()
        this.satClient = new SimpleHttpClient(satelliteServerUrl)
        http = Service.ignite()
        http.port(port)

        def self = this
        def db = this.database

        // Tworzymy anonimową implementację INotificationClient w locie
        def notificationClient = [send: { url, payload ->
            // "Odpalamy" wysyłkę w osobnym wątku i natychmiast wracamy,
            // nie czekając na odpowiedź.
            notificationExecutor.submit {
                try {
                    println "NOTIFICATION_CLIENT: Sending async notification to ${url}..."
                    new SimpleHttpClient().sendGenericPost(url, payload)
                    println "NOTIFICATION_CLIENT: Async notification sent successfully."
                } catch (Exception e) {
                    println "NOTIFICATION_CLIENT_ERROR: Failed to send notification: ${e.message}"
                }
            }
        }] as INotificationClient

        // Przekazujemy implementacje do serwisu
        this.notificationService = new NotificationService(this.database, notificationClient)

        // ---------------  ENDPOINTY

        http.get("/health") { req, res ->
            res.type("application/json")
            if (self.forceUnhealthy) {
                res.status(503)
                return JsonOutput.toJson([status: "DEGRADED", reason: "Database connection pool exhausted"])
            }
            res.status(200)
            return JsonOutput.toJson([status: "UP"])
        }

        // === ENDPOINT GŁÓWNY (ROOT) Z LINKAMI HATEOAS ===
        http.get("/") { req, res ->
            res.type("application/json")

            // Budujemy bazowy URL na podstawie danych z przychodzącego żądania
            // Np. "http://localhost:51234"
            String baseUrl = req.scheme() + "://" + req.host()

            // Zwracamy "menu" dostępnych akcji w standardowym formacie HAL (Hypertext Application Language)
            return JsonOutput.toJson([
                    _links: [
                            // Link do samego siebie (standard w HATEOAS)
                            self: [ href: baseUrl + "/" ],

                            // Link do akcji tworzenia nowej misji
                            "create-mission": [
                                    href: baseUrl + "/missions/photo-collection",
                                    method: "POST"
                            ],

                            // Link do akcji tworzenia nowej subskrypcji
                            "create-subscription": [
                                    href: baseUrl + "/subscriptions",
                                    method: "POST"
                            ]
                    ]
            ])
        }

        // === DYNAMICZNY ENDPOINT STANU MISJI ===
        http.get("/missions/:missionId") { req, res ->
            res.type("application.json")
            println "GRUND: DYNAMICZNY ENDPOINT STANU MISJI"

            String missionId = req.params(":missionId")
            def manifest = missionManifests[missionId]

            if (!manifest) {
                res.status(404)
                return JsonOutput.toJson([error: "Mission with ID ${missionId} not found."])
            }

            String baseUrl = req.scheme() + "://" + req.host()
            def missionUrl = baseUrl + "/missions/${missionId}"

            // === POPRAWKA 2.0: Jawne budowanie mapy dla każdego zadania ===
            def response = [
                    missionId: manifest.missionId,
                    status: manifest.status,

                    //Już nie będziemy zwracać taska tutaj
                    /*
                    tasks: manifest.tasks.collect { task ->
                        [
                                taskId: task.taskId,
                                // Jawnie konwertujemy Enum na jego nazwę (String)
                                status: task.status.name()
                        ]
                    }, */

                    _links: [
                            self: [ href: missionUrl ],
                            tasks: [ href: missionUrl + "/tasks" ] // <-- NAJWAŻNIEJSZA ZMIANA
                    ]
            ]

            // === Logika HATEOAS dla AKCJI (pozostaje bez zmian) ===
            // Ta część nadal będzie dodawać link 'accept-mission', jeśli status to PLANNED.
            switch (manifest.status) {
                case "PLANNED":
                    // Jeśli misja jest zaplanowana, pozwalamy ją zaakceptować
                    response._links."accept-mission" = [ href: missionUrl + "/accept", method: "POST" ]
                    break
                case "ACCEPTED":
                    // Jeśli zaakceptowana, pozwalamy ją wysłać do satelity
                    response._links."upload-to-satellite" = [ href: missionUrl + "/upload", method: "POST" ]
                    break
                case "COMPLETED":
                    // Jeśli zakończona, pozwalamy utworzyć subskrypcję
                    // (To tylko przykład, można tu dodać inne akcje)
                    response._links."create-subscription" = [ href: baseUrl + "/subscriptions", method: "POST" ]
                    break
            }

            res.status(200)
            return JsonOutput.toJson(response)
        }

        // nowy, paginowany endpoint
        http.get("/missions/:missionId/tasks") { req, res ->
            res.type("application/json")
            String missionId = req.params(":missionId")
            def manifest = missionManifests[missionId]

            if (!manifest) {
                res.status(404)
                return JsonOutput.toJson([error: "Mission with ID ${missionId} not found."])
            }

            // === Logika Paginacji ===
            // Pobieramy parametry z URL-a, np. /tasks?page=2&size=10
            int page = req.queryParams("page") ? req.queryParams("page") as int : 1
            int size = req.queryParams("size") ? req.queryParams("size") as int : 10 // Domyślnie 10 na stronę
            int totalTasks = manifest.tasks.size()
            int totalPages = Math.ceil(totalTasks / (double) size) as int
            int offset = (page - 1) * size

            // Wycinamy "plasterek" zadań dla bieżącej strony
            def tasksOnPage = manifest.tasks.drop(offset).take(size)

            // === Logika HATEOAS dla Paginacji ===
            String baseUrl = req.scheme() + "://" + req.host()
            String tasksUrl = baseUrl + "/missions/${missionId}/tasks"
            def links = [
                    self: [ href: tasksUrl + "?page=${page}&size=${size}" ]
            ]
            if (page > 1) {
                links.prev = [ href: tasksUrl + "?page=${page - 1}&size=${size}" ]
            }
            if (page < totalPages) {
                links.next = [ href: tasksUrl + "?page=${page + 1}&size=${size}" ]
            }
            links.first = [ href: tasksUrl + "?page=1&size=${size}" ]
            links.last = [ href: tasksUrl + "?page=${totalPages}&size=${size}" ]

            // Budujemy finalną odpowiedź
            def response = [
                    _links: links,
                    page: [
                            size: tasksOnPage.size(),
                            totalElements: totalTasks,
                            totalPages: totalPages,
                            number: page
                    ],
                    // Osadzamy listę zadań dla tej strony w odpowiedzi
                    _embedded: [
                            tasks: tasksOnPage.collect { [taskId: it.taskId, status: it.status.name()] }
                    ]
            ]

            return JsonOutput.toJson(response)
        }


        // === ENDPOINT AKCJI: Akceptacja misji ===
        http.post("/missions/:missionId/accept") { req, res ->
            res.type("application/json")
            println "GRUND: ENDPOINT AKCJI: Akceptacja misji"

            String missionId = req.params(":missionId")
            def manifest = missionManifests[missionId]

            if (!manifest || manifest.status != "PLANNED") {
                res.status(409) // Conflict
                return JsonOutput.toJson([error: "Mission cannot be accepted at this time."])
            }

            manifest.status = "ACCEPTED"
            res.status(200)
            return JsonOutput.toJson([status: "OK", message: "Mission ${missionId} has been accepted."])
        }

        http.post("/data/ingest") { req, res ->
            res.type("application/json")
            try {
                println "GRUND: Received raw: ${req.body()}"

                /* zapis not-like-lambda*/
                def rawList = new JsonSlurper().parseText(req.body()) as List<Map>
                rawList.each { println "INCOMING MAP: $it" }
                def photos = rawList.collect {
                    new PhotoData(
                            photoId: it.photoId,
                            timestamp: it.timestamp ?: System.currentTimeMillis(),
                            quality: it.quality,
                            content: it.content,
                            missionId: it.missionId
                    )
                }

                /*  zapis lambda-like
                def photos = (new JsonSlurper().parseText(req.body()) as List<Map>)
                        .collect {
                    new PhotoData(it)
                }*/

                if (photos.isEmpty()) {
                    res.status(400)
                    return JsonOutput.toJson([status: "ERROR", message: "Received empty photo list."])
                }

                // Zapisujemy zdjęcia do bazy
                println "GRUND: Zapis zdjęć do bazy"
                println "GRUND: Ingested ${photos.size()} photos."
                println "GRUND: Parsed PhotoData objects: $photos"
                photos.each {
                    database.insertPhoto(it)
                }

                // === LOGIKA POWIADOMIEŃ ===
                // Zakładamy, że wszystkie zdjęcia w paczce są z tej samej misji
                def missionId = photos.first().missionId
                if (missionId && isMissionComplete(missionId)) {
                    println "GRUND: Mission ${missionId} is complete. Publishing event."
                    notificationService.publishEvent(
                            "MISSION_COMPLETED",
                            missionId,
                            [photoCount: getPhotoCountForMission(missionId), message: "All data processed."]
                    )
                }
                res.status(202) // Accepted
                return JsonOutput.toJson([status: "DATA_ACCEPTED", ingestedCount: photos.size()])

            } catch (Exception e) {
                println "GRUND: Error during data ingest: ${e.message}"
                res.status(500)
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }

            res.status(202) // Accepted

            return JsonOutput.toJson([status: "ACCEPTED", received: photos.size()])
        }

        http.get("/data/photos") { req, res ->
            res.type("application/json")
            res.status(200)
            return JsonOutput.toJson(db.getArchivedPhotos())
        }

        http.post("/missions/photo-collection") { req, res ->
            try {
                res.type("application/json")
                def missionData = new JsonSlurper().parseText(req.body())
                int photoCount = missionData.photoCount as int

                if (photoCount <= 0) {
                    res.status(400)
                    return JsonOutput.toJson([error: "photoCount must be a positive integer."])
                }

                def tasks = (1..photoCount).collect { new PhotoTask() }
                def manifest = new PhotoCollectionManifest(tasks: tasks)
                // Manifest jest tworzony z domyślnym statusem "PLANNED"

                self.missionManifests[manifest.missionId] = manifest
                println "GRUND: New mission ${manifest.missionId} created with status PLANNED."

                // === USUWAMY LOGIKĘ WYSYŁANIA ===
                // To będzie teraz robione przez osobny endpoint /upload
            /*
                // Oznacza to:
                // "W bieżącym obiekcie serwera naziemnego (self), użyj jego wewnętrznego klienta HTTP (satClient obiekt ISatelliteApiClient),
                // aby wywołać metodę uploadMissionPlan i przesłać do serwera satelity cały obiekt manifestu (manifest).
                // Wynik tej operacji (odpowiedź od serwera satelity) zapisz w zmiennej response."
                def response = self.satClient.uploadMissionPlan(manifest)
            */

                res.status(202) // Accepted
                // Zwracamy cały manifest, żeby klient miał ID
                return JsonOutput.toJson(manifest)
          /*
                if (response.statusCode == 202) {
                    manifest.status = "UPLOADED"
                    res.status(202)
                    return JsonOutput.toJson(manifest)
                } else {
                    manifest.status = "UPLOAD_FAILED"
                    res.status(502)
                    return JsonOutput.toJson([error: "Satellite rejected the manifest."])
                }

           */
            } catch (Exception e) {
                res.status(500)
                println "ERROR in /missions/photo-collection: ${e.message}"
                e.printStackTrace()
                return JsonOutput.toJson([error: "Unexpected error: ${e.message}"])
            }
        }

        // === ENDPOINT AKCJI: Wysyłka do satelity ===
        // Ten endpoint jest "celem" linku HATEOAS `upload-to-satellite`
        http.post("/missions/:missionId/upload") { req, res ->
            res.type("application/json")
            println "GRUND: ENDPOINT AKCJI: Wysyłka do satelity"

            String missionId = req.params(":missionId")
            def manifest = missionManifests[missionId]

            // Sprawdzamy, czy można wykonać tę akcję
            if (!manifest || manifest.status != "ACCEPTED") {
                res.status(409) // Conflict
                return JsonOutput.toJson([error: "Mission cannot be uploaded at this time."])
            }

            println "GRUND: Preparing to upload manifest for mission ${missionId} to satellite..."
            // === POPRAWKA: Zmień status PRZED wysłaniem ===
            manifest.status = "UPLOADED"

            def uploadResponse = satClient.uploadMissionPlan(manifest)

            // Na podstawie odpowiedzi od satelity, aktualizujemy stan misji
            if (uploadResponse.statusCode == 202) {
                res.status(200)
                return JsonOutput.toJson([status: "OK", message: "Mission ${missionId} uploaded successfully."])
            } else {
                // Jeśli wysyłka się nie udała, cofnijmy status do poprzedniego stanu
                manifest.status = "FAILED" // lub z powrotem "ACCEPTED", aby umożliwić ponowną próbę
                res.status(502)
                return JsonOutput.toJson([status: "ERROR", message: "Failed to upload mission to satellite."])
            }
        }

        // =======================================================
        // === NOWY ENDPOINT DO ZARZĄDZANIA SUBSKRYPCJAMI ===
        // =======================================================
        http.post("/subscriptions") { req, res ->
            res.type("application/json")
            try {
                def subRequest = new SubscriptionRequest(new JsonSlurper().parseText(req.body()))

                // Zapisujemy subskrypcję w bazie
                // Używamy metody z interfejsu ISubscriptionRepository
                database.save(subRequest) // Teraz przekazujemy obiekt, a nie mapę

                res.status(201) // 201 Created
                return JsonOutput.toJson([status: "SUBSCRIPTION_CREATED", details: subRequest])
            } catch (Exception e) {
                res.status(400) // Bad Request
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }

        http.awaitInitialization()
        println "SERVER (GRUND): Started on port ${http.port()}."
    }

    void updateSatClientUrl(String satelliteSimulatorUrl) {
        if (satelliteSimulatorUrl) {
            this.satClient = new SimpleHttpClient(satelliteSimulatorUrl)
            println "GRUND: Satellite client URL updated to ${satelliteSimulatorUrl}"
        }
    }

    void stop() { if (http != null) { http.stop(); http.awaitStop(); println "SERVER (GRUND): Server stopped." } }
    int getPort() { return http.port() }
    void updateSatClient(ISatelliteApiClient client) { this.satClient = client }
    void setMissionPlanner(IMissionPlanner planner) { this.missionPlanner = planner }
    void forceHealthStatus(boolean isUnhealthy) { this.forceUnhealthy = isUnhealthy }

    void resetState() {
        this.database.photoTable.clear()
        println "SERVER (GRUND): State has been reset."
    }

    int getDbPhotoCount() { return database.photoTable.size() }

    /**
    * Sprawdza, czy misja jest zakończona.
    * Porównuje oczekiwaną liczbę zadań z manifestu z liczbą zdjęć
    * dla tej misji, które faktycznie dotarły do bazy danych.
    * @param missionId ID misji do sprawdzenia.
    * @return true, jeśli misja jest kompletna.
    */
    private boolean isMissionComplete(String missionId) {
        def manifest = missionManifests[missionId]
        if (!manifest) {
            println "WARN: Checking completeness for an unknown mission: ${missionId}"
            return false // Nie znamy takiej misji
        }

        def expectedCount = manifest.tasks.size()
        def actualCount = getPhotoCountForMission(missionId)

        println "MISSION_CHECK (${missionId}): Expected=${expectedCount}, Actual=${actualCount}"

        return actualCount >= expectedCount
    }

    /**
    *  Zlicza, ile zdjęć dla danej misji znajduje się obecnie w bazie danych.
     * @param missionId ID misji do zliczenia.
     * @return Liczba zdjęć.
     */
    private int getPhotoCountForMission(String missionId) {
        if (!missionId) return 0

        // Filtrujemy listę zdjęć w bazie, szukając tych z pasującym missionId
        return database.photoTable.findAll { it.missionId == missionId }.size()
    }
}