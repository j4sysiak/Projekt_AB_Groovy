package com.example.sat.server

import com.example.sat.dto.PhotoData
import com.example.sat.fakes.FakeFileSystem
import com.example.sat.fakes.FakeRelationalDB
import com.example.sat.http.SimpleHttpClient
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spark.Service

//Serwer SERW1  (pośrednik pomiędzy SAT i GRUND) obsługuje Potok2
class IntermediateServerApp {
    private boolean unhealthy = false

    final FakeFileSystem fileSystem = new FakeFileSystem()
    final FakeRelationalDB database = new FakeRelationalDB()
    private Service http // Własna, prywatna instancja serwera Spark
    // POLE: Flaga do symulowania awarii w testach
    private boolean forceUnhealthy = false
    // POLE: Klient do komunikacji z serwerem GRUND
    private SimpleHttpClient grundClient

    void start(int port, String groundControlServerUrl = null) {
        println "SERVER (SERW1): Starting Intermediate Server on port ${port}..."
        if (groundControlServerUrl) {
            this.grundClient = new SimpleHttpClient(groundControlServerUrl)
        }

        http = Service.ignite() // Tworzymy nową, niezależną instancję serwera
        http.port(port)

        // Definiujemy endpointy na naszej lokalnej instancji 'http'

        http.get("/health") { req, res ->
            res.type("application/json")
            println "SERVER (SERW1): Jesteś w Endpoint /health"
            if (forceUnhealthy) {
                res.status(503) // Service Unavailable
                return JsonOutput.toJson([status: "DOWN", components: [database: "TIMEOUT"]])
            }
            res.status(200)
            return JsonOutput.toJson([status: "UP", components: [fileSystem: "OK", database: "OK"]])
        }

        http.post("/ingest") { req, res ->
            res.type("application/json")
            println "SERVER (SERW1): Jesteś w Endpoint /ingest"
            if (this.unhealthy) {
                println "SERVER (SERW1): Rejected data on /ingest due to UNHEALTHY state."
                res.status(503)
                return JsonOutput.toJson([status: "UNAVAILABLE", message: "Intermediate server is unhealthy"])
            }
            println "SERVER (SERW1): Receiving data package on /ingest endpoint."
            def jsonPayload = req.body()
            def fileName = "downlink_package_${System.currentTimeMillis()}.json"
            fileSystem.saveFile(fileName, jsonPayload)
            res.status(202) // Accepted
            return JsonOutput.toJson([status: "ACCEPTED", message: "Data package received and stored as ${fileName}"])
        }

        http.post("/process") { req, res ->
            res.type("application/json")
            println "SERVER (SERW1): Jesteś w Endpoint /process"
            archiveFilesToDatabase()
            res.status(200)
            return JsonOutput.toJson([status: "OK", archivedCount: database.photoTable.size()])
        }

        http.post("/forward") { req, res ->
            res.type("application/json")
            println "SERVER (SERW1): Jesteś w Endpoint /forward"
            boolean success = forwardDataToGroundControl()
            res.type("application/json")
            if (success) {
                res.status(200)
                return JsonOutput.toJson([status: "OK", forwardedCount: database.photoTable.size()])
            } else {
                res.status(502) // Bad Gateway
                return JsonOutput.toJson([status: "ERROR", message: "Forwarding failed, Ground Control is unhealthy."])
            }
        }

        http.get("/data") { req, res ->
            res.type("application/json")
            println "SERVER (SERW1): Jesteś w Endpoint /data"
            return JsonOutput.toJson(new ArrayList(database.getArchivedPhotos()))
        }

        http.awaitInitialization()
        println "SERVER (SERW1): Intermediate Server started on port ${http.port()}."
    }

    void updateGrundControlUrl(String groundControlServerUrl) {
        if (groundControlServerUrl) {
            this.grundClient = new SimpleHttpClient(groundControlServerUrl)
        }
    }

    void stop() {
        if (http != null) {
            println "SERVER (SERW1): Stopping Intermediate Server..."
            http.stop()
            http.awaitStop()
            println "SERVER (SERW1): Server stopped."
        }
    }

    // METODA: Do sterowania stanem "zdrowia" w testach
    void forceHealthStatus(boolean status) {
        this.unhealthy = status
    }

    // Publiczna metoda do pobierania portu
    int getPort() { return http.port() }

    // Prywatna metoda logiki biznesowej
    private void archiveFilesToDatabase() {
        println "SERVER (SERW1): Starting archiving process."
        def files = fileSystem.readAllFiles()
        files.each { entry ->
            def fileName = entry.key
            def jsonContent = entry.value

            // Reszta Twojej logiki pozostaje bez zmian
            def photos = new JsonSlurper().parseText(jsonContent)
            photos.each { photoMap ->
                // 1. Stwórz prawdziwy obiekt PhotoData z mapy
                def photoObject = new PhotoData(photoMap)

                // 2. Wstaw do bazy poprawny obiekt
                database.insertPhoto(photoObject)
            }
            fileSystem.deleteFile(fileName)
        }
    }

    void resetState() {
        this.fileSystem.files.clear()
        this.database.photoTable.clear()
        println "SERVER (SERW1): State has been reset."
    }

    // Metody do weryfikacji stanu wewnętrznego w testach
    int getDbPhotoCount() { return database.photoTable.size() }
    List<Map> getDbPhotos() { return database.getArchivedPhotos() }
    int getFileSystemFileCount() { return fileSystem.files.size() }

    boolean forwardDataToGroundControl() {
        println "SERW1: Attempting to forward archived data to Ground Control."
        println "SERW1: Performing health check on Ground Control server..."
        def healthResponse = grundClient.getData("/health")
        if (healthResponse.statusCode != 200 || healthResponse.json.status != "UP") {
            println "SERW1: Health check FAILED for Ground Control. Aborting forward."
            return false // Przerywamy operację
        }
        println "SERW1: Health check PASSED."
        def photos = database.getArchivedPhotos()
        if (photos) {
            def response = grundClient.sendGenericPost("/data/ingest", photos)
            if (response.statusCode == 202) {
                // SUKCES! Wyczyść lokalną bazę, bo dane zostały przekazane.
                println "SERVER (SERW1): Forwarding successful. Clearing local database."
                database.photoTable.clear() // Zakładając, że tak czyścisz swoją FakeRelationalDB
                return true
            } else {
                // BŁĄD! Nie czyść bazy, spróbujemy wysłać ponownie później.
                println "SERVER (SERW1): Forwarding failed. Data remains in local database."
                return false
            }
        }
        return true // Nie było nic do wysłania.
    }
}