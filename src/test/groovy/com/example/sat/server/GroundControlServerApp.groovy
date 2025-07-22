package com.example.sat.server

import com.example.sat.IMissionPlanner
import com.example.sat.dto.PhotoCollectionManifest
import com.example.sat.dto.PhotoTask
import com.example.sat.fakes.FakeRelationalDB
import com.example.sat.http.ISatelliteApiClient
import com.example.sat.http.SimpleHttpClient
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spark.Service

class GroundControlServerApp {
    private Service http
    private ISatelliteApiClient satClient
    private IMissionPlanner missionPlanner
    private Map<String, PhotoCollectionManifest> missionManifests = [:]
    private final FakeRelationalDB database = new FakeRelationalDB()
    private boolean forceUnhealthy = false

    void start(int port, String satelliteServerUrl) {
        this.satClient = new SimpleHttpClient(satelliteServerUrl)
        http = Service.ignite(); http.port(port)

        // === KLUCZOWA ZMIANA: Przechowujemy referencję do 'this' ===
        // Wewnątrz domknięć (closures) Sparka, 'this' może odnosić się do czegoś innego.
        // Użycie 'self' gwarantuje, że zawsze odnosimy się do instancji tej klasy.
        def self = this
        // =========================================================

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
            println "GRUND: Received final data package on /data/ingest endpoint."

            def photos = new JsonSlurper().parseText(req.body()) as List<Map>
            photos.each { photo ->
                self.database.insertPhoto(photo)
            }

            res.status(202)
            return JsonOutput.toJson([status: "ACCEPTED", receivedPhotos: photos.size()])
        }

        http.get("/data/photos") { req, res ->
            res.type("application/json")
            return JsonOutput.toJson(self.database.getArchivedPhotos())
        }

        http.post("/missions/photo-collection") { req, res ->
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
        missionManifests.clear()
        database.photoTable.clear()
        println "SERVER (GRUND): State has been reset."
    }

    int getDbPhotoCount() { return database.photoTable.size() }
}