package com.example.sat.server

import com.example.sat.dto.PhotoData
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
        http = Service.ignite()
        http.port(port)

        def self = this
        def db = this.database

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

            println "GRUND: Received raw: ${req.body()}"
            def rawList = new JsonSlurper().parseText(req.body()) as List<Map>
            rawList.each { println "INCOMING MAP: $it" }

            def photos = rawList.collect {
                new PhotoData(
                        photoId: it.photoId,
                        timestamp: it.timestamp ?: System.currentTimeMillis(),
                        quality: it.quality,
                        content: it.content
                )
            }
            println "GRUND: Parsed PhotoData objects: $photos"
            photos.each { db.insertPhoto(it) }

            res.status(202)
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


        http.awaitInitialization()
        println "SERVER (GRUND): Started on port ${http.port()}."
    }

    void stop() { if (http != null) { http.stop(); http.awaitStop(); println "SERVER (GRUND): Server stopped." } }
    int getPort() { return http.port() }
    void updateSatClient(ISatelliteApiClient client) { this.satClient = client }
    void setMissionPlanner(IMissionPlanner planner) { this.missionPlanner = planner }
    void forceHealthStatus(boolean isUnhealthy) { this.forceUnhealthy = isUnhealthy }

    void resetState() {
        println "RESET before: ${database.photoTable.size()}"
        database.photoTable.clear()
        missionManifests.clear()
        println "RESET after: ${database.photoTable.size()}"
    }

    int getDbPhotoCount() { return database.photoTable.size() }
}