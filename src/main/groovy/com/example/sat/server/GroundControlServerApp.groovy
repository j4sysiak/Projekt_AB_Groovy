// ścieżka: src/main/groovy/com/example/sat/server/GroundControlServerApp.groovy
package com.example.sat.server

import com.example.sat.db.RelationalDB
import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoData
import com.example.sat.dto.PhotoTask
import com.example.sat.dto.SubscriptionRequest
import com.example.sat.http.INotificationClient
import com.example.sat.http.ISatelliteApiClient
import com.example.sat.http.SimpleHttpClient
import com.example.sat.services.NotificationService
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.util.concurrent.Executors

/**
 * Główny serwer aplikacji, odpowiedzialny za zarządzanie misjami,
 * komunikację z satelitą i wysyłanie powiadomień.
 *
 * Zależności (baza danych, serwer HTTP) są wstrzykiwane przez konstruktor,
 * co pozwala na łatwe testowanie i elastyczność.
 */
class GroundControlServerApp {
    // Pola są typowane interfejsami - klucz do czystej architektury
    private final IHttpServer http
    private final RelationalDB database
    private ISatelliteApiClient satClient
    private NotificationService notificationService

    // Stan wewnętrzny serwera
    private Map<String, PhotoCollectionManifest> missionManifests = [:]
    private boolean forceUnhealthy = false

    // === GŁÓWNY KONSTRUKTOR (Dependency Injection) ===
    GroundControlServerApp(RelationalDB database, IHttpServer httpServer) {
        this.database = database
        this.http = httpServer
    }

    void start(int port, String satelliteServerUrl) {
        this.satClient = new SimpleHttpClient(satelliteServerUrl)
        this.http.port(port)

        // Konfiguracja serwisu powiadomień
        def notificationExecutor = Executors.newCachedThreadPool()
        def notificationClient = { String url, Object payload ->
            notificationExecutor.submit {
                try {
                    new SimpleHttpClient().send(url, payload)
                } catch (Exception e) {
                    println "NOTIFICATION_CLIENT_ERROR: Failed to send notification: ${e.message}"
                }
            }
        } as INotificationClient
        this.notificationService = new NotificationService(this.database, notificationClient)

        // --- Definicja wszystkich endpointów API ---
        defineEndpoints()

        http.awaitInitialization()
        println "SERVER (GRUND): Started on port ${http.port()}."
    }

    private void defineEndpoints() {
        http.get("/health") { req, res ->
            res.type("application/json")
            if (forceUnhealthy) {
                res.status(503)
                return JsonOutput.toJson([status: "DEGRADED", reason: "Forced unhealthy state"])
            }
            res.status(200)
            return JsonOutput.toJson([status: "UP"])
        }

        http.get("/") { req, res ->
            res.type("application/json")
            String baseUrl = req.scheme() + "://" + req.host()
            return JsonOutput.toJson([
                    _links: [
                            self: [href: baseUrl + "/"],
                            "create-mission": [href: baseUrl + "/missions/photo-collection", method: "POST"],
                            "create-subscription": [href: baseUrl + "/subscriptions", method: "POST"]
                    ]
            ])
        }

        http.post("/subscriptions") { req, res ->
            res.type("application/json")
            try {
                def subRequest = new SubscriptionRequest(new JsonSlurper().parseText(req.body()))
                database.saveSubscription(subRequest)
                res.status(201)
                return JsonOutput.toJson([status: "SUBSCRIPTION_CREATED", details: subRequest])
            } catch (Exception e) {
                res.status(400)
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }

        http.post("/missions/photo-collection") { req, res ->
            res.type("application/json")
            try {
                def missionData = new JsonSlurper().parseText(req.body())
                int photoCount = missionData.photoCount as int

                def tasks = (1..photoCount).collect { new PhotoTask() }
                def manifest = new PhotoCollectionManifest(tasks: tasks)
                missionManifests[manifest.missionId] = manifest
                println "GRUND: New mission ${manifest.missionId} created with status PLANNED."

                res.status(202) // Accepted
                return JsonOutput.toJson(manifest)
            } catch (Exception e) {
                res.status(500)
                return JsonOutput.toJson([error: "Unexpected error: ${e.message}"])
            }
        }

        http.get("/missions/:missionId") { req, res ->
            res.type("application/json")
            String missionId = req.params(":missionId")
            def manifest = missionManifests[missionId]

            if (!manifest) {
                res.status(404)
                return JsonOutput.toJson([error: "Mission with ID ${missionId} not found."])
            }

            String baseUrl = req.scheme() + "://" + req.host()
            def missionUrl = baseUrl + "/missions/${missionId}"
            def response = [
                    missionId: manifest.missionId,
                    status: manifest.status,
                    _links: [
                            self: [href: missionUrl],
                            tasks: [href: missionUrl + "/tasks"]
                    ]
            ]

            switch (manifest.status) {
                case "PLANNED":
                    response._links."accept-mission" = [href: missionUrl + "/accept", method: "POST"]
                    break
                case "ACCEPTED":
                    response._links."upload-to-satellite" = [href: missionUrl + "/upload", method: "POST"]
                    break
                case "COMPLETED":
                    response._links."create-subscription" = [href: baseUrl + "/subscriptions", method: "POST"]
                    break
            }

            res.status(200)
            return JsonOutput.toJson(response)
        }

        http.get("/missions/:missionId/tasks") { req, res ->
            res.type("application/json")
            String missionId = req.params(":missionId")
            def manifest = missionManifests[missionId]

            if (!manifest) { res.status(404); return JsonOutput.toJson([error: "Mission not found."])}

            int page = req.queryParams("page") ? req.queryParams("page") as int : 1
            int size = req.queryParams("size") ? req.queryParams("size") as int : 10
            int totalTasks = manifest.tasks.size()
            int totalPages = Math.ceil(totalTasks / (double) size) as int
            int offset = (page - 1) * size
            def tasksOnPage = manifest.tasks.drop(offset).take(size)

            String baseUrl = req.scheme() + "://" + req.host()
            String tasksUrl = baseUrl + "/missions/${missionId}/tasks"
            def links = [self: [href: tasksUrl + "?page=${page}&size=${size}"]]
            if (page > 1) links.prev = [href: tasksUrl + "?page=${page - 1}&size=${size}"]
            if (page < totalPages) links.next = [href: tasksUrl + "?page=${page + 1}&size=${size}"]
            links.first = [href: tasksUrl + "?page=1&size=${size}"]
            links.last = [href: tasksUrl + "?page=${totalPages}&size=${size}"]

            def responseBody = [
                    _links: links,
                    page: [
                            size: tasksOnPage.size(), totalElements: totalTasks,
                            totalPages: totalPages, number: page
                    ],
                    _embedded: [
                            tasks: tasksOnPage.collect { [taskId: it.taskId, status: it.status.name()] }
                    ]
            ]
            return JsonOutput.toJson(responseBody)
        }

        http.post("/missions/:missionId/accept") { req, res ->
            res.type("application/json")
            String missionId = req.params(":missionId")
            def manifest = missionManifests[missionId]

            if (!manifest || manifest.status != "PLANNED") {
                res.status(409)
                return JsonOutput.toJson([error: "Mission cannot be accepted at this time."])
            }
            manifest.status = "ACCEPTED"
            res.status(200)
            return JsonOutput.toJson([status: "OK", message: "Mission ${missionId} has been accepted."])
        }

        http.post("/missions/:missionId/upload") { req, res ->
            res.type("application/json")
            String missionId = req.params(":missionId")
            def manifest = missionManifests[missionId]

            if (!manifest || manifest.status != "ACCEPTED") {
                res.status(409)
                return JsonOutput.toJson([error: "Mission cannot be uploaded at this time."])
            }

            manifest.status = "UPLOADED"
            def uploadResponse = satClient.uploadMissionPlan(manifest)

            if (uploadResponse.statusCode == 202) {
                res.status(200)
                return JsonOutput.toJson([status: "OK", message: "Mission ${missionId} uploaded successfully."])
            } else {
                manifest.status = "FAILED"
                res.status(502)
                return JsonOutput.toJson([status: "ERROR", message: "Failed to upload mission to satellite."])
            }
        }

        http.post("/data/ingest") { req, res ->
            res.type("application/json")
            try {
                def photos = (new JsonSlurper().parseText(req.body()) as List<Map>).collect { new PhotoData(it) }
                if (photos.isEmpty()) {
                    res.status(400)
                    return JsonOutput.toJson([status: "ERROR", message: "Received empty photo list."])
                }

                photos.each { database.insertPhoto(it) }

                def missionId = photos.first().missionId
                if (missionId && isMissionComplete(missionId)) {
                    notificationService.publishEvent(
                            "MISSION_COMPLETED", missionId,
                            [photoCount: getPhotoCountForMission(missionId), message: "All data processed."]
                    )
                }
                res.status(202)
                return JsonOutput.toJson([status: "DATA_ACCEPTED", ingestedCount: photos.size()])
            } catch (Exception e) {
                res.status(500)
                return JsonOutput.toJson([status: "ERROR", message: e.message])
            }
        }
    }

    // === Metody Pomocnicze i Zarządzające ===

    void stop() { http?.stop() }
    int getPort() { return http.port() }
    void forceHealthStatus(boolean isUnhealthy) { this.forceUnhealthy = isUnhealthy }

    // Metoda do aktualizacji URL-a klienta SAT po starcie
    void updateSatClientUrl(String satelliteSimulatorUrl) {
        if (satelliteSimulatorUrl) {
            this.satClient = new SimpleHttpClient(satelliteSimulatorUrl)
            println "GRUND: Satellite client URL updated to ${satelliteSimulatorUrl}"
        }
    }

    void resetState() {
        missionManifests.clear()
        database.clearPhotos()
        database.clearSubscriptions()
        forceUnhealthy = false
        println "SERVER (GRUND): State has been reset."
    }

    int getDbPhotoCount() { return database.getPhotoCount() }

    private boolean isMissionComplete(String missionId) {
        def manifest = missionManifests[missionId]
        if (!manifest) return false

        def expectedCount = manifest.tasks.size()
        def actualCount = getPhotoCountForMission(missionId)

        println "MISSION_CHECK (${missionId}): Expected=${expectedCount}, Actual=${actualCount}"
        return actualCount >= expectedCount
    }

    private int getPhotoCountForMission(String missionId) {
        return database.getPhotoCountForMission(missionId)
    }
}