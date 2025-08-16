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
    // NOWE POLE: INSTANCJA SERWISU POWIADOMIEŃ
    private NotificationService notificationService

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

        http.get("/health") { req, res ->
            res.type("application/json")
            if (self.forceUnhealthy) {
                res.status(503)
                return JsonOutput.toJson([status: "DEGRADED", reason: "Database connection pool exhausted"])
            }
            res.status(200)
            return JsonOutput.toJson([status: "UP"])
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
                def params = new JsonSlurper().parseText(req.body())
                int photoCount = params.photoCount

                def tasks = (1..photoCount).collect { new PhotoTask() }
                def manifest = new PhotoCollectionManifest(tasks: tasks)
                self.missionManifests[manifest.missionId] = manifest

                def response = self.satClient.uploadMissionPlan(manifest)

                if (response.statusCode == 202) {
                    manifest.status = "UPLOADED"
                    res.status(202)
                    return JsonOutput.toJson(manifest)
                } else {
                    manifest.status = "UPLOAD_FAILED"
                    res.status(502)
                    return JsonOutput.toJson([error: "Satellite rejected the manifest."])
                }
            } catch (Exception e) {
                res.status(500)
                println "ERROR in /missions/photo-collection: ${e.message}"
                e.printStackTrace()
                return JsonOutput.toJson([error: "Unexpected error: ${e.message}"])
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

// Wklej te metody do klasy GroundControlServerApp

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