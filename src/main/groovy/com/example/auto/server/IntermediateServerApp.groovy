package com.example.auto.server

import com.example.auto.db.IntermediateDB
import com.example.auto.dto.PhotoData
import com.example.auto.http.SimpleHttpClient
import com.example.auto.storage.FileSystem
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class IntermediateServerApp {
    private final IHttpServer http
    private final FileSystem fileSystem
    private final IntermediateDB database
    private SimpleHttpClient platzClient
    private boolean unhealthy = false

    // === Wstrzykiwanie Zależności przez Konstruktor ===
    IntermediateServerApp(IHttpServer httpServer, FileSystem fileSystem, IntermediateDB database) {
        this.http = httpServer
        this.fileSystem = fileSystem
        this.database = database
    }

    void start(int port, String groundControlServerUrl = null) {
        println "SERVER (SERW1): Starting Intermediate Server on port ${port}..."
        if (groundControlServerUrl) {
            this.platzClient = new SimpleHttpClient(groundControlServerUrl)
        }
        http.port(port)
        defineEndpoints()
        http.awaitInitialization()

        // ZMIANA: Użyjmy portu, który już znamy, zamiast pytać serwer
        println "SERVER (SERW1): Intermediate Server started on port ${port}."
    }

    private void defineEndpoints() {
        http.get("/health") { req, res ->
            res.type("application/json")
            if (unhealthy) {
                res.status(503)
                return JsonOutput.toJson([status: "DOWN"])
            }
            res.status(200)
            return JsonOutput.toJson([status: "UP"])
        }

        http.post("/ingest") { req, res ->
            res.type("application/json")
            if (unhealthy) {
                res.status(503)
                return JsonOutput.toJson([status: "UNAVAILABLE"])
            }
            def fileName = "downlink_package_${System.currentTimeMillis()}.json"
            fileSystem.saveFile(fileName, req.body())
            res.status(202)
            return JsonOutput.toJson([status: "ACCEPTED", file: fileName])
        }

        http.post("/process") { req, res ->
            res.type("application/json")
            archiveFilesToDatabase()
            res.status(200)
            return JsonOutput.toJson([status: "OK", archivedCount: database.getPhotoCount()])
        }

        http.post("/forward") { req, res ->
            res.type("application/json")
            boolean success = forwardDataToGroundControl()
            if (success) {
                res.status(200)
                return JsonOutput.toJson([status: "OK", forwardedCount: database.getPhotoCount()])
            } else {
                res.status(502)
                return JsonOutput.toJson([status: "ERROR", message: "Forwarding failed."])
            }
        }

        http.get("/data") { req, res ->
            res.type("application/json")
            return JsonOutput.toJson(database.getArchivedPhotos())
        }
    }

    void stop() { http?.stop() }
    int getPort() { return http.getPort() }
    void forceHealthStatus(boolean isUnhealthy) { this.unhealthy = isUnhealthy }

    void updatePlatzControlUrl(String groundControlServerUrl) {
        if (groundControlServerUrl) {
            this.platzClient = new SimpleHttpClient(groundControlServerUrl)
        }
    }

    void resetState() {
        fileSystem.clear()
        database.clearPhotos()
        println "SERVER (SERW1): State has been reset."
    }

    // Metody do weryfikacji w testach
    int getDbPhotoCount() { return database.getPhotoCount() }
    List<Map> getDbPhotos() { return database.getArchivedPhotos() }
    int getFileSystemFileCount() { return fileSystem.getFileCount() }

    // === Logika Biznesowa ===
    private void archiveFilesToDatabase() {
        def files = fileSystem.readAllFiles()
        files.each { fileName, jsonContent ->
            def photos = new JsonSlurper().parseText(jsonContent) as List<Map>
            photos.each { photoMap ->
                database.insertPhoto(new PhotoData(photoMap))
            }
            fileSystem.deleteFile(fileName)
        }
    }

    private boolean forwardDataToGroundControl() {
        if (!platzClient) return false // Sprawdzenie 1

        // Sprawdzenie 2: Health Check
        def healthResponse = platzClient.getData("/health")
        if (healthResponse.statusCode != 200) return false

        // Sprawdzenie 3: Wysłanie danych
        def photosToForward = database.getArchivedPhotos()
        if (photosToForward) {
            def response = platzClient.sendGenericPost("/data/ingest", photosToForward)
            if (response.statusCode == 202) {
                database.clearPhotos()
                return true
            }
            return false // <-- PRAWDOPODOBNIE TUTAJ JESTEŚMY
        }
        return true
    }
}